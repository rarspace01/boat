package boat.torrent

import boat.utilities.HttpHelper
import org.apache.commons.codec.binary.Hex
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.regex.Pattern


@Service
class TorrentInfoService(httpHelper: HttpHelper) :
    HttpUser(httpHelper) {

    private val trackerUrl = "http://tracker.opentrackr.org:1337/announce"

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    fun refreshTorrentStats(torrentList: MutableList<Torrent>) {
        val nonCachedTorrents = torrentList.filter { torrent -> torrent.cached.size == 0 }
        val maximumQuerySize = 50
        if (nonCachedTorrents.size <= maximumQuerySize) {
            refreshSeedAndLeecherFromTracker(trackerUrl, nonCachedTorrents)
        } else {
            for (i in nonCachedTorrents.indices step maximumQuerySize) {
                refreshSeedAndLeecherFromTracker(
                    trackerUrl,
                    nonCachedTorrents.subList(
                        i,
                        (i + maximumQuerySize).coerceAtMost(nonCachedTorrents.size - 1)
                    )
                )
            }
        }
    }

    fun refreshSeedAndLeecherFromTracker(trackerUrl: String, torrentList: List<Torrent>) {
        val joinedHashes = torrentList
            .map { torrent -> torrent.torrentId }
            .map { torrentHash: String? -> TorrentHelper.urlEncode(Hex.decodeHex(torrentHash)) }
            .joinToString("&info_hash=")
        val queryUrl =
            "${trackerUrl.replace("announce", "scrape")}?info_hash=${joinedHashes}"
        //log.info("$queryUrl")
        val page = httpHelper.getPage(queryUrl)
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
        if (seederList.size == leecherList.size && seederList.size == torrentList.size) {
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