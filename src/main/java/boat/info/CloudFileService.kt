package boat.info

import boat.utilities.LoggerDelegate
import boat.utilities.PropertiesHelper
import boat.utilities.RcloneUploader
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@Service
class CloudFileService(
    /** The rclone program, another one in tests. */
    private val rclone: String = "rclone",
    private val listingTimeoutSeconds: Long = LISTING_TIMEOUT_SECONDS,
) {
    var isCacheFilled = false

    private class RcloneResult(val output: String, val error: String)

    companion object {
        private val logger by LoggerDelegate()

        /** A recursive listing of a whole letter folder of the library takes a while on the remote. */
        const val LISTING_TIMEOUT_SECONDS = 300L
        private const val ABOUT_TIMEOUT_SECONDS = 60L

        /** Reading the output blocks until rclone ends, so not on the common pool, where it would hold threads others need. */
        private val outputReaders = Executors.newVirtualThreadPerTaskExecutor()

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
        var output = ""
        var error = ""
        try {
            val result = run(command, ABOUT_TIMEOUT_SECONDS) ?: return -1.0
            output = result.output
            error = result.error
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
        val command = buildLsjsonCommand(destinationPath)
        logger.info(command.joinToString(" "))
        var output = ""
        var error = ""
        try {
            val result = run(command, listingTimeoutSeconds) ?: return emptyList()
            output = result.output
            error = result.error
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

    /**
     * Runs rclone and reads its output and errors while it runs: a listing is several MB, with nobody reading, rclone
     * would wait on the full pipe. Null if rclone didn't finish within [timeoutSeconds].
     */
    private fun run(command: List<String>, timeoutSeconds: Long): RcloneResult? {
        val process = ProcessBuilder(listOf(rclone) + command.drop(1))
            .directory(File(System.getProperty("user.home")))
            .start()
        val output = CompletableFuture.supplyAsync({ process.inputStream.readAllBytes().toString(Charsets.UTF_8) }, outputReaders)
        val error = CompletableFuture.supplyAsync({ process.errorStream.readAllBytes().toString(Charsets.UTF_8) }, outputReaders)
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            // with its children, they would keep the output open
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
            logger.warn("rclone took more than {}s, stopped it: {}", timeoutSeconds, command.joinToString(" "))
            return null
        }
        return RcloneResult(output.get(5, TimeUnit.SECONDS), error.get(5, TimeUnit.SECONDS))
    }
}
