package pirateboat.multifileHoster

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import pirateboat.torrent.HttpUser
import pirateboat.torrent.Torrent
import pirateboat.torrent.TorrentFile
import pirateboat.utilities.HttpHelper
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors

@Service
class MultifileHosterService @Autowired constructor(httpHelper: HttpHelper?) : HttpUser(httpHelper) {
    private val multifileHosterList: MutableList<MultifileHoster> = ArrayList()
    fun getCachedStateOfTorrents(returnResults: List<Torrent>): List<Torrent> {
        multifileHosterList.forEach(Consumer { multifileHoster: MultifileHoster -> multifileHoster.enrichCacheStateOfTorrents(returnResults) })
        return returnResults
    }

    fun addTorrentToQueue(torrent: Torrent): String {
        val potentialCachedTorrentToDownload = getCachedStateOfTorrents(listOf(torrent)).stream().findFirst().orElse(torrent)
        return multifileHosterList.stream()
                .filter { multifileHoster: MultifileHoster -> multifileHoster.name == potentialCachedTorrentToDownload.cached.stream().findFirst().orElse("") }
                .min(Comparator.comparingInt { obj: MultifileHoster -> obj.prio })
                .orElse(multifileHosterList[0])
                .addTorrentToQueue(torrent)
    }

    val remoteTorrents: List<Torrent>
        get() = multifileHosterList.stream()
                .flatMap { multifileHoster: MultifileHoster -> multifileHoster.remoteTorrents.stream() }
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

    fun getFilesFromTorrent(torrentToBeDownloaded: Torrent): List<TorrentFile> {
        val hoster = multifileHosterList.stream().filter { multifileHoster: MultifileHoster -> multifileHoster.name == torrentToBeDownloaded.source }.findFirst()
        return if (hoster.isPresent) {
            hoster.get().getFilesFromTorrent(torrentToBeDownloaded)
        } else {
            ArrayList()
        }
    }

    fun getMainFileURLFromTorrent(torrentToBeDownloaded: Torrent): String? {
        val tfList = getFilesFromTorrent(torrentToBeDownloaded)
        var remoteURL: String? = null
        // iterate over and check for One File Torrent
        var biggestFileYet: Long = 0
        for (tf in tfList) {
            if (tf.filesize > biggestFileYet) {
                biggestFileYet = tf.filesize
                remoteURL = tf.url
            }
        }
        return remoteURL
    }

    fun delete(torrent: Torrent) {
        val hoster = multifileHosterList.stream().filter { multifileHoster: MultifileHoster -> multifileHoster.name == torrent.source }.findFirst()
        if (hoster.isPresent) {
            hoster.get().delete(torrent)
        } else {
            log.error("Deletion of Torrent not possible: {}", torrent.toString())
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MultifileHosterService::class.java)
    }

    init {
        multifileHosterList.add(Premiumize(httpHelper))
        //currently disabled multifileHosterList.add(new Alldebrid(httpHelper));
    }
}