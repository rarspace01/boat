#!/usr/bin/env python3
"""Converts videos to AV1 (SVT-AV1) with Opus audio, e.g. the files listed by find-av1-candidates.py.

The result is encoded in a local folder (--work-dir, default ~/tmp) and copied to its place afterwards, so the library can be on a mount
(rclone, network share) which doesn't allow seeking in files it writes.

By default the original stays and the result is written next to it as "<name>.av1.mkv". With --replace the original is
deleted after the result was checked (about the same length, smaller) and the result is renamed to "<name>.mkv".

The audio is not scaled down, but the Opus bitrate depends on the channels and never goes above the bitrate of the audio
stream it comes from (Opus needs less than AC3/DTS/MP3 for the same quality, so these defaults are generous).

Try the settings on a short piece first, it never touches the original:
  scripts/convert-to-av1.py --sample 120 movie.avi
  scripts/convert-to-av1.py --crf 28 --preset 4 --from-list candidates.txt --replace
"""
import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

# Opus bitrate (bit/s) by number of channels, a bit more than needed for transparent quality
OPUS_BITRATE = {1: 48_000, 2: 96_000, 3: 128_000, 4: 144_000, 5: 160_000, 6: 160_000, 7: 208_000, 8: 224_000}
# libopus refuses layouts like 5.1(side), the plain layouts work
OPUS_LAYOUTS = {6: "5.1", 8: "7.1"}
INTERLACED = {"tt", "bb", "tb", "bt"}
# SVT-AV1 preset by picture height, the bigger the picture the faster the preset to keep the time per movie reasonable
PRESET_BY_HEIGHT = [(576, 5), (1080, 6)]
PRESET_ABOVE = 7


def preset_for(video) -> int:
    height = (video or {}).get("height") or 0
    return next((preset for limit, preset in PRESET_BY_HEIGHT if height <= limit), PRESET_ABOVE)


def probe(path: Path) -> dict:
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-protocol_whitelist", "file", "-show_entries",
         "stream=index,codec_type,codec_name,channels,bit_rate,field_order,height:stream_tags=BPS:format=duration",
         "-of", "json", "-i", str(path)], capture_output=True, text=True, check=True)
    return json.loads(out.stdout)


def source_bitrate(stream: dict):
    value = stream.get("bit_rate") or stream.get("tags", {}).get("BPS")
    return int(value) if value and str(value).isdigit() else None


def audio_bitrate(stream: dict) -> int:
    channels = stream.get("channels") or 2
    target = OPUS_BITRATE.get(channels, 32_000 * channels)
    existing = source_bitrate(stream)
    return min(target, existing) if existing else target


def build_command(path: Path, output: Path, info: dict, args) -> list:
    streams = info["streams"]
    video = next((s for s in streams if s["codec_type"] == "video"), None)
    audios = [s for s in streams if s["codec_type"] == "audio"]
    command = ["ffmpeg", "-hide_banner", "-nostdin", "-y"]
    if args.sample:
        command += ["-ss", str(args.sample_start)]
    command += ["-i", str(path)]
    if args.sample:
        command += ["-t", str(args.sample)]
    # video, all audio and subtitles, chapters and tags; data streams (e.g. teletext) are dropped
    command += ["-map", "0:v:0", "-map", "0:a?", "-map", "0:s?", "-map_metadata", "0", "-map_chapters", "0"]
    # DVD material is often interlaced, it looks bad on a progressive screen without this
    if video and video.get("field_order") in INTERLACED:
        command += ["-vf", "bwdif=mode=send_frame"]
    command += ["-c:v", "libsvtav1", "-crf", str(args.crf), "-preset", str(args.preset if args.preset is not None else preset_for(video)), "-pix_fmt", "yuv420p10le"]
    command += ["-c:a", "libopus", "-c:s", "copy"]
    for n, stream in enumerate(audios):
        command += [f"-b:a:{n}", str(audio_bitrate(stream))]
        layout = OPUS_LAYOUTS.get(stream.get("channels"))
        if layout:
            command += [f"-filter:a:{n}", f"channelmap=channel_layout={layout}"]
    return command + [str(output)]


def convert(path: Path, args) -> bool:
    try:
        info = probe(path)
    except subprocess.CalledProcessError as e:
        print(f"FAILED: ffprobe can't read {path}: {e.stderr.strip().splitlines()[-1:] or ''}", file=sys.stderr)
        return False
    output = path.with_name(path.stem + (".sample.mkv" if args.sample else ".av1.mkv"))
    if output.exists() and not args.sample:
        print(f"skip, exists: {output}")
        return True
    # encoded on a local disk: the Matroska muxer seeks in the file at the end, a mount (rclone, network share) can't do that
    descriptor, name = tempfile.mkstemp(prefix="av1-", suffix=".mkv", dir=args.work_dir)
    os.close(descriptor)
    work = Path(name)
    try:
        command = build_command(path, work, info, args)
        print(" ".join(f"'{c}'" if " " in c else c for c in command))
        if args.dry_run:
            return True
        if subprocess.run(command).returncode != 0:
            print(f"FAILED: {path}", file=sys.stderr)
            return False
        if args.sample:
            copy_to(work, output)
            print(f"sample: {output} ({output.stat().st_size / 1024 ** 2:.0f} MB for {args.sample}s)")
            return True
        # the result has to be as long as the original and smaller, else something went wrong
        old_duration = float(info["format"].get("duration") or 0)
        new_duration = float(probe(work)["format"].get("duration") or 0)
        if old_duration and abs(new_duration - old_duration) > max(2.0, old_duration * 0.01):
            print(f"FAILED: length {new_duration:.0f}s instead of {old_duration:.0f}s: {path}", file=sys.stderr)
            return False
        if work.stat().st_size >= path.stat().st_size:
            print(f"not smaller, kept the original: {path}")
            return True
        saved = (path.stat().st_size - work.stat().st_size) / 1024 ** 2
        copy_to(work, output)
        if args.replace:
            # the result is at its place and complete, only now the original goes
            target = path.with_suffix(".mkv")
            path.unlink()
            output.rename(target)
            print(f"replaced, saved {saved:.0f} MB: {target}")
        else:
            print(f"saved {saved:.0f} MB: {output}")
        return True
    except OSError as e:
        print(f"FAILED: {path}: {e}", file=sys.stderr)
        return False
    finally:
        work.unlink(missing_ok=True)


def copy_to(source: Path, target: Path):
    """Copies the file (not its times and permissions, a mount may refuse those) and checks it arrived completely."""
    try:
        shutil.copyfile(source, target)
        if target.stat().st_size != source.stat().st_size:
            raise OSError(f"copy to {target} is incomplete")
    except OSError:
        target.unlink(missing_ok=True)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("files", type=Path, nargs="*")
    parser.add_argument("--from-list", type=Path, help="file with one path per line (--list of find-av1-candidates.py)")
    parser.add_argument("--crf", type=int, default=30, help="quality, lower is better and bigger (default 30)")
    parser.add_argument("--preset", type=int, help="0 slowest/best .. 13 fastest (default by height: 5 up to 576p, 6 up to 1080p, 7 above)")
    parser.add_argument("--replace", action="store_true", help="delete the original after the checks, name the result <name>.mkv")
    parser.add_argument("--sample", type=int, metavar="SECONDS", help="only convert that much from the middle, to try the settings")
    parser.add_argument("--sample-start", type=int, default=600, help="where the sample starts, seconds (default 600)")
    parser.add_argument("--work-dir", type=Path, default=Path.home() / "tmp", help="local folder for the encoding, created if missing, needs room for one result (default: ~/tmp)")
    parser.add_argument("--dry-run", action="store_true", help="only print the ffmpeg commands")
    args = parser.parse_args()

    args.work_dir.mkdir(parents=True, exist_ok=True)
    files = list(args.files)
    if args.from_list:
        files += [Path(line) for line in args.from_list.read_text().splitlines() if line.strip()]
    if not files:
        parser.error("no files given")
    failed = [f for f in files if not convert(f, args)]
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
