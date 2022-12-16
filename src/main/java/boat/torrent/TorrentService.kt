package boat.torrent

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class TorrentService {
    private val trackerList: List<String> = ArrayList()
    private val releaseTags: List<String> = ArrayList()
    val trackerUrls: List<String>
        get() {
            if (trackerList.isNotEmpty()) {
                return trackerList
            }
            return try {
                val inputStream = TorrentService::class.java.getResourceAsStream("/trackers.txt")
                val inputStreamReader = InputStreamReader(inputStream, Charset.defaultCharset())
                inputStreamReader.readText().split("\n")
            } catch (e: IOException) {
                emptyList()
            }
        }
    val allTrackerUrls: List<String>
        get() {
            if (trackerList.isNotEmpty()) {
                return trackerList
            }
            val currentChars = AtomicInteger(0)
            val trackerList: MutableList<String> = ArrayList()
            return try {
                val inputStream = TorrentService::class.java.getResourceAsStream("/trackers.txt")
                val inputStreamReader = InputStreamReader(inputStream, Charset.defaultCharset())
                val bufferedReader = BufferedReader(inputStreamReader)
                var line: String
                while (bufferedReader.readLine().also { line = it } != null) {
                    trackerList.add(line)
                }
                trackerList
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