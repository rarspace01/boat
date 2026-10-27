package boat.info

import boat.model.QueueEntry
import boat.repositories.QueueRepository
import org.springframework.stereotype.Service

/** The queue, first in, first out, kept in MongoDB so it survives a restart. */
@Service
class QueueService(private val queueRepository: QueueRepository) {

    /** Guarded by `this`, keeps the order of entries added within the same millisecond. */
    private var lastSequence = 0L

    @Synchronized
    fun addAll(listOfMediaItems: List<MediaItem>) {
        listOfMediaItems.forEach { add(it) }
    }

    /** An item that is already queued keeps its place. */
    @Synchronized
    fun add(mediaItem: MediaItem) {
        if (queueRepository.existsById(QueueEntry.idOf(mediaItem))) return
        lastSequence = maxOf(lastSequence + 1, System.currentTimeMillis())
        queueRepository.save(QueueEntry.of(mediaItem, lastSequence))
    }

    @Synchronized
    fun remove(mediaItem: MediaItem) {
        queueRepository.deleteById(QueueEntry.idOf(mediaItem))
    }

    /** A copy, oldest first. */
    @Synchronized
    fun getQueue(): List<MediaItem> = queueRepository.findAllByOrderBySequenceAsc().map { it.toMediaItem() }
}
