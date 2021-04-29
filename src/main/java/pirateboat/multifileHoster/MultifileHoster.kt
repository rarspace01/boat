package pirateboat.multifileHoster

import pirateboat.torrent.Torrent
import pirateboat.torrent.TorrentFile

interface MultifileHoster {
    fun addTorrentToQueue(toBeAddedTorrent: Torrent): String
    fun getRemoteTorrents(): List<Torrent>
    fun enrichCacheStateOfTorrents(torrents: List<Torrent>)
    fun delete(remoteTorrent: Torrent)
    fun getFilesFromTorrent(torrent: Torrent): List<TorrentFile>
    fun getPrio(): Int
    fun getRemainingTrafficInMB(): Double
    fun getName(): String
}