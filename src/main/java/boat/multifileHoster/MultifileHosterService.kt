package boat.multifileHoster

import boat.torrent.HttpUser
import boat.torrent.Torrent
import boat.torrent.TorrentFile
import boat.utilities.HttpHelper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.function.Consumer
import java.util.stream.Collectors

@Service
class MultifileHosterService @Autowired constructor(httpHelper: HttpHelper) : HttpUser(httpHelper) {
    private val multifileHosterList: MutableList<MultifileHoster> = mutableListOf(Premiumize(httpHelper), Alldebrid(httpHelper))
    private val multifileHosterListForDownloads: MutableList<MultifileHoster> = getEligibleMultifileHoster(httpHelper)

    private fun getEligibleMultifileHoster(httpHelper: HttpHelper): MutableList<MultifileHoster> {
        val eligibleList = mutableListOf<MultifileHoster>(Premiumize(httpHelper))
        if(!httpHelper.externalHostname.contains("happysrv")){
            log.info("httpHelper.externalHostname: {}", httpHelper.externalHostname)
            eligibleList.add(Alldebrid(httpHelper))
        }
        return eligibleList
    }

    fun getCachedStateOfTorrents(returnResults: List<Torrent>): List<Torrent> {
        multifileHosterList.forEach(Consumer { multifileHoster: MultifileHoster ->
            multifileHoster.enrichCacheStateOfTorrents(
                returnResults
            )
        })
        return returnResults
    }

    fun addTorrentToQueue(torrent: Torrent): String {
        // filter for traffic left
        val listOfMultihostersWithTrafficLeft = multifileHosterListForDownloads.filter { multifileHoster -> multifileHoster.getRemainingTrafficInMB() > 30000 }
        val potentialMultihosters = listOfMultihostersWithTrafficLeft.ifEmpty { multifileHosterList }

        return if (potentialMultihosters.size == 1) {
            potentialMultihosters[0].addTorrentToQueue(torrent)
        } else {
            val potentialCachedTorrentToDownload =
                getCachedStateOfTorrents(listOf(torrent)).stream().findFirst().orElse(torrent)
            val cachedMultihosters = potentialMultihosters
                .filter { multifileHoster: MultifileHoster ->
                    potentialCachedTorrentToDownload.cached.contains(multifileHoster.getName())
                }
            val multihostersToDownload = cachedMultihosters.ifEmpty { potentialMultihosters }
            multihostersToDownload
                .stream()
                .min(Comparator.comparingInt(MultifileHoster::getPrio))
                .orElse(potentialMultihosters.first())
                .addTorrentToQueue(torrent)
        }
    }

    val remoteTorrents: List<Torrent>
        get() = multifileHosterList.stream()
            .flatMap { multifileHoster: MultifileHoster -> multifileHoster.getRemoteTorrents().stream() }
            .collect(Collectors.toList())

    fun isSingleFileDownload(torrentToBeDownloaded: Torrent): Boolean {
        val tfList = getFilesFromTorrent(torrentToBeDownloaded)
        var sumFileSize = 0L
        var biggestFileYet = 0L
        for (tf in tfList) {
            if (tf.filesize > biggestFileYet) {
                biggestFileYet = tf.filesize
            }
            sumFileSize += tf.filesize
        }
        // if maxfilesize >90% sumSize --> Singlefile
        return biggestFileYet > 0.9 * sumFileSize
    }

    fun getSizeOfTorrentInMB(torrent: Torrent): Double {
        val size: Long = getFilesFromTorrent(torrent).sumOf { torrentFile: TorrentFile -> torrentFile.filesize }
        return size.toDouble() / 1024.0 / 1024.0
    }

    fun getRemainingTrafficInMB(): Double {
        return multifileHosterList.sumOf { multifileHoster: MultifileHoster -> multifileHoster.getRemainingTrafficInMB() }
    }

    fun getFilesFromTorrent(torrentToBeDownloaded: Torrent): List<TorrentFile> {
        val hoster = multifileHosterList.stream()
            .filter { multifileHoster: MultifileHoster -> multifileHoster.getName() == torrentToBeDownloaded.source }
            .findFirst()
        return if (hoster.isPresent) {
            hoster.get().getFilesFromTorrent(torrentToBeDownloaded)
        } else {
            ArrayList()
        }
    }

    fun getMainFileURLFromTorrent(torrentToBeDownloaded: Torrent): TorrentFile {
        val tfList = getFilesFromTorrent(torrentToBeDownloaded)
        // iterate over and check for One File Torrent
        var biggestFileYet: TorrentFile = tfList[0]
        for (tf in tfList) {
            if (tf.filesize > biggestFileYet.filesize) {
                biggestFileYet = tf
            }
        }
        return biggestFileYet
    }

    fun delete(torrent: Torrent) {
        val hoster = multifileHosterList.firstOrNull { multifileHoster: MultifileHoster -> multifileHoster.getName() == torrent.source }
        if (hoster != null) {
            hoster.delete(torrent)
        } else {
            log.error("Deletion of Torrent not possible: {}", torrent.toString())
        }
    }

    fun getActiveMultifileHosters():List<MultifileHoster>{
        return multifileHosterList
    }

    fun getActiveMultifileHosterForDownloads():List<MultifileHoster>{
        return multifileHosterListForDownloads
    }

    companion object {
        private val log = LoggerFactory.getLogger(MultifileHosterService::class.java)
    }

}