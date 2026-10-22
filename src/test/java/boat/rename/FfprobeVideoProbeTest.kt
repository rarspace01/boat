package boat.rename

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

internal class FfprobeVideoProbeTest {

    @Test
    fun `should read the answer of ffprobe`() {
        val json = """{"streams":[{"codec_name":"hevc","width":1920,"height":1080,"bit_rate":"2500000"}],
            "format":{"duration":"1450.123000","size":"453000000","bit_rate":"2499000"}}"""

        assertThat(FfprobeVideoProbe.parse(json)).isEqualTo(VideoInfo("hevc", 1920, 1080, 2_500_000, 1450.123))
    }

    @Test
    fun `should take the bitrate of the file if the stream has none like in mkv`() {
        val json = """{"streams":[{"codec_name":"h264","width":720,"height":516}],"format":{"duration":"114.463000","size":"5077251","bit_rate":"354857"}}"""

        assertThat(FfprobeVideoProbe.parse(json)?.bitrate).isEqualTo(354_857)
    }

    @Test
    fun `should work out the bitrate from size and length if nothing tells it`() {
        val json = """{"streams":[{"codec_name":"h264","width":720,"height":516}],"format":{"duration":"100.0","size":"12500000"}}"""

        assertThat(FfprobeVideoProbe.parse(json)?.bitrate).isEqualTo(1_000_000)
    }

    @Test
    fun `should not make up what ffprobe does not tell`() {
        assertThat(FfprobeVideoProbe.parse("""{"streams":[],"format":{"duration":"10"}}""")).isNull()
        assertThat(FfprobeVideoProbe.parse("""{"format":{"duration":"10"}}""")).isNull()
        assertThat(FfprobeVideoProbe.parse("not json")).isNull()
        assertThat(FfprobeVideoProbe.parse("")).isNull()
        val noDuration = FfprobeVideoProbe.parse("""{"streams":[{"codec_name":"h264","width":1,"height":1}],"format":{"duration":"N/A"}}""")
        assertThat(noDuration?.durationSeconds).isNull()
        assertThat(noDuration?.bitrate).isNull()
    }

    @Test
    fun `should return null if the program or the file is not there`(@TempDir tempDir: Path) {
        assertThat(FfprobeVideoProbe("ffprobe-which-does-not-exist").probe(File(tempDir.toFile(), "a.mkv"))).isNull()
        assumeTrue(isInstalled("ffprobe"), "ffprobe is not installed")
        assertThat(FfprobeVideoProbe().probe(File(tempDir.toFile(), "missing.mkv"))).isNull()
        val notAVideo = File(tempDir.toFile(), "text.mkv").apply { writeText("this is not a video") }
        assertThat(FfprobeVideoProbe().probe(notAVideo)).isNull()
    }

    @Test
    fun `should give up on a ffprobe which hangs after the timeout`(@TempDir tempDir: Path) {
        // the same as a ffprobe on a file which cannot be read: it never answers
        val hanging = File(tempDir.toFile(), "hanging-ffprobe.sh").apply {
            writeText("#!/bin/sh\nsleep 30\n")
            setExecutable(true)
        }
        val start = System.nanoTime()

        val info = FfprobeVideoProbe(hanging.path, timeoutSeconds = 1).probe(File(tempDir.toFile(), "a.mkv"))

        assertThat(info).isNull()
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(5_000)
    }

    @Test
    fun `should read a real video`(@TempDir tempDir: Path) {
        assumeTrue(isInstalled("ffprobe") && isInstalled("ffmpeg"), "ffmpeg is not installed")
        val video = File(tempDir.toFile(), "test.mp4")
        val created = ProcessBuilder(
            "ffmpeg", "-v", "error", "-f", "lavfi", "-i", "testsrc=size=640x360:rate=10:duration=3", "-c:v", "libx264", "-pix_fmt", "yuv420p", video.path
        ).redirectErrorStream(true).start().waitFor()
        assumeTrue(created == 0 && video.exists(), "ffmpeg could not create the test video (no libx264?)")

        val info = FfprobeVideoProbe().probe(video)

        assertThat(info?.codec).isEqualTo("h264")
        assertThat(info?.width).isEqualTo(640)
        assertThat(info?.height).isEqualTo(360)
        assertThat(info?.durationSeconds).isBetween(2.9, 3.2)
        assertThat(info?.bitrate).isGreaterThan(0)
    }

    private fun isInstalled(command: String): Boolean =
        runCatching { ProcessBuilder(command, "-version").redirectErrorStream(true).start().also { it.inputStream.readAllBytes() }.waitFor() == 0 }.getOrDefault(false)
}
