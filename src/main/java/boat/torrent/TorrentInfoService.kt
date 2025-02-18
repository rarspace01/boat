package boat.torrent

import boat.utilities.HttpHelper
import org.apache.commons.codec.binary.Hex
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

@Service
class TorrentInfoService(httpHelper: HttpHelper) :
    HttpUser(httpHelper) {

    private val trackerUrl = TorrentService().trackerUrls.first()

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    fun refreshTorrentStats(torrentList: MutableList<Torrent>) {
        val startOfRefresh = System.currentTimeMillis()
        val nonCachedTorrents = torrentList.filter { torrent -> torrent.cached.size == 0 }
        val parallelism: Int = 1.coerceAtLeast(nonCachedTorrents.size)
        val forkJoinPool = ForkJoinPool(parallelism)
        try {
            forkJoinPool.submit {
                nonCachedTorrents.parallelStream()
                    .forEach { torrent ->
                        refreshSeedAndLeecherFromTracker(trackerUrl, listOf(torrent))
                    }
            }.get()
        } catch (e: Exception) {
            log.error("Parallel refresh execution failed", e)
        } finally {
            forkJoinPool.shutdown()
            val awaitTermination = forkJoinPool.awaitTermination(30, TimeUnit.SECONDS)
            if (!awaitTermination) {
                forkJoinPool.shutdownNow()
                log.error("refreshTorrentStats() terminated after timeout")
            }
        }
        log.info("Refresh took {}ms", System.currentTimeMillis() - startOfRefresh)
    }

    fun refreshSeedAndLeecherFromTracker(trackerUrl: String, torrentList: List<Torrent>) {
        val joinedHashes = torrentList
            .map { torrent -> torrent.torrentId }
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
