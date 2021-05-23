package boat.info

import org.springframework.stereotype.Service
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import java.util.function.Consumer

@Service
class QueueService {

    private val properties: Properties = initQueue()

    private fun initQueue(): Properties {
        val propertiesInit = Properties()
        return try {
            val queueListFile: InputStream = FileInputStream("queue.list")
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
        properties.setProperty(mediaItem.title, mediaItem.year.toString())
    }

    fun addAndSave(mediaItem: MediaItem) {
        properties.setProperty(mediaItem.title, mediaItem.year.toString())
        saveQueue()
    }

    fun remove(mediaItem: MediaItem) {
        properties.remove(mediaItem.title)
    }

    fun getQueue(): List<MediaItem> {
        return properties
            .entries
            .map { mutableEntry ->
                MediaItem(
                    title = mutableEntry.key.toString(),
                    year = mutableEntry.value.toString().toIntOrNull()
                )
            }
    }

    fun saveQueue() {
        try {
            val queueListFile: OutputStream = FileOutputStream("queue.list")
            properties.store(queueListFile, null)
        } catch (exception: Exception) {

        }
    }

}