package boat.info

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

internal class CloudFileServiceProcessTest {

    private fun fakeRclone(dir: File, script: String): String =
        File(dir, "rclone").apply {
            writeText("#!/bin/sh\n$script\n")
            setExecutable(true)
        }.path

    @Test
    fun `should read a large listing without waiting`(@TempDir tempDir: Path) {
        // about 1 MB, more than the pipe holds: rclone has to be read while it runs
        val rclone = fakeRclone(
            tempDir.toFile(),
            """awk 'BEGIN { printf "["; for (i = 0; i < 20000; i++) printf "%s{\"Path\":\"Movie %05d.mkv\"}", (i ? "," : ""), i; print "]" }'"""
        )
        val start = System.nanoTime()

        val files = CloudFileService(rclone).getFilesInPathWithRetries("remote:Movies/M/", 0)

        assertThat(files).hasSize(20_000)
        assertThat(files.first()).isEqualTo("remote:Movies/M/Movie 00000.mkv")
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(4_000)
    }

    @Test
    fun `should not hang on lots of error output`(@TempDir tempDir: Path) {
        val rclone = fakeRclone(
            tempDir.toFile(),
            """awk 'BEGIN { for (i = 0; i < 5000; i++) print "NOTICE: some message which is repeated a lot of times" > "/dev/stderr" }'; echo '[{"Path":"a.mkv"}]'"""
        )

        assertThat(CloudFileService(rclone).getFilesInPathWithRetries("remote:Movies/A/", 0)).containsExactly("remote:Movies/A/a.mkv")
    }

    @Test
    fun `should stop rclone after the timeout`(@TempDir tempDir: Path) {
        val rclone = fakeRclone(tempDir.toFile(), "sleep 30")
        val start = System.nanoTime()

        val files = CloudFileService(rclone, listingTimeoutSeconds = 1).getFilesInPathWithRetries("remote:Movies/A/", 0)

        assertThat(files).isEmpty()
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(5_000)
    }

    @Test
    fun `should treat a missing folder as empty`(@TempDir tempDir: Path) {
        val rclone = fakeRclone(tempDir.toFile(), """echo "ERROR : error listing: directory not found" >&2; exit 3""")

        assertThat(CloudFileService(rclone).getFilesInPathWithRetries("remote:Movies/Q/", 0)).isEmpty()
    }

    @Test
    fun `should return nothing if rclone is not installed`() {
        assertThat(CloudFileService("rclone-which-does-not-exist").getFilesInPathWithRetries("remote:Movies/A/", 0)).isEmpty()
    }
}
