package boat.model

import boat.torrent.TorrentHelper.formatDuration
import org.springframework.data.annotation.Id
import java.time.Duration
import java.time.Instant
import java.util.UUID


data class Transfer(
    @Id
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var remoteId: String? = null,
    var source: String = "☕",
    var uri: String = "",
    var transferStatus: TransferStatus = TransferStatus.NONE,
    var transferType: TransferType = TransferType.TORRENT,
    var progressInPercentage: Double = 0.0,
    var sizeInBytes: Long = 0,
    var feedbackMessage: String? = null,
    var eta: Duration = Duration.ZERO,
    var updated: Instant = Instant.now(),
) {
    override fun toString(): String {
        return String.format(
            "\n<br/>[<!-- ID:[%s]RID:[%s] -->%s,\uD83C\uDFE0%s,%s,%s,%s<!-- MSG: %s -->%s<!-- ,%s -->]", id, remoteId, name,
            source[0], transferStatus.string, type, percentageString, feedbackMessage,
            printDuration(),
            updated
        )
    }

    private val type: String
        get() = transferType.string
    private val percentageString: String
        get() = String.format("%.2f%%", progressInPercentage * 100.0)

    private fun printDuration(): String {
        return if (eta == Duration.ZERO) {
            ""
        } else {
            ", " + formatDuration(eta)
        }
    }
}