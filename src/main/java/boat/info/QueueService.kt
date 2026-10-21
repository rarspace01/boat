package boat.info

import boat.utilities.LoggerDelegate
import boat.utilities.PropertiesHelper
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.File
import java.util.Properties
import java.util.function.Consumer

@Service
class QueueService internal constructor(private val queueFile: File) {

    @Autowired
    constructor() : this(File(PropertiesHelper.getProperty("QUEUE_FILE", DEFAULT_QUEUE_FILE)!!))

    private val properties: Properties = initQueue()

    private fun initQueue(): Properties {
        val propertiesInit = Properties()
        if (queueFile.isFile) {
            runCatching { queueFile.inputStream().use { propertiesInit.load(it) } }
                .onFailure { logger.error("could not read the queue [{}]: {}", queueFile, it.message) }
        }
        return propertiesInit
    }

    fun addAll(listOfMediaItems: List<MediaItem>) {
        listOfMediaItems.forEach(Consumer { t -> add(t) })
        saveQueue()
    }

    fun add(mediaItem: MediaItem) {
        properties.setProperty(keyOf(mediaItem), serialize(mediaItem))
    }

    fun addAndSave(mediaItem: MediaItem) {
        add(mediaItem)
        saveQueue()
    }

    fun remove(mediaItem: MediaItem) {
        properties.remove(keyOf(mediaItem))
        // entries of older queue files are keyed by the title only
        if (properties.getProperty(mediaItem.title)?.startsWith("{") == false) properties.remove(mediaItem.title)
    }

    /** Title and year, so the same title with different years doesn't collide. */
    private fun keyOf(mediaItem: MediaItem): String = mediaItem.title + (mediaItem.year?.let { " ($it)" } ?: "")

    fun getQueue(): List<MediaItem> {
        return properties
            .entries
            .map { mutableEntry -> deserialize(mutableEntry.key.toString(), mutableEntry.value.toString()) }
    }

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

    fun saveQueue() {
        try {
            queueFile.parentFile?.mkdirs()
            queueFile.outputStream().use { properties.store(it, null) }
        } catch (exception: Exception) {
            logger.error("could not save the queue [{}]: {}", queueFile, exception.message)
        }
    }

    companion object {
        private const val DEFAULT_QUEUE_FILE = "/tmp/boat/queue.list"
        private val logger by LoggerDelegate()
    }
}
