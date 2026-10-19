package boat

import boat.info.MediaProxyService
import boat.info.TheMovieDataBaseService
import boat.info.TheTVDBService
import boat.utilities.HttpHelper
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File

class ContentRenamerIntegrationTest {
    val homeDir: String? = System.getProperty("user.home")

    // archives and disc images plus the non-media files the renamer skips, except downloads in progress and the fonts
    // of styled subtitles
    private val deletableExtensions = setOf("rar", "7z", "zip", "mp3", "vob", "iso", "ifo", "bup") +
            (ContentRenamer.nonMediaExtensions - setOf("!qb", "part", "ttf", "otf"))

    @Disabled
    @Test
    fun manualFullRun() {
        renameAll("$homeDir/pfdb/PFDB/Series-Shows/")
    }

    @Disabled
    @Test
    fun manualFullRunMovies() {
        renameAll("$homeDir/pfdb/PFDB/Movies/")
    }

    @Disabled
    @Test
    fun deleteAll() {
        processFile(File("$homeDir/pfdb/PFDB/Series-Shows/"))
    }

    @Disabled
    @Test
    fun deleteAllMovies() {
        processFile(File("$homeDir/pfdb/PFDB/Movies/"))
    }

    private fun renameAll(path: String) {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )
        val logFile = File("extraction.log")
        if (logFile.exists()) logFile.delete()
        if (File(path).exists()) {
            contentRenamer.processFilesFromDirectory(path, logFile, basePath = "$homeDir/pfdb/PFDB")
        }
    }

    private fun processFile(file: File) {
        if (file.exists()) {
            if (file.isDirectory) {
                val listFiles = file.listFiles()
                if(listFiles.isEmpty()) {
                    println("RMDIR: ${file.absolutePath}")
                    file.delete()
                } else {
                    listFiles.forEach { processFile(it) }
                }
            } else {
                // unfinished downloads moved into a show of the library aren't downloading anymore
                val isStaleDownload = file.extension.lowercase() == "!qb" && ContentRenamer.remoteMediaIdPattern.containsMatchIn(file.absolutePath)
                if (file.extension.lowercase() in deletableExtensions || isStaleDownload) {
                    println("DEL: ${file.absolutePath}")
                    file.delete()
                }
            }
        }
    }

}