package boat.info

import boat.model.QueueEntry
import boat.repositories.QueueRepository
import boat.utilities.LocalFiles
import boat.utilities.LoggerDelegate
import boat.utilities.PropertiesHelper
import com.google.gson.JsonParser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.File
import java.util.Properties

/**
 * The queue, first in, first out, kept in MongoDB so it survives a restart. A queue file of an older version
 * (format of [Properties], title=json or title=year) is imported once, if the queue is empty, and renamed afterwards.
 */
@Service
class QueueService internal constructor(private val queueRepository: QueueRepository, private val legacyQueueFile: File) {

    @Autowired
    constructor(queueRepository: QueueRepository) : this(queueRepository, File(PropertiesHelper.getProperty("QUEUE_FILE", DEFAULT_QUEUE_FILE)!!))

    /** Guarded by `this`, keeps the order of entries added within the same millisecond. */
    private var lastSequence = 0L

    init {
        importLegacyQueueFile()
    }

    private fun importLegacyQueueFile() {
        if (!legacyQueueFile.isFile) return
        // the default is in /tmp, the queue must not come from another user of the machine
        if (!LocalFiles.isOwnFile(legacyQueueFile.toPath())) {
            logger.warn("not importing the queue file [{}], it is a link or a file of another user", legacyQueueFile)
            return
        }
        try {
            if (queueRepository.count() == 0L) {
                val ordered = LinkedHashMap<String, String>()
                // Properties is a hash table, the entries are taken as they are read to keep their order
                val reader = object : Properties() {
                    override fun put(key: Any, value: Any): Any? = ordered.put(key.toString(), value.toString())
                }
                legacyQueueFile.inputStream().use { reader.load(it) }
                addAll(ordered.map { (key, value) -> deserialize(key, value) })
                logger.info("imported {} entries of the queue file [{}]", ordered.size, legacyQueueFile)
            }
            val imported = File(legacyQueueFile.path + ".imported")
            if (!legacyQueueFile.renameTo(imported)) logger.warn("could not rename the queue file [{}]", legacyQueueFile)
        } catch (exception: Exception) {
            logger.error("could not import the queue file [{}]: {}", legacyQueueFile, exception.message)
        }
    }

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

    private fun deserialize(title: String, value: String): MediaItem {
        if (!value.startsWith("{")) return MediaItem(title = title, year = value.toIntOrNull())
        return runCatching {
            val json = JsonParser.parseString(value).asJsonObject
            MediaItem(
                title = json["title"]?.asString ?: title,
                originalTitle = json["originalTitle"]?.asString,
                year = json["year"]?.asInt,
                type = json["type"]?.asString?.let { name -> MediaType.entries.firstOrNull { it.name == name } },
                id = json["id"]?.asString
            )
        }.getOrElse { MediaItem(title = title) }
    }

    companion object {
        private const val DEFAULT_QUEUE_FILE = "/tmp/boat/queue.list"
        private val logger by LoggerDelegate()
    }
}
