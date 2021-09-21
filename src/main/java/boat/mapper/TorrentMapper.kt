package boat.mapper

import boat.torrent.TorrentStatus

class TorrentMapper {
    companion object {
        fun mapRemoteStatus(remoteStatusCode: Int?): TorrentStatus {
            return when (remoteStatusCode) {
                0 -> TorrentStatus.ADDED
                1 -> TorrentStatus.DOWNLOADING_TO_MULTIHOSTER
                2, 3 -> TorrentStatus.UPLOADING_TO_MULTIHOSTER
                4 -> TorrentStatus.READY_TO_BE_DOWNLOADED
                in 5..11 -> TorrentStatus.ERROR
                else -> TorrentStatus.UNKNOWN
            }
        }

        fun mapRemoteStatus(remoteStatus: String?): TorrentStatus {
            return when(remoteStatus) {
            "waiting","queued" -> TorrentStatus.ADDED
            "finished","seeding" -> TorrentStatus.READY_TO_BE_DOWNLOADED
            "running" -> TorrentStatus.DOWNLOADING_TO_MULTIHOSTER
            "deleted" -> TorrentStatus.DELETED
            "banned","error","timeout" -> TorrentStatus.ERROR
             else -> TorrentStatus.UNKNOWN
            }
        }
    }
}
