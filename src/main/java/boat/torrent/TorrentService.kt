package boat.torrent

import boat.utilities.HttpHelper
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicInteger

class TorrentService(val httpHelper: HttpHelper?) {
    private val trackerList: List<String> = ArrayList()
    private val releaseTags: List<String> = ArrayList()
    val trackerUrls: List<String>
        get() {
            if (trackerList.isNotEmpty()) {
                return trackerList
            }
            return try {
                val trackers = httpHelper?.getPage("https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_all_http.txt")
                trackers?.split("\n")?.filter(String::isNotEmpty) ?: emptyList()
            } catch (e: IOException) {
                emptyList()
            }
        }

    fun getReleaseTags(): List<String> {
        if (releaseTags.isNotEmpty()) {
            return releaseTags
        }
        return try {
            val inputStream = this.javaClass.getResourceAsStream("/releasetags.txt")
            val inputStreamReader = InputStreamReader(inputStream, Charset.defaultCharset())
            inputStreamReader.readText().split("\n").map { it.lowercase() }
        } catch (e: IOException) {
            emptyList()
        }
    }
}