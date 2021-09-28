package boat.mapper

import boat.model.Transfer
import boat.torrent.Torrent
import boat.torrent.TorrentStatus

class TorrentMapper {
    companion object {
        fun mapRemoteStatus(remoteStatusCode: Int?): TorrentStatus {
            return when (remoteStatusCode) {
                0 -> TorrentStatus.ADDED_TO_MULTIHOSTER
                1 -> TorrentStatus.DOWNLOADING_TO_MULTIHOSTER
                2, 3 -> TorrentStatus.UPLOADING_TO_MULTIHOSTER
                4 -> TorrentStatus.READY_TO_BE_DOWNLOADED
                in 5..11 -> TorrentStatus.ERROR
                else -> TorrentStatus.UNKNOWN
            }
        }

        fun mapRemoteStatus(remoteStatus: String?): TorrentStatus {
            return when(remoteStatus) {
            "waiting","queued" -> TorrentStatus.ADDED_TO_MULTIHOSTER
            "finished","seeding" -> TorrentStatus.READY_TO_BE_DOWNLOADED
            "running" -> TorrentStatus.DOWNLOADING_TO_MULTIHOSTER
            "deleted" -> TorrentStatus.DELETED
            "banned","error","timeout" -> TorrentStatus.ERROR
             else -> TorrentStatus.UNKNOWN
            }
        }

        fun mapTransferToTorrent(transfer:Transfer):Torrent {
            val torrent = Torrent(transfer.source)
            torrent.magnetUri = transfer.uri
            torrent.remoteStatusText = transfer.feedbackMessage
            torrent.localTorrentStatus = transfer.torrentStatus
            return torrent
        }
    }
}
