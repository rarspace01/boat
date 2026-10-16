package boat.multifileHoster

import boat.torrent.Torrent
import boat.torrent.TorrentFile

interface MultifileHoster {
    fun addTorrentToDownloadQueue(toBeAddedTorrent: Torrent): AddTransferResult
    fun getRemoteTorrents(): List<Torrent>
    fun enrichCacheStateOfTorrents(torrents: List<Torrent>)
    fun delete(remoteTorrent: Torrent)
    fun getFilesFromTorrent(torrent: Torrent): List<TorrentFile>
    fun getPrio(): Int
    fun getRemainingTrafficInMB(): Double
    fun getMaximumActiveTransfers(): Int
    fun getName(): String
    override fun toString(): String
}

/**
 * @param message raw response of the hoster
 * @param remoteId id of the transfer as listed by the hoster, null if the hoster couldn't resolve it
 */
data class AddTransferResult(val message: String, val remoteId: String? = null)
