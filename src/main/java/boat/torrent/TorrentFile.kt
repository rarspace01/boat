package boat.torrent

data class TorrentFile(
    var id: String? = null,
    var name: String? = null,
    var filesize: Long = 0,
    var url: String,
)