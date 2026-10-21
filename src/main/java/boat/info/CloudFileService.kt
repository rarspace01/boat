package boat.info

import boat.utilities.LoggerDelegate
import boat.utilities.PropertiesHelper
import boat.utilities.RcloneUploader
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@Service
class CloudFileService {
    var isCacheFilled = false

    companion object {
        private val logger by LoggerDelegate()

        /** The arguments are passed to rclone directly instead of through a shell, so paths can't break out of the command. */
        internal fun buildLsjsonCommand(destinationPath: String): List<String> =
            listOf("rclone") + RcloneUploader.certificateArguments() + "lsjson" +
                    (if (destinationPath.contains("/transfer/")) emptyList() else listOf("--recursive")) +
                    destinationPath
    }

    @Cacheable("getFreeSpaceInMegaBytes")
    fun getFreeSpaceInMegaBytes():Double {
        val basePath = PropertiesHelper.getProperty(CloudService.RCLONE_DIR)
        // no shell, so the path can't break out of the command
        val command = listOf("rclone") + RcloneUploader.certificateArguments() + listOf("about", basePath.toString(), "--json")
        logger.info(command.joinToString(" "))
        val builder = ProcessBuilder()
        builder.command(command)
        builder.directory(File(System.getProperty("user.home")))
        var output = ""
        var error = ""
        try {
            val process = builder.start()
            process.waitFor(5, TimeUnit.SECONDS)
            output = String(process.inputStream.readAllBytes())
            error = String(process.errorStream.readAllBytes())
            if (error.contains("limit") && output.isEmpty()) {
                Thread.sleep(2000)
                return -1.0
            }
            if (error.contains("directory not found")) {
                return -1.0
            }
            val jsonElement = JsonParser.parseString(output)
            return if (jsonElement.isJsonObject) {
                return jsonElement.asJsonObject["free"].asLong.toDouble()/1024/1024
            } else -1.0
        } catch (e: Exception) {
            logger.error("{}\nPath: [$basePath]\nOutput from process:\n{}\nError from Process:\n{}", e.message, output, error, e)
            return -1.0
        }
    }

    @Cacheable("filesCache", unless = "#result.isEmpty()")
    fun getFilesInPath(destinationPath: String): List<String> {
        return getFilesInPathWithRetries(destinationPath, 3)
    }

    fun getFilesInPathWithRetries(destinationPath: String, retriesLeft: Int): List<String> {
        val fileList: MutableList<String> = ArrayList()
        val startCounter = System.currentTimeMillis()
        logger.info("Search in [$destinationPath]")
        val builder = ProcessBuilder()
        val command = buildLsjsonCommand(destinationPath)
        logger.info(command.joinToString(" "))
        builder.command(command)
        builder.directory(File(System.getProperty("user.home")))
        var output = ""
        var error = ""
        try {
            val process = builder.start()
            process.waitFor(5, TimeUnit.SECONDS)
            output = String(process.inputStream.readAllBytes())
            error = String(process.errorStream.readAllBytes())
            if (error.contains("limit") && output.isEmpty() && retriesLeft > 0) {
                Thread.sleep(2000)
                return getFilesInPathWithRetries(destinationPath, retriesLeft - 1)
            }
            if (error.contains("directory not found")) {
                return emptyList()
            }
            val jsonElement = JsonParser.parseString(output)
            if (jsonElement.isJsonArray) {
                jsonElement.asJsonArray
                    .forEach(Consumer { jsonElement1: JsonElement -> fileList.add(destinationPath + jsonElement1.asJsonObject["Path"].asString) })
            } else if (jsonElement.isJsonObject) {
                fileList.add(destinationPath + jsonElement.asJsonObject["Path"].asString)
            }
        } catch (e: Exception) {
            logger.error("{}\nPath: [$destinationPath]\nOutput from process:\n{}\nError from Process:\n{}", e.message, output, error, e)
        }
        logger.info("Took {}ms with [$destinationPath] for ${fileList.size} files", System.currentTimeMillis() - startCounter)
        return fileList
    }
}
