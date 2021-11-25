package boat.info

import boat.multifileHoster.MultifileHosterService
import boat.torrent.Torrent
import boat.utilities.LoggerDelegate
import org.springframework.stereotype.Service
import java.util.Locale
import java.util.function.Consumer

@Service
class TorrentMetaService(private val multifileHosterService: MultifileHosterService) {
    companion object {
        private val logger by LoggerDelegate()
    }

    val localStatusStorage = HashMap<String, String>()

    var activeTorrents: MutableList<Torrent> = ArrayList()

    fun refreshTorrents() {
        val remoteTorrents = multifileHosterService.remoteTorrents
        remoteTorrents.forEach(
            Consumer { torrent: Torrent ->
                if (isReadyForDownloadStatus(torrent.remoteStatusText)) {
                    val localStatus = localStatusStorage[torrent.torrentId]
                    torrent.remoteStatusText = localStatus ?: torrent.remoteStatusText
                } else {
                    localStatusStorage.remove(torrent.torrentId)
                }
            }
        )
        activeTorrents = ArrayList()
        activeTorrents.addAll(remoteTorrents)
    }

    fun getTorrentsToDownload(): List<Torrent> {
        val remoteTorrents = multifileHosterService.remoteTorrentsForDownload.apply {
            forEach(
                Consumer { torrent: Torrent ->
                    if (isReadyForDownloadStatus(torrent.remoteStatusText)) {
                        val localStatus = localStatusStorage[torrent.torrentId]
                        torrent.remoteStatusText = localStatus ?: torrent.remoteStatusText
                    } else {
                        localStatusStorage.remove(torrent.torrentId)
                    }
                }
            )
        }
        activeTorrents = ArrayList()
        activeTorrents.addAll(remoteTorrents)
        return activeTorrents
    }

    private fun isReadyForDownloadStatus(status: String?): Boolean {
        return status != null && status.lowercase(Locale.getDefault()).matches(Regex("finished|seeding|ready"))
    }

    fun updateTorrent(torrentUpdate: Torrent) {
        localStatusStorage[torrentUpdate.torrentId] = torrentUpdate.remoteStatusText
        torrentUpdate.remoteId?.let {
            localStatusStorage[torrentUpdate.remoteId] = torrentUpdate.remoteStatusText
        }
    }
}
