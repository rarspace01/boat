#!/usr/bin/env python3
"""Lists the videos of a library worth converting to AV1: old codecs (MPEG-2, Xvid/DivX/MPEG-4 part 2) which are big.

Only reads the files (ffprobe), nothing is changed. The savings are a rough guess by codec, the real size depends on the
encoder settings and the picture.

  scripts/find-av1-candidates.py ~/pfdb/PFDB/Movies
  scripts/find-av1-candidates.py ~/pfdb/PFDB --min-size 500 --csv candidates.csv --list candidates.txt
"""
import argparse
import csv
import json
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

VIDEO_EXTENSIONS = {".mkv", ".mp4", ".avi", ".mpg", ".mpeg", ".m2ts", ".ts", ".vob", ".divx", ".m4v", ".wmv"}
# what AV1 needs of the old size at a similar quality, a rough rule of thumb
AV1_SIZE_FACTOR = {"mpeg2video": 0.30, "mpeg4": 0.50, "msmpeg4v3": 0.50, "msmpeg4v2": 0.50}
GB = 1024 ** 3
MB = 1024 ** 2


def probe(path: Path, timeout: int):
    try:
        result = subprocess.run(
            ["ffprobe", "-v", "error", "-protocol_whitelist", "file", "-select_streams", "v:0",
             "-show_entries", "stream=codec_name,codec_tag_string,width,height:format=duration,size",
             "-of", "json", "-i", str(path)],
            capture_output=True, text=True, timeout=timeout)
        if result.returncode != 0:
            return path, None, result.stderr.strip().splitlines()[-1:] or ["ffprobe failed"]
        data = json.loads(result.stdout)
        stream = (data.get("streams") or [{}])[0]
        fmt = data.get("format", {})
        return path, {
            "codec": stream.get("codec_name"),
            "tag": (stream.get("codec_tag_string") or "").strip(),
            "width": stream.get("width"),
            "height": stream.get("height"),
            "duration": float(fmt["duration"]) if fmt.get("duration") else None,
            "size": int(fmt["size"]) if fmt.get("size") else path.stat().st_size,
        }, None
    except (subprocess.TimeoutExpired, OSError, ValueError) as e:
        return path, None, [str(e)]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("folder", type=Path, nargs="+", help="library folder(s) to scan")
    parser.add_argument("--min-size", type=int, default=300, metavar="MB", help="ignore smaller files, little to win (default 300)")
    parser.add_argument("--min-saving", type=int, default=150, metavar="MB", help="ignore files with less estimated saving (default 150)")
    parser.add_argument("--threads", type=int, default=4, help="parallel ffprobe calls (default 4, a network mount may like fewer)")
    parser.add_argument("--timeout", type=int, default=60, help="seconds for one file (default 60)")
    parser.add_argument("--csv", type=Path, help="also write the candidates as CSV")
    parser.add_argument("--list", type=Path, help="also write the plain list of paths, e.g. for a conversion script")
    args = parser.parse_args()

    files = [p for folder in args.folder for p in folder.rglob("*")
             if p.suffix.lower() in VIDEO_EXTENSIONS and p.is_file() and p.stat().st_size >= args.min_size * MB]
    print(f"probing {len(files)} files of at least {args.min_size} MB ...", file=sys.stderr)

    candidates, failed = [], []
    with ThreadPoolExecutor(args.threads) as pool:
        for done, (path, info, error) in enumerate(pool.map(lambda p: probe(p, args.timeout), files), 1):
            if done % 100 == 0:
                print(f"  {done}/{len(files)}", file=sys.stderr)
            if info is None:
                failed.append((path, error))
                continue
            factor = AV1_SIZE_FACTOR.get(info["codec"])
            if factor is None:
                continue
            saving = info["size"] * (1 - factor)
            if saving < args.min_saving * MB:
                continue
            kind = "xvid/divx" if info["codec"] == "mpeg4" else info["codec"]
            minutes = info["duration"] / 60 if info["duration"] else None
            candidates.append({
                "path": str(path), "codec": kind, "tag": info["tag"],
                "resolution": f'{info["width"]}x{info["height"]}',
                "minutes": round(minutes) if minutes else "",
                "size_gb": round(info["size"] / GB, 2), "saving_gb": round(saving / GB, 2),
            })

    candidates.sort(key=lambda c: c["saving_gb"], reverse=True)
    for c in candidates:
        print(f'{c["saving_gb"]:6.2f} GB  of {c["size_gb"]:6.2f} GB  {c["codec"]:10} {c["resolution"]:>9} {str(c["minutes"]):>4} min  {c["path"]}')
    total = sum(c["saving_gb"] for c in candidates)
    print(f"\n{len(candidates)} candidates, about {total:.1f} GB to win; {len(failed)} files could not be read", file=sys.stderr)
    for path, error in failed:
        print(f"  unreadable: {path}: {' '.join(error)}", file=sys.stderr)

    if args.csv and candidates:
        with args.csv.open("w", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=list(candidates[0]))
            writer.writeheader()
            writer.writerows(candidates)
    if args.list:
        args.list.write_text("".join(c["path"] + "\n" for c in candidates))


if __name__ == "__main__":
    main()
