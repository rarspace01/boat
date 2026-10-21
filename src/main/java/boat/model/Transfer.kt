package boat.model

import org.springframework.web.util.HtmlUtils
import boat.info.MediaType
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
    var remoteMediaId: String? = null,
    var mediaType: MediaType? = null,
    var source: String = "☕",
    var uri: String = "",
    var transferStatus: TransferStatus = TransferStatus.NONE,
    var transferType: TransferType = TransferType.TORRENT,
    var progressInPercentage: Double = 0.0,
    var sizeInBytes: Long = 0,
    var feedbackMessage: String? = null,
    var eta: Duration = Duration.ZERO,
    var failedUploadsToDrive: Int = 0,
    var updated: Instant = Instant.now(),
) {
    override fun toString(): String {
        return String.format(
            "\n<br/>[<!-- ID:[%s]RID:[%s]MID:[%s]MT:[%s] -->%s,\uD83C\uDFE0%s,%s,%s,%s<!-- MSG: %s -->%s<!-- ,%s -->]", esc(id), esc(remoteId), esc(remoteMediaId), mediaType, esc(name),
            esc(source.take(1)), transferStatus.string, type, percentageString, esc(feedbackMessage),
            printDuration(),
            updated
        )
    }

    // the transfer is written into html (debug page), names come from search engines and hosters
    private fun esc(value: String?): String? = value?.let { HtmlUtils.htmlEscape(it) }

    /**
     * The name as reported by the multifilehoster, without the media title prefix ("<media title> | <hoster name>").
     * Functions instead of properties, so they don't get persisted.
     */
    fun hosterName(): String {
        return if (hasMediaTitlePrefix()) name.substringAfter(MEDIA_TITLE_SEPARATOR) else name
    }

    fun updateHosterName(hosterName: String) {
        name = if (hasMediaTitlePrefix()) name.substringBefore(MEDIA_TITLE_SEPARATOR) + MEDIA_TITLE_SEPARATOR + hosterName else hosterName
    }

    private fun hasMediaTitlePrefix(): Boolean {
        return remoteMediaId != null && name.contains(MEDIA_TITLE_SEPARATOR)
    }

    companion object {
        const val MEDIA_TITLE_SEPARATOR = " | "

        fun buildName(mediaTitle: String?, hosterName: String): String {
            return if (mediaTitle.isNullOrBlank()) hosterName else mediaTitle + MEDIA_TITLE_SEPARATOR + hosterName
        }
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