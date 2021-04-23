package pirateboat.info

import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Service
import pirateboat.multifileHoster.MultifileHosterService
import pirateboat.torrent.Torrent
import java.util.function.Consumer

@Slf4j
@Service
class TorrentMetaService(private val multifileHosterService: MultifileHosterService) {
    private val localStatusStorage = HashMap<String, String>()

    var activeTorrents: MutableList<Torrent> = ArrayList()

    fun refreshTorrents() {
        val remoteTorrents = multifileHosterService.remoteTorrents
        remoteTorrents.forEach(Consumer { torrent: Torrent ->
            if (isReadyForDownloadStatus(torrent.status)) {
                val localStatus = localStatusStorage[torrent.torrentId]
                torrent.status = localStatus ?: torrent.status
            } else {
                localStatusStorage.remove(torrent.torrentId)
            }
        })
        activeTorrents = ArrayList()
        activeTorrents.addAll(remoteTorrents)
    }

    private fun isReadyForDownloadStatus(status: String?): Boolean {
        return status != null && status.toLowerCase().matches(Regex("finished|seeding|ready"))
    }

    fun updateTorrent(torrentUpdate: Torrent) {
        localStatusStorage[torrentUpdate.torrentId] = torrentUpdate.status
    }
}