package boat.torrent

import boat.utilities.HttpHelper
import org.apache.commons.codec.binary.Hex
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import jakarta.annotation.PreDestroy
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Service
class TorrentInfoService(httpHelper: HttpHelper) :
    HttpUser(httpHelper) {

    private val torrentService = TorrentService(httpHelper)

    // loaded when needed (the service must start even if the tracker list is not reachable), the list is cached
    private val trackerUrl: String
        get() = torrentService.trackerUrls.first()

    // shared and bounded, a search with many results doesn't open one thread per torrent
    internal val executor: ExecutorService = Executors.newFixedThreadPool(MAX_PARALLEL_TRACKER_REQUESTS) { runnable ->
        Thread(runnable, "tracker-stats").apply { isDaemon = true }
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
        const val MAX_PARALLEL_TRACKER_REQUESTS = 8
        private const val REFRESH_TIMEOUT_SECONDS = 30L
        private const val REQUEST_TIMEOUT_MILLIS = 10 * 1000
        /** Request-URI Too Long and Payload Too Large: the tracker refuses that many hashes at once. */
        private val URL_TOO_LONG_STATUSES = setOf(413, 414)

        /**
         * Trackers refuse the request somewhere above 100 hashes (url longer than about 6000 characters), this stays
         * well below that.
         */
        const val BATCH_SIZE = 40
        private val HASH_PATTERN = Regex("^[a-fA-F0-9]{40}$")
    }

    class TrackerStats(val seeders: Int, val leechers: Int)

    /**
     * Asks the tracker for the seeders and leechers of the torrents which are not cached at a multifilehoster (cached
     * ones download from the hoster, so their stats don't matter). Many hashes go into one scrape request, which is
     * much faster than a request per torrent. The batches are asked in parallel by a few threads shared by all searches.
     */
    fun refreshTorrentStats(torrentList: MutableList<Torrent>) {
        val startOfRefresh = System.currentTimeMillis()
        val torrentsByHash = torrentList.filter { torrent -> torrent.cached.size == 0 }.groupByHash()
        try {
            val tracker = trackerUrl
            val tasks = torrentsByHash.keys.chunked(BATCH_SIZE).map { hashes ->
                Callable { applyStats(scrape(tracker, hashes), torrentsByHash) }
            }
            // tasks which didn't finish in time are cancelled
            executor.invokeAll(tasks, REFRESH_TIMEOUT_SECONDS, TimeUnit.SECONDS).forEach { future ->
                if (future.isCancelled) return@forEach
                try {
                    future.get()
                } catch (e: ExecutionException) {
                    log.error("Refresh of a batch of torrents failed", e.cause)
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            log.error("refreshTorrentStats() interrupted")
        }
        log.info("Refresh of {} torrents took {}ms", torrentsByHash.size, System.currentTimeMillis() - startOfRefresh)
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }

    private fun List<Torrent>.groupByHash(): Map<String, List<Torrent>> =
        filter { torrent -> torrent.torrentId.matches(HASH_PATTERN) }.groupBy { torrent -> torrent.torrentId.lowercase() }

    private fun applyStats(stats: Map<String, TrackerStats>, torrentsByHash: Map<String, List<Torrent>>) {
        stats.forEach { (hash, trackerStats) ->
            torrentsByHash[hash]?.forEach { torrent ->
                torrent.seeder = trackerStats.seeders
                torrent.leecher = trackerStats.leechers
                torrent.statsVerified = true
            }
        }
    }

    /**
     * The stats of the [hashes] (lowercase hex) the tracker knows. The answer lists the hashes sorted and only the
     * known ones, so the stats are matched by hash. A tracker refuses requests with too many hashes (url too long),
     * then the hashes are asked for in two halves, down to single requests. If the tracker can't be reached at all
     * nothing is retried.
     */
    internal fun scrape(trackerUrl: String, hashes: List<String>): Map<String, TrackerStats> {
        val queryUrl = trackerUrl.replace("announce", "scrape") + "?info_hash=" +
                hashes.joinToString("&info_hash=") { hash -> TorrentHelper.urlEncode(Hex.decodeHex(hash)) }
        val response = try {
            httpHelper.getBinary(queryUrl, REQUEST_TIMEOUT_MILLIS)
        } catch (e: IOException) {
            log.warn("tracker {} not reachable: {}", trackerUrl, e.message)
            return emptyMap()
        }
        // only a url which is too long is worth smaller batches, any other error (404, 5xx, no scrape) would be the same for them
        if (response.status in URL_TOO_LONG_STATUSES && hashes.size > 1) {
            val halves = hashes.chunked((hashes.size + 1) / 2)
            return halves.fold(emptyMap()) { stats, half -> stats + scrape(trackerUrl, half) }
        }
        val answer = if (response.status in 200..299) Bencode.decode(response.body) as? Map<*, *> else null
        return parseScrapeAnswer(answer)
    }

    private fun parseScrapeAnswer(answer: Map<*, *>?): Map<String, TrackerStats> {
        val files = answer?.get("files") as? Map<*, *> ?: return emptyMap()
        val stats = mutableMapOf<String, TrackerStats>()
        files.forEach { (key, value) ->
            val entry = value as? Map<*, *> ?: return@forEach
            val seeders = (entry["complete"] as? Long)?.toInt() ?: return@forEach
            val leechers = (entry["incomplete"] as? Long)?.toInt() ?: return@forEach
            val hash = (key as? String)?.takeIf { it.length == 20 }?.map { "%02x".format(it.code) }?.joinToString("") ?: return@forEach
            stats[hash] = TrackerStats(seeders, leechers)
        }
        return stats
    }
}
