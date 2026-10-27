package boat.utilities

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset
import java.security.cert.X509Certificate
import java.time.Duration
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private const val USER_AGENT_BROWSER = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0"
private const val ACCEPT_HEADER =
    "text/html,application/xhtml+xml,application/xml,application/json;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"

/**
 * Fetches pages for the search engines, hosters and metadata services on top of [java.net.http.HttpClient].
 * The page methods return an empty string if the page can't be read (the reason is logged, including the start of the
 * answer of the server), [getBinary] and [getPublicPage] are for callers which need to tell failures apart.
 * Certificates are verified, only the search sites of `UNVERIFIED_TLS_HOSTS` fall back to an unverified connection
 * ([mayFallBackToUnverifiedTls]).
 * https uses HTTP/2 where the server supports it, plain http uses HTTP/1.1.
 */
@Service
class HttpHelper {

    class BinaryResponse(val status: Int, val body: ByteArray)

    fun getPage(url: String): String = fetchPage(url, null, null, DEFAULT_TIMEOUT, null)

    fun getPage(url: String, timeout: Int): String = fetchPage(url, null, null, timeout, null)

    /** Sends [body] as POST if there is one. */
    fun getPage(url: String, body: String?, headers: Map<String, String>? = null): String =
        fetchPage(url, null, body, SHORT_TIMEOUT, headers)

    fun getPageWithCookies(url: String, cookies: String, timeout: Int = DEFAULT_TIMEOUT): String =
        fetchPage(url, cookies, null, timeout, null)

    private fun fetchPage(url: String, cookies: String?, body: String?, timeout: Int, headers: Map<String, String>?): String {
        return try {
            readPage(url, send(verifiedClientFor(url), url, cookies, body, timeout, headers))
        } catch (exception: SSLException) {
            if (mayFallBackToUnverifiedTls(url)) {
                log.warn("TLS verification failed for [{}], retrying without verification: {}", hostOf(url), redactSecrets(exception.message))
                try {
                    readPage(url, send(unverifiedClient, url, cookies, body, timeout, headers))
                } catch (retryException: Exception) {
                    log.error("HttpHelper [{}]: {}", hostOf(url), redactSecrets(retryException.message))
                    ""
                }
            } else {
                log.error("TLS verification failed for [{}]: {} (a search site with a broken certificate can be allowed with {})",
                    hostOf(url), redactSecrets(exception.message), UNVERIFIED_TLS_HOSTS)
                ""
            }
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            ""
        } catch (exception: Exception) {
            // no stack trace, the message of connection errors contains the url with its api key
            log.error("HttpHelper [{}]: {} {}", hostOf(url), exception.javaClass.simpleName, redactSecrets(exception.message))
            ""
        }
    }

    private fun send(
        client: HttpClient, url: String, cookies: String?, body: String?, timeout: Int, headers: Map<String, String>?
    ): HttpResponse<ByteArray> {
        val builder = HttpRequest.newBuilder(toUri(url))
            .timeout(Duration.ofMillis(timeout.toLong()))
            .setHeader("User-Agent", USER_AGENT_BROWSER)
            .setHeader("Accept-Charset", "UTF-8")
            .setHeader("Accept", ACCEPT_HEADER)
        cookies?.let { builder.setHeader("Cookie", it) }
        headers?.forEach { (key, value) -> builder.setHeader(key, value) }
        if (body != null) {
            if (headers == null || headers.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
                builder.setHeader("Content-Type", "application/json")
            }
            builder.POST(HttpRequest.BodyPublishers.ofString(body))
        } else {
            builder.GET()
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
    }

    private fun readPage(url: String, response: HttpResponse<ByteArray>): String {
        val text = String(response.body(), charsetOf(response))
        if (response.statusCode() !in 200..299) {
            // the answer often tells what is wrong (invalid key, blocked, rate limit)
            log.warn("HttpHelper [{}]: HTTP {} {}", hostOf(url), response.statusCode(), redactSecrets(text.take(200).replace(Regex("\\s+"), " ")))
            return ""
        }
        return text
    }

    private fun charsetOf(response: HttpResponse<ByteArray>): Charset {
        val contentType = response.headers().firstValue("Content-Type").orElse("")
        val name = contentType.replace(" ", "").split(";").firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter("=")?.trim('"')
        return name?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
    }

    /**
     * Fetches a page from a url given by a user. Only http(s) urls to public addresses are fetched, redirects are
     * followed by hand so every hop is checked, and at most [maxBytes] are read. Returns null if the url is not
     * allowed or the page can't be read. Set `ALLOW_PRIVATE_URLS=true` to also allow addresses in the local network.
     */
    fun getPublicPage(url: String, maxBytes: Int = 1024 * 1024, timeout: Int = SHORT_TIMEOUT): String? {
        val allowPrivate = PropertiesHelper.getProperty("ALLOW_PRIVATE_URLS") == "true"
        var currentUrl = url
        repeat(MAX_REDIRECTS + 1) {
            if (!isAllowedPublicUrl(currentUrl, allowPrivate)) {
                log.warn("refusing to fetch [{}]", hostOf(currentUrl))
                return null
            }
            try {
                val request = HttpRequest.newBuilder(URI.create(currentUrl))
                    .timeout(Duration.ofMillis(timeout.toLong()))
                    .setHeader("User-Agent", USER_AGENT_BROWSER)
                    .GET()
                    .build()
                val response = noRedirectClientFor(currentUrl).send(request, HttpResponse.BodyHandlers.ofInputStream())
                response.body().use { stream ->
                    val status = response.statusCode()
                    if (status in 300..399) {
                        val location = response.headers().firstValue("Location").orElse(null) ?: return null
                        currentUrl = URI.create(currentUrl).resolve(location).toString()
                        return@repeat
                    }
                    if (status !in 200..299) return null
                    val bytes = stream.readNBytes(maxBytes + 1)
                    if (bytes.size > maxBytes) {
                        log.warn("[{}] is larger than {} bytes, not reading it", hostOf(currentUrl), maxBytes)
                        return null
                    }
                    return String(bytes, Charsets.UTF_8)
                }
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            } catch (exception: Exception) {
                log.error("HttpHelper [{}]: {} {}", hostOf(currentUrl), exception.javaClass.simpleName, redactSecrets(exception.message))
                return null
            }
        }
        log.warn("too many redirects for [{}]", hostOf(url))
        return null
    }

    /**
     * Fetches a url without decoding the answer, for binary content like tracker answers or pages with their own
     * charset. [headers] replace the default ones (e.g. the User-Agent). Certificates are verified.
     * Unlike the page methods this doesn't hide failures: connection problems and timeouts are thrown as
     * [IOException], other status codes than 200 are returned.
     */
    @Throws(IOException::class)
    fun getBinary(url: String, timeout: Int = SHORT_TIMEOUT, headers: Map<String, String> = emptyMap()): BinaryResponse {
        val builder = HttpRequest.newBuilder(toUri(url))
            .timeout(Duration.ofMillis(timeout.toLong()))
            .setHeader("User-Agent", USER_AGENT_BROWSER)
        headers.forEach { (key, value) -> builder.setHeader(key, value) }
        val request = builder.GET().build()
        return try {
            val response = verifiedClientFor(url).send(request, HttpResponse.BodyHandlers.ofByteArray())
            BinaryResponse(response.statusCode(), response.body())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("interrupted", e)
        }
    }

    companion object {
        // what may stand in a url as it is: letters, digits, the delimiters and %-escapes
        private val urlCharacter = Regex("[A-Za-z0-9\\-._~:/?#@!$&'()*+,;=%]")

        /**
         * Links from the search sites contain characters a url must escape (`[ettv]`, spaces), browsers escape them
         * and so does this. Escapes which are already there stay.
         */
        internal fun toUri(url: String): URI = runCatching { URI.create(url) }.getOrElse {
            URI.create(url.map { char ->
                if (urlCharacter.matches(char.toString())) char.toString()
                else URLEncoder.encode(char.toString(), Charsets.UTF_8).replace("+", "%20")
            }.joinToString(""))
        }

        const val DEFAULT_TIMEOUT = 30 * 1000
        const val SHORT_TIMEOUT = 10 * 1000
        private const val MAX_REDIRECTS = 3
        private val log = LoggerFactory.getLogger(HttpHelper::class.java)

        // Calls to these hosts carry API keys or deliver the data we act on, they are never made without verification.
        private val VERIFIED_HOST_SUFFIXES = listOf(
            "premiumize.me", "alldebrid.com", "themoviedb.org", "thetvdb.com", "github.com", "githubusercontent.com"
        )

        private val secretPattern = Regex("(api_?key|token|password|passwd)=[^&\\s\"']+", RegexOption.IGNORE_CASE)

        /** Hides the values of api keys and similar parameters, e.g. before an url ends up in a log. */
        fun redactSecrets(text: String?): String? = text?.replace(secretPattern) { it.groupValues[1] + "=***" }

        // https uses HTTP/2 where the server offers it (the JDK negotiates it with ALPN and falls back to HTTP/1.1), plain
        // http stays on HTTP/1.1, so no HTTP/2 upgrade is attempted on it
        private fun clientBuilder(version: HttpClient.Version) =
            HttpClient.newBuilder().version(version).connectTimeout(Duration.ofSeconds(10))

        private val httpsClient: HttpClient by lazy {
            clientBuilder(HttpClient.Version.HTTP_2).followRedirects(HttpClient.Redirect.NORMAL).build()
        }
        private val plainClient: HttpClient by lazy {
            clientBuilder(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NORMAL).build()
        }
        private val httpsClientWithoutRedirects: HttpClient by lazy {
            clientBuilder(HttpClient.Version.HTTP_2).followRedirects(HttpClient.Redirect.NEVER).build()
        }
        private val plainClientWithoutRedirects: HttpClient by lazy {
            clientBuilder(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NEVER).build()
        }

        private fun isHttps(url: String) = url.startsWith("https:", ignoreCase = true)

        private fun verifiedClientFor(url: String) = if (isHttps(url)) httpsClient else plainClient

        private fun noRedirectClientFor(url: String) = if (isHttps(url)) httpsClientWithoutRedirects else plainClientWithoutRedirects

        // accepts every certificate: the host name is still checked, but against a certificate anyone can make, so this
        // is no protection against someone on the way (only for the hosts of UNVERIFIED_TLS_HOSTS)
        private val unverifiedClient: HttpClient by lazy {
            val sslContext = SSLContext.getInstance("TLS").apply { init(null, trustAllCerts, null) }
            clientBuilder(HttpClient.Version.HTTP_2).sslContext(sslContext).followRedirects(HttpClient.Redirect.NORMAL).build()
        }

        /** http(s) url whose host only resolves to public addresses (unless [allowPrivate]). */
        internal fun isAllowedPublicUrl(url: String, allowPrivate: Boolean = false): Boolean {
            val uri = runCatching { URI.create(url) }.getOrNull() ?: return false
            if (uri.scheme?.lowercase() !in listOf("http", "https") || uri.host.isNullOrBlank()) return false
            if (allowPrivate) return true
            val addresses = runCatching { InetAddress.getAllByName(uri.host) }.getOrNull() ?: return false
            return addresses.isNotEmpty() && addresses.all { isPublicAddress(it) }
        }

        internal fun isPublicAddress(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress
            ) return false
            val bytes = address.address
            return when (bytes.size) {
                4 -> isPublicIpv4(bytes)
                16 -> isPublicIpv6(bytes)
                else -> false
            }
        }

        private fun isPublicIpv4(bytes: ByteArray): Boolean {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            return when {
                // 0.0.0.0/8, 10/8, 127/8 (the latter two also isSiteLocal/isLoopback for an Inet4Address)
                first == 0 || first == 10 || first == 127 -> false
                // 100.64.0.0/10 (carrier grade NAT)
                first == 100 && (second and 0xc0) == 64 -> false
                // 169.254/16, 172.16/12, 192.168/16, for addresses taken out of IPv6 ones
                first == 169 && second == 254 -> false
                first == 172 && (second and 0xf0) == 16 -> false
                first == 192 && second == 168 -> false
                // 192.0.0.0/24 (IETF protocol assignments), 198.18.0.0/15 (benchmarking)
                first == 192 && second == 0 && (bytes[2].toInt() and 0xff) == 0 -> false
                first == 198 && (second and 0xfe) == 18 -> false
                // 224/4 multicast, 240.0.0.0/4 (reserved) and the broadcast address
                first >= 224 -> false
                else -> true
            }
        }

        /**
         * Besides fc00::/7 (unique local) the IPv6 addresses with an IPv4 address in them are only public if that one
         * is: NAT64 (64:ff9b::/96, 64:ff9b:1::/48), 6to4 (2002::/16) and the old IPv4 compatible ::a.b.c.d. The IPv4
         * mapped ::ffff:a.b.c.d come as Inet4Address already.
         */
        private fun isPublicIpv6(bytes: ByteArray): Boolean {
            fun word(index: Int) = ((bytes[index].toInt() and 0xff) shl 8) or (bytes[index + 1].toInt() and 0xff)
            return when {
                (bytes[0].toInt() and 0xfe) == 0xfc -> false
                word(0) == 0x64 && word(2) == 0xff9b && (0..3).all { word(4 + 2 * it) == 0 } -> isPublicIpv4(bytes.copyOfRange(12, 16))
                word(0) == 0x64 && word(2) == 0xff9b && word(4) == 1 -> false
                word(0) == 0x2002 -> isPublicIpv4(bytes.copyOfRange(2, 6))
                (0..11).all { bytes[it].toInt() == 0 } -> isPublicIpv4(bytes.copyOfRange(12, 16))
                // 2001:db8::/32 documentation
                word(0) == 0x2001 && word(2) == 0x0db8 -> false
                else -> true
            }
        }

        private fun hostOf(url: String): String = runCatching { toUri(url).host }.getOrNull() ?: "unknown"

        /** The setting with the hosts (comma separated) whose broken certificates are accepted. */
        const val UNVERIFIED_TLS_HOSTS = "UNVERIFIED_TLS_HOSTS"

        private fun unverifiedTlsHosts(): Set<String> =
            PropertiesHelper.getProperty(UNVERIFIED_TLS_HOSTS).orEmpty().split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

        /**
         * Only the hosts of [UNVERIFIED_TLS_HOSTS] (a search site with a broken certificate) fall back to an unverified
         * connection after a failed verification: anyone on the way could make that verification fail and then show
         * a certificate of their own, so it isn't done for every site. Hosters, metadata providers, GitHub and every url
         * with an api key never do, whatever the setting says.
         */
        internal fun mayFallBackToUnverifiedTls(url: String, allowedHosts: Set<String> = unverifiedTlsHosts()): Boolean {
            val host = hostOf(url).lowercase()
            if (host !in allowedHosts) return false
            if (VERIFIED_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }) return false
            return !url.contains("apikey=", ignoreCase = true) && !url.contains("api_key=", ignoreCase = true)
        }

        private val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}

                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
            }
        )
    }
}
