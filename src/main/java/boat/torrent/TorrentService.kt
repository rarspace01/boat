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
            val maxChars = 1000
            val currentChars = AtomicInteger(0)
            val trackerList: MutableList<String> = ArrayList()
            return try {
                val inputStream = TorrentService::class.java.getResourceAsStream("/trackers.txt")
                val inputStreamReader = InputStreamReader(inputStream, Charset.defaultCharset())
                val bufferedReader = BufferedReader(inputStreamReader)
                var line: String
                while (bufferedReader.readLine().also { line = it } != null) {
                    if (currentChars.get() + line.length < maxChars) {
                        trackerList.add(line)
                        currentChars.addAndGet(line.length)
                    }
                }
                trackerList
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
        val releaseTags: MutableList<String> = ArrayList()
        return try {
            val inputStream = TorrentService::class.java.getResourceAsStream("/releasetags.txt")
            val inputStreamReader = InputStreamReader(inputStream, Charset.defaultCharset())
            val bufferedReader = BufferedReader(inputStreamReader)
            var line: String
            while (bufferedReader.readLine().also { line = it } != null) {
                releaseTags.add(line.lowercase(Locale.getDefault()))
            }
            releaseTags
        } catch (e: IOException) {
            emptyList()
        }
    }
}