package boat.model

import boat.info.MediaItem
import boat.info.MediaType
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

/** One entry of the queue. The id is the title and year, so the same title with different years doesn't collide. */
@Document("queue")
data class QueueEntry(
    @Id
    val id: String,
    val title: String,
    val originalTitle: String? = null,
    val year: Int? = null,
    val type: MediaType? = null,
    val mediaId: String? = null,
    /** Orders the queue, first in, first out. */
    val sequence: Long = 0,
) {
    fun toMediaItem() = MediaItem(title = title, originalTitle = originalTitle, year = year, type = type, id = mediaId)

    companion object {
        fun idOf(mediaItem: MediaItem): String = mediaItem.title + (mediaItem.year?.let { " ($it)" } ?: "")

        fun of(mediaItem: MediaItem, sequence: Long) = QueueEntry(
            id = idOf(mediaItem),
            title = mediaItem.title,
            originalTitle = mediaItem.originalTitle,
            year = mediaItem.year,
            type = mediaItem.type,
            mediaId = mediaItem.id,
            sequence = sequence,
        )
    }
}
