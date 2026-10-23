package boat.utilities

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/** Uses the real rclone with local paths, the tests are skipped if rclone is not installed. */
internal class RcloneUploaderCopyTest {

    @TempDir
    lateinit var dir: File

    private val uploader = RcloneUploader()
    private lateinit var server: HttpServer
    private val downloads = AtomicInteger()
    private val content = "x".repeat(1000)

    @BeforeEach
    fun setUp() {
        assumeTrue(ProcessUtil.isRcloneInstalled, "rclone is not installed")
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/file.mkv") { exchange ->
            downloads.incrementAndGet()
            val body = content.toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @AfterEach
    fun tearDown() {
        if (::server.isInitialized) server.stop(0)
    }

    private fun url() = "http://127.0.0.1:${server.address.port}/file.mkv"

    @Test
    fun shouldTellTheSizeOfAnExistingFileOnly() {
        File(dir, "a.mkv").writeText("hello")
        File(dir, "folder").mkdirs()

        assertThat(uploader.existingFileSize(File(dir, "a.mkv").path)).isEqualTo(5)
        assertThat(uploader.existingFileSize(File(dir, "missing.mkv").path)).isNull()
        assertThat(uploader.existingFileSize(File(dir, "folder").path)).isNull()
    }

    @Test
    fun shouldKeepAnApostropheInTheTargetPath() {
        val target = File(dir, "Ocean's Eleven/Ocean's Eleven.mkv")

        assertThat(uploader.copyUrl(url(), target.path, content.length.toLong())).isTrue
        assertThat(target.readText()).isEqualTo(content)
        assertThat(uploader.existingFileSize(target.path)).isEqualTo(content.length.toLong())
    }

    @Test
    fun shouldCopyAFileWhichIsNotThereYet() {
        val target = File(dir, "new.mkv")

        assertThat(uploader.copyUrl(url(), target.path, content.length.toLong())).isTrue
        assertThat(target.readText()).isEqualTo(content)
        assertThat(downloads.get()).isEqualTo(1)
    }

    @Test
    fun shouldNotDownloadAFileAgainWhichIsThereWithTheExpectedSize() {
        val target = File(dir, "complete.mkv").apply { writeText(content) }
        val modified = target.lastModified()

        assertThat(uploader.copyUrl(url(), target.path, content.length.toLong())).isTrue
        assertThat(downloads.get()).isEqualTo(0)
        assertThat(target.lastModified()).isEqualTo(modified)
    }

    @Test
    fun shouldReplaceAFileWithAnotherSize() {
        val target = File(dir, "partial.mkv").apply { writeText("x".repeat(300)) }

        assertThat(uploader.copyUrl(url(), target.path, content.length.toLong())).isTrue
        assertThat(target.readText()).isEqualTo(content)
        assertThat(downloads.get()).isEqualTo(1)
    }

    @Test
    fun shouldReplaceAnExistingFileIfTheSizeIsNotKnown() {
        val target = File(dir, "unknown.mkv").apply { writeText("x".repeat(content.length)) }

        assertThat(uploader.copyUrl(url(), target.path)).isTrue
        assertThat(uploader.copyUrl(url(), target.path, 0)).isTrue
        assertThat(downloads.get()).isEqualTo(2)
    }
}
