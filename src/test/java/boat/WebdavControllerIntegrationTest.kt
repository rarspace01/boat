package boat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.support.ResourceRegion
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import java.io.File
import kotlin.io.path.createTempDirectory

@SpringBootTest
class WebdavControllerIntegrationTest {

    @Autowired
    private lateinit var webdavController: WebdavController

    private lateinit var tempDir: File
    private lateinit var testFile: File

    @BeforeEach
    fun setUp() {
        tempDir = createTempDirectory("pfdb_test").normalize().toFile()
        webdavController.rootDir = tempDir

        testFile = File(tempDir, "video.mp4")
        testFile.writeText("0123456789") // Length is exactly 10 bytes for easy math
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `GET without range returns full 200 OK with inline disposition`() {
        val request = MockHttpServletRequest("GET", "/PFDB/video.mp4")
        val response = webdavController.webdavPfdb(request)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentLength).isEqualTo(10)
        assertThat(response.headers.getFirst(HttpHeaders.CONTENT_DISPOSITION))
            .isEqualTo("inline; filename=\"video.mp4\"; filename*=UTF-8''video.mp4")
        
        val resource = response.body as FileSystemResource
        assertThat(resource.inputStream.readBytes().toString(Charsets.UTF_8)).isEqualTo("0123456789")
    }

    @Test
    fun `GET with standard byte range returns 206 Partial Content and Spring Auto-Generates Headers`() {
        val request = MockHttpServletRequest("GET", "/PFDB/video.mp4")
        request.addHeader(HttpHeaders.RANGE, "bytes=0-4")
        val response = webdavController.webdavPfdb(request)

        assertThat(response.statusCode).isEqualTo(HttpStatus.PARTIAL_CONTENT)
        assertThat(response.headers.getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 0-4/10")
        assertThat(response.headers.contentLength).isEqualTo(5)

        val region = response.body as ResourceRegion
        assertThat(region.resource.contentLength()).isEqualTo(10)
        assertThat(region.position).isEqualTo(0)
        assertThat(region.count).isEqualTo(5)
    }

    @Test
    fun `GET with open-ended byte range returns 206 Partial Content`() {
        val request = MockHttpServletRequest("GET", "/PFDB/video.mp4")
        request.addHeader(HttpHeaders.RANGE, "bytes=5-")
        val response = webdavController.webdavPfdb(request)

        assertThat(response.statusCode).isEqualTo(HttpStatus.PARTIAL_CONTENT)
        assertThat(response.headers.getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 5-9/10")
        assertThat(response.headers.contentLength).isEqualTo(5)

        val region = response.body as ResourceRegion
        assertThat(region.resource.contentLength()).isEqualTo(10)
        assertThat(region.position).isEqualTo(5)
        assertThat(region.count).isEqualTo(5)
    }

    @Test
    fun `GET with suffix byte range returns 206 Partial Content`() {
        val request = MockHttpServletRequest("GET", "/PFDB/video.mp4")
        request.addHeader(HttpHeaders.RANGE, "bytes=-5")
        val response = webdavController.webdavPfdb(request)

        assertThat(response.statusCode).isEqualTo(HttpStatus.PARTIAL_CONTENT)
        assertThat(response.headers.getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 5-9/10")
        assertThat(response.headers.contentLength).isEqualTo(5)

        val region = response.body as ResourceRegion
        assertThat(region.resource.contentLength()).isEqualTo(10)
        assertThat(region.position).isEqualTo(5)
        assertThat(region.count).isEqualTo(5)
    }
}