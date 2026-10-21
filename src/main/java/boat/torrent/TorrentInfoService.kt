package boat.torrent

import boat.utilities.HttpHelper
import org.apache.commons.codec.binary.Hex
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import jakarta.annotation.PreDestroy
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

@Service
class TorrentInfoService(httpHelper: HttpHelper) :
    HttpUser(httpHelper) {

    private val trackerUrl = TorrentService(httpHelper).trackerUrls.first()

    // shared and bounded, a search with many results doesn't open one thread per torrent
    internal val executor: ExecutorService = Executors.newFixedThreadPool(MAX_PARALLEL_TRACKER_REQUESTS) { runnable ->
        Thread(runnable, "tracker-stats").apply { isDaemon = true }
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
        const val MAX_PARALLEL_TRACKER_REQUESTS = 8
        private const val REFRESH_TIMEOUT_SECONDS = 30L
    }

    /**
     * The trackers are asked one torrent at a time (the answer lists the hashes sorted, not in the order of the
     * request), by a few threads shared by all searches instead of one thread per torrent and search.
     */
    fun refreshTorrentStats(torrentList: MutableList<Torrent>) {
        val startOfRefresh = System.currentTimeMillis()
        val nonCachedTorrents = torrentList.filter { torrent -> torrent.cached.size == 0 }
        try {
            val tasks = nonCachedTorrents.map { torrent ->
                Callable { refreshSeedAndLeecherFromTracker(trackerUrl, listOf(torrent)) }
            }
            // tasks which didn't finish in time are cancelled
            executor.invokeAll(tasks, REFRESH_TIMEOUT_SECONDS, TimeUnit.SECONDS).forEach { future ->
                if (future.isCancelled) return@forEach
                try {
                    future.get()
                } catch (e: ExecutionException) {
                    log.error("Refresh of a torrent failed", e.cause)
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            log.error("refreshTorrentStats() interrupted")
        }
        log.info("Refresh took {}ms", System.currentTimeMillis() - startOfRefresh)
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }

    fun refreshSeedAndLeecherFromTracker(trackerUrl: String, torrentList: List<Torrent>) {
        val joinedHashes = torrentList
            .map { torrent -> torrent.torrentId }
            .filter { torrentHash -> torrentHash.isNotEmpty() && torrentHash.matches(Regex("^[a-fA-F0-9]{40}$")) }
            .map { torrentHash: String? -> TorrentHelper.urlEncode(Hex.decodeHex(torrentHash)) }
            .joinToString("&info_hash=")
        val queryUrl =
            "${trackerUrl.replace("announce", "scrape")}?info_hash=$joinedHashes"
        // log.info("$queryUrl")
        val page = httpHelper.getPage(queryUrl, 10*1000)
//        val splittedHashs = page
//                .replace("d5:files","")
//        .replace("d20:","")
//            .replace(Regex("d8:completei[0-9]+"),"")
//            .replace(Regex("e10:downloadedi[0-9]+"),"")
//            .replace(Regex("e10:incompletei[0-9]+e"),"")
//            .replace("eee","")
//            .split("e20:")
        val seederPattern = Pattern.compile("8:completei([0-9]+)e")
        val leecherPattern = Pattern.compile("10:incompletei([0-9]+)e")
        val seederMatcher = seederPattern.matcher(page)
        val leecherMatcher = leecherPattern.matcher(page)
        var seeder: Int
        val seederList = mutableListOf<Int>()
        val leecherList = mutableListOf<Int>()
        var leecher: Int
        while (seederMatcher.find()) {
            seeder = seederMatcher.group(1).toInt()
            seederList.add(seeder)
        }
        while (leecherMatcher.find()) {
            leecher = leecherMatcher.group(1).toInt()
            leecherList.add(leecher)
        }
        if (seederList.size == leecherList.size) {
            for (i in 0 until seederList.size) {
                if (seederList[i] > -1) {
                    torrentList[i].seeder = seederList[i]
                    torrentList[i].leecher = leecherList[i]
                    torrentList[i].statsVerified = true
                }
            }
        }
    }
}
