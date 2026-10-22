package boat.torrent

import boat.utilities.HttpHelper
import boat.utilities.LoggerDelegate
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class TorrentService(val httpHelper: HttpHelper?) {

    companion object {
        const val TRACKER_LIST_URL = "https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_all_http.txt"
        private val TRACKER_LIST_MAX_AGE_NANOS = TimeUnit.HOURS.toNanos(24)
        private val TRACKER_LIST_RETRY_NANOS = TimeUnit.MINUTES.toNanos(5)

        /** Used while the list can't be loaded, e.g. when GitHub is not reachable or rate limits. */
        val DEFAULT_TRACKERS = listOf(
            "http://tracker.opentrackr.org:1337/announce",
            "http://tracker.qu.ax:6969/announce",
            "http://tracker.renfei.net:8080/announce",
        )
    }

    private val logger by LoggerDelegate()

    // the loaded list with the time it is loaded again: after a day, or after a few minutes if it couldn't be loaded
    private class TrackerList(val urls: List<String>, val reloadAtNanos: Long)

    @Volatile
    private var trackerList: TrackerList? = null

    /** Never empty. */
    val trackerUrls: List<String>
        get() {
            val current = trackerList
            if (current != null && System.nanoTime() - current.reloadAtNanos < 0) {
                return current.urls
            }
            return loadTrackerUrls(current)
        }

    @Synchronized
    private fun loadTrackerUrls(previous: TrackerList?): List<String> {
        // loaded by another thread while this one was waiting
        trackerList?.takeIf { it !== previous && System.nanoTime() - it.reloadAtNanos < 0 }?.let { return it.urls }
        val loaded = try {
            httpHelper?.getPage(TRACKER_LIST_URL)?.split("\n")?.map { it.trim() }?.filter { it.startsWith("http") }
        } catch (e: IOException) {
            null
        }
        val urls = loaded?.takeIf { it.isNotEmpty() }
        if (urls == null) {
            if (httpHelper != null) logger.warn("could not load the tracker list, using the default trackers")
            // keeps an older list if there is one
            val fallback = previous?.urls ?: DEFAULT_TRACKERS
            trackerList = TrackerList(fallback, System.nanoTime() + TRACKER_LIST_RETRY_NANOS)
            return fallback
        }
        trackerList = TrackerList(urls, System.nanoTime() + TRACKER_LIST_MAX_AGE_NANOS)
        return urls
    }

    private val cachedReleaseTags: List<String> by lazy {
        try {
            val inputStream = this.javaClass.getResourceAsStream("/releasetags.txt")
            InputStreamReader(inputStream, Charset.defaultCharset()).use { it.readText() }.split("\n").map { it.lowercase() }
        } catch (e: IOException) {
            emptyList()
        }
    }

    fun getReleaseTags(): List<String> = cachedReleaseTags
}
