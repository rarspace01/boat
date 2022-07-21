package boat.torrent

import boat.model.TransferStatus
import org.apache.logging.log4j.util.Strings
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.Objects
import java.util.regex.Pattern

class Torrent(
    var source: String,
    var name: String,
    var date: Date? = null,
    var size: String,
    var lsize: Double = 0.0,
    var seeder: Int = -1,
    var leecher: Int = -1,
    var magnetUri: String,
    var category: String? = null,
    var searchRating: Double = 0.0,
    var debugRating: String = "",
    var remoteStatusText: String,
    var remoteStatusCode: Int = 0,
    var remoteTransferStatus: TransferStatus,
    var localTransferStatus: TransferStatus,
    var remoteProgress: String? = null,
    var remoteProgressInPercent: Double = 0.0,
    var progress: String? = null,
    var eta: Duration? = null,
    var remoteId: String,
    var folder_id: String? = null,
    var file_id: String? = null,
    var remoteUrl: String? = null,
    var cached: List<String> = ArrayList(),
    var fileList: List<TorrentFile> = ArrayList(),
    var isVerified: Boolean = false,
    var statsVerified: Boolean = false,

    ) : Comparable<Torrent> {

    private val magnetPattern = Pattern.compile("(btih:)([a-zA-Z0-9]*)&*")
    private val magnetNamePattern = Pattern.compile("dn=(.*?)&")
    override fun toString(): String {
        val stringBuilder = StringBuilder()
        val seedRatio: Double
        seedRatio = if (leecher > 0) {
            seeder.toDouble() / leecher.toDouble()
        } else {
            seeder.toDouble()
        }
        var magnetUriBase64: String? = ""
        if (magnetUri != null) {
            magnetUriBase64 = Base64.getUrlEncoder().encodeToString(magnetUri!!.toByteArray(StandardCharsets.UTF_8))
        }
        stringBuilder.append(String.format("[%s]\uD83C\uDFE0[%s]", name, retrieveSourceName()))
        if (!isRemoteTorrent) {
            stringBuilder.append(String.format("[%s][%s/%s@%.2f]", size, leecher, seeder, seedRatio))
            stringBuilder.append(String.format("R: %.2f ", searchRating))
        }
        if (isNotARemoteTorrent(magnetUriBase64)) {
            stringBuilder.append("<a href=\"./boat/download/?d=").append(magnetUriBase64).append("\">Download</a>")
        }
        if (Strings.isNotEmpty(debugRating)) {
            stringBuilder.append(" 🏭").append(debugRating)
        }


        /*        if (getTorrentId() != null) {
            stringBuilder.append(" TID:" + getTorrentId());
        }*/if (remoteStatusText != null && remoteProgress != null) {
            var progress = "/" + remoteProgress
            if (remoteStatusText!!.contains("Uploading")) {
                progress = ""
            }
            stringBuilder.append(" ").append(remoteStatusText!!.replace("finished".toRegex(), "Waiting for Upload"))
                .append(progress)
        }
        if (eta != null) {
            stringBuilder.append(" ETA:").append(eta)
        }
        if (!isNotARemoteTorrent(magnetUriBase64)) {
            stringBuilder.append(String.format("%s - %s - folder_id: %s file_id: %s", torrentId, remoteId, folder_id, file_id))
        }
        stringBuilder.append("</br>")
        return stringBuilder.toString()
    }

    private fun isNotARemoteTorrent(magnetUriBase64: String?): Boolean {
        return magnetUriBase64 != null && magnetUriBase64.length > 0 && remoteStatusText == null
    }

    private fun retrieveSourceName(): String {
        return if (isRemoteTorrent && source != null) source!![0].toString() else source!!
    }

    private val isRemoteTorrent: Boolean
        private get() = remoteId != null && remoteId!!.length > 0

    override fun equals(obj: Any?): Boolean {
        return torrentId == if (obj != null) (obj as Torrent).torrentId else null
    }

    val torrentId: String
        get() {
            if (magnetUri == null) {
                return remoteIdOrHash
            }
            val matcher = magnetPattern.matcher(magnetUri)
            return if (matcher.find()) {
                matcher.group(2).lowercase(Locale.getDefault())
            } else {
                remoteIdOrHash
            }
        }
    val torrentNameFromUri: String?
        get() {
            if (magnetUri == null) {
                return null
            }
            val matcher = magnetNamePattern.matcher(magnetUri)
            return if (matcher.find()) {
                matcher.group(1).lowercase(Locale.getDefault())
            } else {
                null
            }
        }
    private val remoteIdOrHash: String
        private get() = Objects.requireNonNullElseGet(remoteId) { this.hashCode().toString() }

    override fun compareTo(o: Torrent): Int {
        return if (lsize < o.lsize) -1 else 1
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