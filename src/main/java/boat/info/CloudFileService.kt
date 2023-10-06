package boat.info

import boat.utilities.LoggerDelegate
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import lombok.Getter
import lombok.Setter
import lombok.extern.slf4j.Slf4j
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
    }

    @Cacheable("filesCache")
    fun getFilesInPath(destinationPath: String): List<String> {
        return getFilesInPathWithRetries(destinationPath, 3)
    }

    fun getFilesInPathWithRetries(destinationPath: String, retriesLeft: Int): List<String> {
        val fileList: MutableList<String> = ArrayList()
        val startCounter = System.currentTimeMillis()
        logger.debug("Search in [$destinationPath]")
        val builder = ProcessBuilder()
        val optionalIfNonTransfer = if(destinationPath.contains("/transfer/")) "" else "--recursive "
        val commandToRun = "rclone --no-check-certificate lsjson $optionalIfNonTransfer'$destinationPath'"
        logger.debug(commandToRun)
        builder.command("bash", "-c", commandToRun)
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
            logger.error("{}\nPath: [$destinationPath]\nOutput from process:\n{}\nError from Process:\n{}", e.message, output, error)
            e.printStackTrace()
        }
        logger.info("Took {}ms with [$destinationPath] for ${fileList.size} files", System.currentTimeMillis() - startCounter)
        return fileList
    }
}
