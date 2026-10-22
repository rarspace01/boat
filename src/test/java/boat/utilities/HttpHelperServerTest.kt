package boat.utilities

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

internal class HttpHelperServerTest {

    private lateinit var server: HttpServer
    private val requests = CopyOnWriteArrayList<Map<String, String>>()
    private val helper = HttpHelper()

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    private fun respond(exchange: HttpExchange, status: Int, body: ByteArray, contentType: String? = null) {
        contentType?.let { exchange.responseHeaders.add("Content-Type", it) }
        exchange.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
        if (body.isNotEmpty()) exchange.responseBody.use { it.write(body) }
        exchange.close()
    }

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.add(
                mapOf(
                    "method" to exchange.requestMethod,
                    "path" to exchange.requestURI.toString(),
                    "cookie" to (exchange.requestHeaders.getFirst("Cookie") ?: ""),
                    "contentType" to (exchange.requestHeaders.getFirst("Content-Type") ?: ""),
                    "authorization" to (exchange.requestHeaders.getFirst("Authorization") ?: ""),
                    "userAgent" to (exchange.requestHeaders.getFirst("User-Agent") ?: ""),
                    "body" to String(exchange.requestBody.readAllBytes()),
                )
            )
            when {
                exchange.requestURI.path == "/ok" -> respond(exchange, 200, "hello".toByteArray())
                exchange.requestURI.path == "/latin" -> respond(exchange, 200, "Café".toByteArray(Charsets.ISO_8859_1), "text/html; charset=ISO-8859-1")
                exchange.requestURI.path == "/utf8" -> respond(exchange, 200, "Café".toByteArray(Charsets.UTF_8), "text/html")
                exchange.requestURI.path == "/missing" -> respond(exchange, 404, "{\"error\":\"nothing here apikey=secret123\"}".toByteArray())
                exchange.requestURI.path == "/redirect" -> { exchange.responseHeaders.add("Location", "/ok"); respond(exchange, 302, ByteArray(0)) }
                exchange.requestURI.path == "/bytes" -> respond(exchange, 200, byteArrayOf(0xff.toByte(), 0x00, 0x80.toByte()))
                exchange.requestURI.path == "/slow" -> { Thread.sleep(1500); respond(exchange, 200, "late".toByteArray()) }
                else -> respond(exchange, 500, ByteArray(0))
            }
        }
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun shouldGetAPage() {
        assertThat(helper.getPage(url("/ok"))).isEqualTo("hello")
        assertThat(requests.single()["method"]).isEqualTo("GET")
        assertThat(requests.single()["userAgent"]).contains("Mozilla")
    }

    @Test
    fun shouldDecodeWithTheCharsetOfTheAnswer() {
        assertThat(helper.getPage(url("/latin"))).isEqualTo("Café")
        assertThat(helper.getPage(url("/utf8"))).isEqualTo("Café")
    }

    @Test
    fun shouldReturnNothingForErrorStatus() {
        assertThat(helper.getPage(url("/missing"))).isEmpty()
        assertThat(helper.getPage(url("/other"))).isEmpty()
    }

    @Test
    fun shouldFollowRedirects() {
        assertThat(helper.getPage(url("/redirect"))).isEqualTo("hello")
    }

    @Test
    fun shouldPostABodyAsJsonByDefaultAndSendHeaders() {
        helper.getPage(url("/ok"), "{\"a\":1}", mapOf("Authorization" to "Bearer abc"))

        val request = requests.single()
        assertThat(request["method"]).isEqualTo("POST")
        assertThat(request["body"]).isEqualTo("{\"a\":1}")
        assertThat(request["contentType"]).isEqualTo("application/json")
        assertThat(request["authorization"]).isEqualTo("Bearer abc")
    }

    @Test
    fun shouldKeepAContentTypeOfTheCaller() {
        helper.getPage(url("/ok"), "a=1", mapOf("content-type" to "application/x-www-form-urlencoded"))

        assertThat(requests.single()["contentType"]).isEqualTo("application/x-www-form-urlencoded")
    }

    @Test
    fun shouldSendCookies() {
        helper.getPageWithCookies(url("/ok"), "lw=s")

        assertThat(requests.single()["cookie"]).isEqualTo("lw=s")
    }

    @Test
    fun shouldGiveUpAfterTheTimeout() {
        val start = System.currentTimeMillis()

        assertThat(helper.getPage(url("/slow"), 300)).isEmpty()
        assertThat(System.currentTimeMillis() - start).isLessThan(1400)
    }

    @Test
    fun shouldReturnTheBytesAndStatusOfBinaryRequests() {
        val ok = helper.getBinary(url("/bytes"))
        val missing = helper.getBinary(url("/missing"))

        assertThat(ok.status).isEqualTo(200)
        assertThat(ok.body).isEqualTo(byteArrayOf(0xff.toByte(), 0x00, 0x80.toByte()))
        assertThat(missing.status).isEqualTo(404)
    }

    @Test
    fun shouldLetTheCallerReplaceTheUserAgentOfBinaryRequests() {
        helper.getBinary(url("/bytes"), headers = mapOf("User-Agent" to "boat-test"))
        helper.getBinary(url("/bytes"))

        assertThat(requests.map { it["userAgent"] }).containsExactly("boat-test", requests.last()["userAgent"])
        assertThat(requests.last()["userAgent"]).contains("Mozilla")
    }

    @Test
    fun shouldNotLogApiKeysOfErrorAnswers() {
        assertThat(HttpHelper.redactSecrets("{\"error\":\"nothing here apikey=secret123\"}")).doesNotContain("secret123")
    }
}
