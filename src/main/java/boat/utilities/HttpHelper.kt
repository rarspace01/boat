package boat.utilities

import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.net.URLConnection
import java.nio.channels.Channels
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Service
class HttpHelper {
    fun getPage(url: String, params: List<String>?, cookies: String?): String {
        return getPage(url, params, cookies, 30 * 1000)
    }

    fun getPageWithShortTimeout(url: String, params: List<String>?, cookies: String?): String {
        return getPage(url, params, cookies, 10 * 1000)
    }

    fun getPage(url: String, params: List<String>?, cookies: String?, timeout: Int): String {
        val returnString: String
        val buildString = StringBuilder()
        val connection: URLConnection
        try {
            val sc = SSLContext.getInstance("SSL")
            sc.init(null, trustAllCerts, null)
            connection = URL(url).openConnection()
            if (connection is HttpsURLConnection) {
                connection.sslSocketFactory = sc.socketFactory
            }
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:68.0) Gecko/20100101 Firefox/68.0")
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
        } catch (ignored: IOException) {
        } catch (ignored: NoSuchAlgorithmException) {
        } catch (ignored: KeyManagementException) {
        }
        returnString = buildString.toString()
        return returnString
    }

    fun getRawPage(url: String?, params: List<String?>?, cookies: String?, timeout: Int): ByteArray? {
        val connection: URLConnection
        try {
            val sc = SSLContext.getInstance("SSL")
            sc.init(null, trustAllCerts, null)
            connection = URL(url).openConnection()
            if (connection is HttpsURLConnection) {
                connection.sslSocketFactory = sc.socketFactory
            }
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:68.0) Gecko/20100101 Firefox/68.0"
            )
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
            return IOUtils.toByteArray(response)
        } catch (ignored: IOException) {
        } catch (ignored: NoSuchAlgorithmException) {
        } catch (ignored: KeyManagementException) {
        }
        return null
    }

    fun getPage(url: String): String {
        return getPage(url, null, null)
    }

    fun getPageWithShortTimeout(url: String): String {
        return getPage(url, null, null, 10 * 1000)
    }

    fun getPage(url: String, timeout: Int): String {
        return getPage(url, null, null, timeout)
    }

    fun getPage(url: String, params: List<String>?): String {
        return getPage(url, params, null)
    }

    fun isWebsiteResponding(baseUrl: String, timeout: Int): Boolean {
        return getPage(baseUrl, timeout).isNotEmpty()
    }

    val externalHostname: String
        get() {
            val builder = ProcessBuilder()
            builder.command("bash", "-c", "dig -x $(curl -s checkip.amazonaws.com) +short")
            builder.directory(File(System.getProperty("user.home")))
            var output = ""
            val error = ""
            try {
                val process = builder.start()
                process.waitFor(5, TimeUnit.SECONDS)
                output = String(process.inputStream.readAllBytes())
            } catch (e: Exception) {
                log.error("{}\nOutput from process:\n{}\nError from Process:\n{}", e.message, output, error)
                e.printStackTrace()
            }
            return output
        }

    companion object {
        private const val charset = "UTF-8" // Or in Java 7 and later, use the constant: java.nio.charset.StandardCharsets.UTF_8.name()
        private val log = LoggerFactory.getLogger(HttpHelper::class.java)
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
            val remoteUrl = URL(fileURLFromTorrent)
            val rbc = Channels.newChannel(remoteUrl.openStream())
            val fos = FileOutputStream(localPath)
            fos.channel.transferFrom(rbc, 0, Long.MAX_VALUE)
        }
    }
}
