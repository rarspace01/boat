package boat.mapper

import boat.model.Transfer
import boat.model.TransferStatus
import boat.torrent.Torrent
import boat.torrent.TorrentHelper

class TorrentMapper {
    companion object {
        fun mapRemoteStatus(remoteStatusCode: Int?): TransferStatus {
            return when (remoteStatusCode) {
                0 -> TransferStatus.ADDED_TO_MULTIHOSTER
                1 -> TransferStatus.DOWNLOADING_TO_MULTIHOSTER
                2, 3 -> TransferStatus.UPLOADING_TO_MULTIHOSTER
                4 -> TransferStatus.READY_TO_BE_DOWNLOADED
                in 5..11 -> TransferStatus.ERROR
                else -> TransferStatus.UNKNOWN
            }
        }

        fun mapRemoteStatus(remoteStatus: String?): TransferStatus {
            return when (remoteStatus) {
                "waiting", "queued" -> TransferStatus.ADDED_TO_MULTIHOSTER
                "finished", "seeding" -> TransferStatus.READY_TO_BE_DOWNLOADED
                "running" -> TransferStatus.DOWNLOADING_TO_MULTIHOSTER
                "deleted" -> TransferStatus.DELETED
                "banned", "error", "timeout" -> TransferStatus.ERROR
                else -> TransferStatus.UNKNOWN
            }
        }

        fun mapTransferToTorrent(transfer: Transfer): Torrent {
            return Torrent(
                source = transfer.source,
                name = transfer.name,
                size = TorrentHelper.humanReadableByteCountBinary(transfer.sizeInBytes),
                magnetUri = transfer.uri,
                remoteStatusText = transfer.feedbackMessage ?: "",
                remoteTransferStatus = transfer.transferStatus,
                localTransferStatus = transfer.transferStatus,
                remoteId = transfer.remoteId ?: "",
            )
        }
    }
}
