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

   @Disabled
   @Test
    fun manualFullRun() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )
        // get folders and files in path
        val path = "$homeDir/pfdb/PFDB/Series-Shows/"
        val logFile = File("extraction.log")
        if (logFile.exists()) logFile.delete()
        if (File(path).exists()) {
            contentRenamer.processFilesFromDirectory(path, logFile)
        }
    }

    @Disabled
    @Test
    fun deleteAll() {
        // get folders and files in path
        val path = "$homeDir/pfdb/PFDB/Series-Shows/"
        val file = File(path)
        processFile(file)
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
                val fileExt = file.name.replace(file.nameWithoutExtension, "")
                if (fileExt == ".rar" || fileExt == ".7z" || fileExt == ".zip" || fileExt == ".mp3") {
                    println("DEL: ${file.absolutePath}")
                    file.delete()
                }
            }
        }
    }

}