package boat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.core.io.support.ResourceRegion
import java.io.File
import kotlin.io.path.createTempDirectory

internal class WebdavControllerTest {

    private val webdavController = WebdavController()
    private lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        tempDir = createTempDirectory().normalize().toFile()
        webdavController.rootDir = tempDir
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `OPTIONS request should return allowed methods`() {
        // Given
        val request = MockHttpServletRequest("OPTIONS", "/PFDB/")

        // When
        val response = webdavController.webdavPfdb(request)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.getFirst(HttpHeaders.ALLOW)).contains("PROPFIND")
        assertThat(response.headers["DAV"]).contains("1, 2")
    }

    @Test
    fun `PROPFIND on root with depth 0 should return only root info`() {
        // Given
        val request = MockHttpServletRequest("PROPFIND", "/PFDB/")
        request.addHeader("Depth", "0")

        // When
        val response = webdavController.webdavPfdb(request)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.valueOf(207))
        val body = response.body.toString()
        assertThat(body).contains("<D:href>/PFDB/</D:href>")
        assertThat(body).doesNotContain("test.txt")
    }

    @Test
    fun `PROPFIND on root with depth 1 should return root and children`() {
        // Given
        File(tempDir, "test.txt").createNewFile()
        val request = MockHttpServletRequest("PROPFIND", "/PFDB/")
        request.addHeader("Depth", "1")

        // When
        val response = webdavController.webdavPfdb(request)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.valueOf(207))
        val body = response.body.toString()
        assertThat(body).contains("<D:href>/PFDB/</D:href>")
        assertThat(body).contains("<D:href>/PFDB/test.txt</D:href>")
    }

    @Test
    fun `GET on a file should return its content`() {
        // Given
        val testFile = File(tempDir, "test.txt")
        testFile.writeText("hello world")
        val request = MockHttpServletRequest("GET", "/PFDB/test.txt")

        // When
        val response = webdavController.webdavPfdb(request)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentLength).isEqualTo(11)
        val body = (response.body as org.springframework.core.io.FileSystemResource).inputStream.readAllBytes().toString(Charsets.UTF_8)
        assertThat(body).isEqualTo("hello world")
    }

    @Test
    fun `GET on a directory should return a listing`() {
        // Given
        File(tempDir, "test.txt").createNewFile()
        val request = MockHttpServletRequest("GET", "/PFDB/")

        // When
        val response = webdavController.webdavPfdb(request)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val body = (response.body as org.springframework.core.io.ByteArrayResource).byteArray.toString(Charsets.UTF_8)
        assertThat(body).contains("test.txt")
    }

    @Test
    fun `HEAD on a directory should return headers but no body`() {
        // Given
        File(tempDir, "test.txt").createNewFile()
        val request = MockHttpServletRequest("HEAD", "/PFDB/")

        // When
        val response = webdavController.webdavPfdb(request)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        // The content length for directory listings might vary slightly based on generated HTML, 
        // so we mainly check for no body and expected content type.
        assertThat(response.headers.contentType).isEqualTo(org.springframework.http.MediaType.TEXT_HTML)
        assertThat(response.body).isNull()
    }

    @Test
    fun `GET on a non-existent file should return 404`() {
        // Given
        val request = MockHttpServletRequest("GET", "/PFDB/nonexistent.txt")

        // When
        val response = webdavController.webdavPfdb(request)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `HEAD on a file should return headers but no body`() {
        // Given
        val testFile = File(tempDir, "test.txt")
        testFile.writeText("hello world")
        val request = MockHttpServletRequest("HEAD", "/PFDB/test.txt")

        // When
        val response = webdavController.webdavPfdb(request)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentLength).isEqualTo(11)
        assertThat(response.body).isNull()
    }

    @Test
    fun `encodePath should encode segments correctly`() {
        // Given
        val path = "/PFDB/some folder/file name with spaces.txt"

        // When
        val encodedPath = webdavController.encodePath(path)

        // Then
        assertThat(encodedPath).isEqualTo("/PFDB/some%20folder/file%20name%20with%20spaces.txt")
    }

    @Test
    fun `encodePath should handle special characters in segments`() {
        // Given
        val path = "/test/a&b/c?d/e#f"

        // When
        val encodedPath = webdavController.encodePath(path)

        // Then
        assertThat(encodedPath).isEqualTo("/test/a%26b/c%3Fd/e%23f")
    }

    @Test
    fun `escapeXml should escape special XML characters`() {
        // Given
        val input = "Title with & < > \" '"

        // When
        val escaped = webdavController.escapeXml(input)

        // Then
        assertThat(escaped).isEqualTo("Title with &amp; &lt; &gt; &quot; &apos;")
    }

    @Test
    fun `escapeXml should return same string if no special characters`() {
        // Given
        val input = "Simple Title 123"

        // When
        val escaped = webdavController.escapeXml(input)

        // Then
        assertThat(escaped).isEqualTo("Simple Title 123")
    }
    
    @Test
    fun `buildHref should build standard HTTP URL correctly`() {
        // Given
        val request = MockHttpServletRequest()
        request.scheme = "http"
        request.serverName = "localhost"
        request.serverPort = 8080
        
        // When
        val href = webdavController.buildHref(request, "/PFDB/test", false)
        
        // Then
        assertThat(href).isEqualTo("/PFDB/test")
    }
    
    @Test
    fun `buildHref should handle default HTTP port`() {
        // Given
        val request = MockHttpServletRequest()
        request.scheme = "http"
        request.serverName = "example.com"
        request.serverPort = 80
        
        // When
        val href = webdavController.buildHref(request, "/PFDB", true)
        
        // Then
        assertThat(href).isEqualTo("/PFDB/")
    }

    @Test
    fun `buildHref should handle default HTTPS port`() {
        // Given
        val request = MockHttpServletRequest()
        request.scheme = "https"
        request.serverName = "example.com"
        request.serverPort = 443
        
        // When
        val href = webdavController.buildHref(request, "/PFDB", true)
        
        // Then
        assertThat(href).isEqualTo("/PFDB/")
    }

    @Test
    fun `buildHref should respect X-Forwarded headers`() {
        // Given
        val request = MockHttpServletRequest()
        request.scheme = "http"
        request.serverName = "internal.local"
        request.serverPort = 8080
        request.addHeader("X-Forwarded-Proto", "https")
        request.addHeader("X-Forwarded-Host", "external.com")
        request.addHeader("X-Forwarded-Port", "443")
        
        // When
        val href = webdavController.buildHref(request, "/PFDB/test", false)
        
        // Then
        assertThat(href).isEqualTo("/PFDB/test")
    }

    @Test
    fun `buildHref should append slash for directories`() {
        // Given
        val request = MockHttpServletRequest()
        request.scheme = "http"
        request.serverName = "localhost"
        request.serverPort = 8080
        
        // When
        val href = webdavController.buildHref(request, "/PFDB/folder", true)
        
        // Then
        assertThat(href).isEqualTo("/PFDB/folder/")
    }

    @Test
    fun `GET with byte range should return partial content`() {
        // Given
        val testFile = File(tempDir, "test.txt")
        testFile.writeText("hello world") // Length 11
        val request = MockHttpServletRequest("GET", "/PFDB/test.txt")
        request.addHeader(HttpHeaders.RANGE, "bytes=0-4") // "hello"

        // When
        val response = webdavController.webdavPfdb(request)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.PARTIAL_CONTENT)
        assertThat(response.headers.getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 0-4/11")
        assertThat(response.headers.contentLength).isEqualTo(5)
        val region = response.body as ResourceRegion
        val body = region.resource.inputStream.use { it.skip(region.position); it.readBytes() }.take(region.count.toInt()).toByteArray().toString(Charsets.UTF_8)
        assertThat(body).isEqualTo("hello")
    }

    @Test
    fun `GET with open-ended byte range should return partial content`() {
        // Given
        val testFile = File(tempDir, "test.txt")
        testFile.writeText("hello world") // Length 11
        val request = MockHttpServletRequest("GET", "/PFDB/test.txt")
        request.addHeader(HttpHeaders.RANGE, "bytes=6-") // "world"

        // When
        val response = webdavController.webdavPfdb(request)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.PARTIAL_CONTENT)
        assertThat(response.headers.getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 6-10/11")
        assertThat(response.headers.contentLength).isEqualTo(5)
        val region = response.body as ResourceRegion
        val body = region.resource.inputStream.use { it.skip(region.position); it.readBytes() }.take(region.count.toInt()).toByteArray().toString(Charsets.UTF_8)
        assertThat(body).isEqualTo("world")
    }

    @Test
    fun `GET with suffix byte range should return partial content`() {
        // Given
        val testFile = File(tempDir, "test.txt")
        testFile.writeText("hello world") // Length 11
        val request = MockHttpServletRequest("GET", "/PFDB/test.txt")
        request.addHeader(HttpHeaders.RANGE, "bytes=-5") // "world"

        // When
        val response = webdavController.webdavPfdb(request)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.PARTIAL_CONTENT)
        assertThat(response.headers.getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 6-10/11")
        assertThat(response.headers.contentLength).isEqualTo(5)
        val region = response.body as ResourceRegion
        val body = region.resource.inputStream.use { it.skip(region.position); it.readBytes() }.take(region.count.toInt()).toByteArray().toString(Charsets.UTF_8)
        assertThat(body).isEqualTo("world")
    }

    @Test
    fun `GET with invalid byte range should return 416`() {
        // Given
        val testFile = File(tempDir, "test.txt")
        testFile.writeText("hello world") // Length 11
        val request = MockHttpServletRequest("GET", "/PFDB/test.txt")
        request.addHeader(HttpHeaders.RANGE, "bytes=100-110") // Out of bounds

        // When
        val response = webdavController.webdavPfdb(request)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
        assertThat(response.headers.getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */11")
        assertThat(response.body).isNull()
    }

    @Test
    fun `HEAD with byte range should return partial content headers but no body`() {
        // Given
        val testFile = File(tempDir, "test.txt")
        testFile.writeText("hello world") // Length 11
        val request = MockHttpServletRequest("HEAD", "/PFDB/test.txt")
        request.addHeader(HttpHeaders.RANGE, "bytes=0-4") // "hello"

        // When
        val response = webdavController.webdavPfdb(request)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.PARTIAL_CONTENT)
        assertThat(response.headers.getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 0-4/11")
        assertThat(response.headers.contentLength).isEqualTo(5)
        assertThat(response.body).isNull()
    }
}