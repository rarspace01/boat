package pirateboat.multifileHoster

import pirateboat.torrent.Torrent
import pirateboat.torrent.TorrentFile

interface MultifileHoster {
    fun addTorrentToQueue(toBeAddedTorrent: Torrent?): String?
    val remoteTorrents: List<Torrent?>?
    fun enrichCacheStateOfTorrents(torrents: List<Torrent?>?)
    fun delete(remoteTorrent: Torrent?)
    fun getFilesFromTorrent(torrent: Torrent?): List<TorrentFile?>?
    val prio: Int
    val name: String?
}