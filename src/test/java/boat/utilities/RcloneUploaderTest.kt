package boat.utilities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RcloneUploaderTest {

    @Test
    fun shouldDetectRcloneErrorLines() {
        assertThat(RcloneUploader.isErrorLine("2026/10/21 07:08:55 ERROR : Attempt 1/3 failed with 1 errors and: CopyURL failed: 404 File not found")).isTrue()
        assertThat(RcloneUploader.isErrorLine("2026/10/21 07:08:55 CRITICAL: Failed to create file system")).isTrue()
    }

    @Test
    fun shouldNotDetectOtherLinesAsErrors() {
        assertThat(RcloneUploader.isErrorLine("2026/10/21 07:08:55 NOTICE: Config file not found - using defaults")).isFalse()
        assertThat(RcloneUploader.isErrorLine("Transferred: 1.907 MiB / 1.907 MiB, 100%, 0 B/s, ETA -")).isFalse()
        assertThat(RcloneUploader.isErrorLine("2026/10/21 07:08:55 INFO  : MY_ERROR_MOVIE.mkv: Copied (new)")).isFalse()
        assertThat(RcloneUploader.isErrorLine("2026/10/21 07:08:55 INFO  : Hard ERROR: Part 2.mkv: Copied (new)")).isFalse()
    }

    @Test
    fun shouldVerifyCertificatesByDefault() {
        assertThat(RcloneUploader.certificateArguments()).isEmpty()
    }
}
