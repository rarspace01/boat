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
    private val magnetNamePattern = Pattern.compile("dn=(.*?)&")
    override fun toString(): String {
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
        }*/if (remoteStatusText.isNotEmpty() && remoteProgress != null) {
            var progress = "/" + remoteProgress
            if (remoteStatusText.contains("Uploading")) {
                progress = ""
            }
            stringBuilder.append(" ").append(remoteStatusText.replace("finished".toRegex(), "Waiting for Upload"))
                .append(progress)
        }
        if (eta != Duration.ZERO) {
            stringBuilder.append(" ETA:").append(eta)
        }
        if (!isNotARemoteTorrent(magnetUriBase64)) {
            stringBuilder.append(String.format("%s - %s - folder_id: %s file_id: %s", torrentId, remoteId, folder_id, file_id))
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
        return torrentId == if (other != null) (other as Torrent).torrentId else null
    }

    val torrentId: String
        get() {
            if (magnetUri.isEmpty()) {
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
            if (magnetUri.isEmpty()) {
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
        get() = Objects.requireNonNullElseGet(remoteId) { this.hashCode().toString() }

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