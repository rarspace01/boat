package boat.utilities

import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URLConnection
import java.nio.channels.Channels
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private const val USER_AGENT_BROWSER = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0"

@Service
class HttpHelper {
    fun getPage(url: String, params: List<String>?, cookies: String?): String {
        return getPage(url, params, cookies, null, 30 * 1000)
    }

    fun getPageWithShortTimeout(url: String, params: List<String>?, cookies: String?): String {
        return getPage(url, params, cookies, null, 10 * 1000)
    }

    fun getPage(url: String, params: List<String>?, cookies: String?, body:String? = null, timeout: Int, headers: Map<String, String>? = null): String {
        return try {
            fetchPage(url, cookies, body, timeout, headers, trustAllCertificates = false)
        } catch (exception: SSLException) {
            if (mayFallBackToUnverifiedTls(url)) {
                log.warn("TLS verification failed for [{}], retrying without verification: {}", hostOf(url), redactSecrets(exception.message))
                runCatching { fetchPage(url, cookies, body, timeout, headers, trustAllCertificates = true) }
                    .onFailure { log.error("HttpHelper [{}]: {}", hostOf(url), redactSecrets(it.message)) }
                    .getOrDefault("")
            } else {
                log.error("TLS verification failed for [{}]: {}", hostOf(url), redactSecrets(exception.message))
                ""
            }
        } catch (exception: Exception) {
            // no stack trace, the message of connection errors contains the url with its api key
            log.error("HttpHelper [{}]: {} {}", hostOf(url), exception.javaClass.simpleName, redactSecrets(exception.message))
            ""
        }
    }

    private fun fetchPage(url: String, cookies: String?, body: String?, timeout: Int, headers: Map<String, String>?, trustAllCertificates: Boolean): String {
        val buildString = StringBuilder()
        run {
            val connection = URI.create(url).toURL().openConnection()
            body?.let {
                if (connection is HttpsURLConnection) {
                    connection.requestMethod = "POST"
                }
                connection.doOutput = true
                connection.doInput = true
            }
            if (trustAllCertificates && connection is HttpsURLConnection) {
                connection.sslSocketFactory = trustAllSocketFactory
            }
            connection.setRequestProperty("User-Agent", USER_AGENT_BROWSER)
            connection.setRequestProperty("Accept-Charset", charset)
            connection.setRequestProperty(
                "Accept",
                "text/html,application/xhtml+xml,application/xml,application/json;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
            )
            headers?.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }
            if (body != null && connection.getRequestProperty("Content-Type") == null) {
                connection.setRequestProperty("Content-Type", "application/json")
            }
            connection.connectTimeout = timeout
            connection.readTimeout = timeout
            if (cookies != null) {
                connection.setRequestProperty("Cookie", cookies)
            }
            body?.let {
                val outputStream = connection.getOutputStream()
                val outputStreamWriter = OutputStreamWriter(outputStream, "UTF-8")
                outputStreamWriter.write(body)
                outputStreamWriter.flush()
                outputStreamWriter.close()
                outputStream.close()
            }

            val response = connection.getInputStream()

//            for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
//                System.out.println(header.getKey() + "=" + header.getValue());
//            }
            val contentType = connection.getHeaderField("Content-Type")
            var charset: String? = null
            if (contentType != null) {
                for (param in contentType.replace(" ", "").split(";".toRegex()).toTypedArray()) {
                    if (param.startsWith("charset=")) {
                        charset = param.split("=".toRegex(), 2).toTypedArray()[1]
                        break
                    }
                }
            }
            if (charset == null) {
                charset = "UTF-8"
            }
            BufferedReader(InputStreamReader(response, charset)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    buildString.append(line).append(System.getProperty("line.separator"))
                }
            }
        }
        return buildString.toString()
    }

    fun getRawPage(url: String, params: List<String?>?, cookies: String?, timeout: Int): ByteArray? {
        return try {
            fetchRawPage(url, cookies, timeout, trustAllCertificates = false)
        } catch (exception: SSLException) {
            if (mayFallBackToUnverifiedTls(url)) {
                log.warn("TLS verification failed for [{}], retrying without verification: {}", hostOf(url), redactSecrets(exception.message))
                runCatching { fetchRawPage(url, cookies, timeout, trustAllCertificates = true) }.getOrNull()
            } else {
                log.error("TLS verification failed for [{}]: {}", hostOf(url), redactSecrets(exception.message))
                null
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun fetchRawPage(url: String, cookies: String?, timeout: Int, trustAllCertificates: Boolean): ByteArray {
        val connection = URI.create(url).toURL().openConnection()
        if (trustAllCertificates && connection is HttpsURLConnection) {
            connection.sslSocketFactory = trustAllSocketFactory
        }
        connection.setRequestProperty("User-Agent", USER_AGENT_BROWSER)
        connection.setRequestProperty("Accept-Charset", charset)
        connection.setRequestProperty(
            "Accept",
            "text/html,application/xhtml+xml,application/xml,application/json;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        )
        connection.connectTimeout = timeout
        connection.readTimeout = timeout
        if (cookies != null) {
            connection.setRequestProperty("Cookie", cookies)
        }
        return IOUtils.toByteArray(connection.getInputStream())
    }

    /**
     * Fetches a page from a url given by a user. Only http(s) urls to public addresses are fetched, redirects are
     * followed by hand so every hop is checked, and at most [maxBytes] are read. Returns null if the url is not
     * allowed or the page can't be read. Set `ALLOW_PRIVATE_URLS=true` to also allow addresses in the local network.
     */
    fun getPublicPage(url: String, maxBytes: Int = 1024 * 1024, timeout: Int = 10 * 1000): String? {
        val allowPrivate = PropertiesHelper.getProperty("ALLOW_PRIVATE_URLS") == "true"
        var currentUrl = url
        repeat(MAX_REDIRECTS + 1) {
            if (!isAllowedPublicUrl(currentUrl, allowPrivate)) {
                log.warn("refusing to fetch [{}]", hostOf(currentUrl))
                return null
            }
            try {
                val connection = URI.create(currentUrl).toURL().openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = timeout
                connection.readTimeout = timeout
                connection.setRequestProperty("User-Agent", USER_AGENT_BROWSER)
                val status = connection.responseCode
                if (status in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return null
                    currentUrl = URI.create(currentUrl).resolve(location).toString()
                    return@repeat
                }
                if (status !in 200..299) return null
                val bytes = connection.inputStream.use { it.readNBytes(maxBytes + 1) }
                if (bytes.size > maxBytes) {
                    log.warn("[{}] is larger than {} bytes, not reading it", hostOf(currentUrl), maxBytes)
                    return null
                }
                return String(bytes, Charsets.UTF_8)
            } catch (exception: Exception) {
                log.error("HttpHelper [{}]: {} {}", hostOf(currentUrl), exception.javaClass.simpleName, redactSecrets(exception.message))
                return null
            }
        }
        log.warn("too many redirects for [{}]", hostOf(url))
        return null
    }

    fun getPage(url: String): String {
        return getPage(url, null, null, null, 30 * 1000)
    }

    fun getPage(url: String, body: String?, headers: Map<String, String>? = null): String {
        return getPage(url, null, null, body, 10 * 1000, headers)
    }

    fun getPageWithShortTimeout(url: String): String {
        return getPage(url, null, null, null, 10 * 1000)
    }

    fun getPage(url: String, timeout: Int): String {
        return getPage(url, null, null, null, timeout)
    }

    fun getPage(url: String, params: List<String>?): String {
        return getPage(url, params, null)
    }

    fun isWebsiteResponding(baseUrl: String, timeout: Int): Boolean {
        return getPage(baseUrl, timeout).isNotEmpty()
    }

    companion object {
        private const val charset = "UTF-8" // Or in Java 7 and later, use the constant: java.nio.charset.StandardCharsets.UTF_8.name()
        private val log = LoggerFactory.getLogger(HttpHelper::class.java)

        // Calls to these hosts carry API keys or deliver the data we act on, they are never made without verification.
        private val VERIFIED_HOST_SUFFIXES = listOf(
            "premiumize.me", "alldebrid.com", "themoviedb.org", "thetvdb.com", "github.com", "githubusercontent.com"
        )

        private val secretPattern = Regex("(api_?key|token|password|passwd)=[^&\\s\"']+", RegexOption.IGNORE_CASE)

        /** Hides the values of api keys and similar parameters, e.g. before an url ends up in a log. */
        fun redactSecrets(text: String?): String? = text?.replace(secretPattern) { it.groupValues[1] + "=***" }

        private const val MAX_REDIRECTS = 3

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
                // 100.64.0.0/10 (carrier grade NAT), 0.0.0.0/8
                4 -> !((bytes[0].toInt() and 0xff) == 100 && (bytes[1].toInt() and 0xc0) == 64) && bytes[0].toInt() != 0
                // fc00::/7 (unique local)
                16 -> (bytes[0].toInt() and 0xfe) != 0xfc
                else -> false
            }
        }

        private fun hostOf(url: String): String = runCatching { URI.create(url).host }.getOrNull() ?: "unknown"

        /**
         * Torrent sites often have broken certificates, so for them a failed verification falls back to an
         * unverified connection. Hosters, metadata providers, GitHub and every url with an api key never do.
         */
        internal fun mayFallBackToUnverifiedTls(url: String): Boolean {
            val host = hostOf(url).lowercase()
            if (VERIFIED_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }) return false
            return !url.contains("apikey=", ignoreCase = true) && !url.contains("api_key=", ignoreCase = true)
        }

        private val trustAllSocketFactory by lazy {
            SSLContext.getInstance("TLS").apply { init(null, trustAllCerts, null) }.socketFactory
        }

        private val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return emptyArray()
                }

                override fun checkClientTrusted(
                    certs: Array<X509Certificate>,
                    authType: String
                ) {
                }

                override fun checkServerTrusted(
                    certs: Array<X509Certificate>,
                    authType: String
                ) {
                }
            }
        )

        @Throws(IOException::class)
        fun downloadFileToPath(fileURLFromTorrent: String, localPath: String) {
            val remoteUrl = URI.create(fileURLFromTorrent).toURL()
            val rbc = Channels.newChannel(remoteUrl.openStream())
            val fos = FileOutputStream(localPath)
            fos.channel.transferFrom(rbc, 0, Long.MAX_VALUE)
        }
    }
}
