package boat.info

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.springframework.stereotype.Service
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import java.util.function.Consumer

@Service
class QueueService {

    private val properties: Properties = initQueue()

    private fun initQueue(): Properties {
        val propertiesInit = Properties()
        return try {
            val queueListFile: InputStream = FileInputStream("/tmp/boat/queue.list")
            propertiesInit.load(queueListFile)
            propertiesInit
        } catch (exception: FileNotFoundException) {
            propertiesInit
        }
    }

    fun addAll(listOfMediaItems: List<MediaItem>) {
        listOfMediaItems.forEach(Consumer { t -> add(t) })
        saveQueue()
    }

    fun add(mediaItem: MediaItem) {
        properties.setProperty(mediaItem.title, serialize(mediaItem))
    }

    fun addAndSave(mediaItem: MediaItem) {
        add(mediaItem)
        saveQueue()
    }

    fun remove(mediaItem: MediaItem) {
        properties.remove(mediaItem.title)
    }

    fun getQueue(): List<MediaItem> {
        return properties
            .entries
            .map { mutableEntry -> deserialize(mutableEntry.key.toString(), mutableEntry.value.toString()) }
    }

    /** Entries are stored as JSON with the matched media data; older queue files only hold the year. */
    private fun serialize(mediaItem: MediaItem): String = JsonObject().apply {
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
                title = title,
                originalTitle = json["originalTitle"]?.asString,
                year = json["year"]?.asInt,
                type = json["type"]?.asString?.let { name -> MediaType.entries.firstOrNull { it.name == name } },
                id = json["id"]?.asString
            )
        }.getOrElse { MediaItem(title = title) }
    }

    fun saveQueue() {
        try {
            val queueListFile: OutputStream = FileOutputStream("/tmp/boat/queue.list")
            properties.store(queueListFile, null)
        } catch (exception: Exception) {
        }
    }
}
