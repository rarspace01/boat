package boat.torrent

import boat.info.MediaType
import boat.model.TransferStatus
import org.apache.logging.log4j.util.Strings
import org.springframework.web.util.HtmlUtils
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.*
import java.util.regex.Pattern

data class Torrent(
    var source: String = "NONE",
    var name: String = "",
    var date: Date? = null,
    var size: String = "",
    var sizeInMB: Double = 0.0,
    var seeder: Int = -1,
    var leecher: Int = -1,
    var magnetUri: String = "",
    var category: String? = null,
    var searchRating: Double = 0.0,
    var debugRating: String = "",
    var remoteStatusText: String = "",
    var remoteStatusCode: Int = 0,
    var remoteTransferStatus: TransferStatus = TransferStatus.NONE,
    var localTransferStatus: TransferStatus = TransferStatus.NONE,
    var remoteProgress: String? = null,
    var remoteProgressInPercent: Double = 0.0,
    var progress: String? = null,
    var eta: Duration = Duration.ZERO,
    var remoteId: String = "",
    var folder_id: String? = null,
    var file_id: String = "",
    var remoteUrl: String = "",
    var cached: MutableList<String> = mutableListOf(),
    var fileList: List<TorrentFile> = mutableListOf(),
    var isVerified: Boolean = false,
    var statsVerified: Boolean = false,

    ) : Comparable<Torrent> {

    private val magnetPattern = Pattern.compile("(btih:)([a-zA-Z0-9]*)&*")
    private val magnetNamePattern = Pattern.compile("[?&]dn=([^&]*)", Pattern.CASE_INSENSITIVE)
    override fun toString(): String {
        return toHtml()
    }

    fun toHtml(remoteMediaId: String? = null, mediaType: MediaType? = null): String {
        val stringBuilder = StringBuilder()
        val seedRatio: Double
        seedRatio = if (leecher > 0) {
            seeder.toDouble() / leecher.toDouble()
        } else {
            seeder.toDouble()
        }
        var magnetUriBase64 = ""
        if (magnetUri.isNotEmpty()) {
            magnetUriBase64 = Base64.getUrlEncoder().encodeToString(magnetUri.toByteArray(StandardCharsets.UTF_8))
        }
        stringBuilder.append(String.format("[%s][%s]", HtmlUtils.htmlEscape(name), HtmlUtils.htmlEscape(retrieveSourceName())))
        if (!isRemoteTorrent) {
            stringBuilder.append(String.format("[%s@<!-- ][%s/%s][ -->%.2f]", HtmlUtils.htmlEscape(size), leecher, seeder, seedRatio))
            stringBuilder.append(String.format("<!-- R: %.2f -->", searchRating))
        }
        if (cached.isNotEmpty()) {
            stringBuilder.append("⚡")
        }
        if (isVerified) {
            stringBuilder.append("✅")
        }
        if (isNotARemoteTorrent(magnetUriBase64)) {
            stringBuilder.append("<a href=\"./download?d=").append(magnetUriBase64)
            if (!remoteMediaId.isNullOrEmpty()) {
                stringBuilder.append("&mid=").append(URLEncoder.encode(remoteMediaId, StandardCharsets.UTF_8))
            }
            if (mediaType != null) {
                stringBuilder.append("&mt=").append(mediaType.name)
            }
            stringBuilder.append("\">Download</a>")
        }
        if (Strings.isNotEmpty(debugRating)) {
            stringBuilder.append(debugRating)
        }


        if (remoteStatusText.isNotEmpty() && remoteProgress != null) {
            // from the hoster, like everything here it goes into html
            var progress = "/" + HtmlUtils.htmlEscape(remoteProgress.orEmpty())
            if (remoteStatusText.contains("Uploading")) {
                progress = ""
            }
            stringBuilder.append(" ").append(HtmlUtils.htmlEscape(remoteStatusText.replace("finished".toRegex(), "Waiting for Upload")))
                .append(progress)
        }
        if (eta != Duration.ZERO) {
            stringBuilder.append(" ETA:").append(eta)
        }
        if (!isNotARemoteTorrent(magnetUriBase64)) {
            stringBuilder.append(
                String.format(
                    "%s - %s - folder_id: %s file_id: %s",
                    HtmlUtils.htmlEscape(torrentId.toString()),
                    HtmlUtils.htmlEscape(remoteId.toString()),
                    HtmlUtils.htmlEscape(folder_id.toString()),
                    HtmlUtils.htmlEscape(file_id.toString())
                )
            )
        }
        stringBuilder.append("</br>")
        return stringBuilder.toString()
    }

    private fun isNotARemoteTorrent(magnetUriBase64: String): Boolean {
        return magnetUriBase64.isNotEmpty() && remoteStatusText.isEmpty()
    }

    private fun retrieveSourceName(): String {
        return if (isRemoteTorrent && source.isNotEmpty()) source[0].toString() else source
    }

    private val isRemoteTorrent: Boolean
        get() = remoteId.isNotEmpty()

    override fun equals(other: Any?): Boolean {
        return other is Torrent && torrentId == other.torrentId
    }

    override fun hashCode(): Int {
        return torrentId.hashCode()
    }

    /**
     * The btih hash of the magnet uri, null for other uris (e.g. direct download urls).
     */
    val infoHash: String?
        get() {
            if (magnetUri.isEmpty()) {
                return null
            }
            val matcher = magnetPattern.matcher(magnetUri)
            return if (matcher.find() && matcher.group(2).isNotEmpty()) matcher.group(2).lowercase(Locale.ROOT) else null
        }

    /**
     * Never empty: the info hash, else the remote id, else the uri, else an id of this instance. An empty id would be
     * contained in every uri and so match any transfer.
     */
    val torrentId: String
        get() = infoHash
            ?: remoteId.ifEmpty { null }
            ?: magnetUri.ifEmpty { null }
            ?: "torrent-" + System.identityHashCode(this)
    val torrentNameFromUri: String?
        get() {
            if (magnetUri.isEmpty()) {
                return null
            }
            val matcher = magnetNamePattern.matcher(magnetUri)
            return if (matcher.find() && matcher.group(1).isNotBlank()) {
                matcher.group(1)
            } else {
                null
            }
        }
    override fun compareTo(other: Torrent): Int {
        return if (sizeInMB < other.sizeInMB) -1 else 1
    }

    fun getByteSize(): Long {
        return (sizeInMB * 1024 * 1024).toLong()
    }

    companion object {
        fun of(magnetUri: String): Torrent {
            return Torrent(
                source = "\uD83D\uDDA5",
                name = "",
                size = "",
                magnetUri = magnetUri,
                remoteStatusText = "",
                remoteTransferStatus = TransferStatus.NONE,
                localTransferStatus = TransferStatus.NONE,
                remoteId = "",
            )
        }
    }
}
