package boat.info

import boat.utilities.LoggerDelegate
import boat.utilities.PropertiesHelper
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties

/**
 * The queue, first in, first out: the entries are kept and saved in the order they were added. Its file is in the
 * format of [Properties] (title=json), older files are read too.
 */
@Service
class QueueService internal constructor(private val queueFile: File) {

    @Autowired
    constructor() : this(File(PropertiesHelper.getProperty("QUEUE_FILE", DEFAULT_QUEUE_FILE)!!))

    /** Guarded by `this`: the web requests add, the queue job reads and removes. */
    private val entries: LinkedHashMap<String, String> = initQueue()

    private fun initQueue(): LinkedHashMap<String, String> {
        val ordered = LinkedHashMap<String, String>()
        if (queueFile.isFile) {
            // Properties is a hash table, the entries are taken as they are read to keep their order
            val reader = object : Properties() {
                override fun put(key: Any, value: Any): Any? = ordered.put(key.toString(), value.toString())
            }
            runCatching { queueFile.inputStream().use { reader.load(it) } }
                .onFailure { logger.error("could not read the queue [{}]: {}", queueFile, it.message) }
        }
        return ordered
    }

    @Synchronized
    fun addAll(listOfMediaItems: List<MediaItem>) {
        listOfMediaItems.forEach { add(it) }
        saveQueue()
    }

    @Synchronized
    fun add(mediaItem: MediaItem) {
        entries[keyOf(mediaItem)] = serialize(mediaItem)
    }

    @Synchronized
    fun addAndSave(mediaItem: MediaItem) {
        add(mediaItem)
        saveQueue()
    }

    @Synchronized
    fun remove(mediaItem: MediaItem) {
        entries.remove(keyOf(mediaItem))
        // entries of older queue files are keyed by the title only
        if (entries[mediaItem.title]?.startsWith("{") == false) entries.remove(mediaItem.title)
    }

    /** Title and year, so the same title with different years doesn't collide. */
    private fun keyOf(mediaItem: MediaItem): String = mediaItem.title + (mediaItem.year?.let { " ($it)" } ?: "")

    /** A copy, oldest first. */
    @Synchronized
    fun getQueue(): List<MediaItem> = entries.map { (key, value) -> deserialize(key, value) }

    /** Entries are stored as JSON with the matched media data; older queue files only hold the year. */
    private fun serialize(mediaItem: MediaItem): String = JsonObject().apply {
        addProperty("title", mediaItem.title)
        mediaItem.year?.let { addProperty("year", it) }
        mediaItem.type?.let { addProperty("type", it.name) }
        mediaItem.id?.let { addProperty("id", it) }
        mediaItem.originalTitle?.let { addProperty("originalTitle", it) }
    }.toString()

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

    @Synchronized
    fun saveQueue() {
        try {
            queueFile.parentFile?.mkdirs()
            queueFile.writeText(entries.entries.joinToString("") { (key, value) -> propertiesLine(key, value) }, Charsets.ISO_8859_1)
        } catch (exception: Exception) {
            logger.error("could not save the queue [{}]: {}", queueFile, exception.message)
        }
    }

    companion object {
        /** One entry as [Properties.store] writes it (escaped, without its date comment), [Properties] itself writes in hash order. */
        private fun propertiesLine(key: String, value: String): String {
            // the stream variant escapes what is not Latin-1 (\uXXXX), as the file is read
            val bytes = ByteArrayOutputStream()
            Properties().apply { setProperty(key, value) }.store(bytes, null)
            return bytes.toString(Charsets.ISO_8859_1).lineSequence().filter { it.isNotEmpty() && !it.startsWith("#") }.joinToString("") { "$it\n" }
        }

        private const val DEFAULT_QUEUE_FILE = "/tmp/boat/queue.list"
        private val logger by LoggerDelegate()
    }
}
