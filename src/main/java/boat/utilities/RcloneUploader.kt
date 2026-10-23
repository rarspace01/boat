package boat.utilities

import com.google.gson.JsonParser
import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Copies a remote file into the rclone remote. The arguments are passed to rclone directly instead of through a
 * shell, so urls and paths can't break out of the command.
 */
@Component
class RcloneUploader {
    /**
     * rclone replaces a file which is already there. If the file is known to have [expectedSize] bytes and the target
     * already has that size, it is complete, so it isn't downloaded again. A file of another size (e.g. a partial
     * one) is replaced.
     */
    fun copyUrl(url: String, destinationPath: String, expectedSize: Long? = null): Boolean {
        if (expectedSize != null && expectedSize > 0 && existingFileSize(destinationPath) == expectedSize) {
            log.info("skipped, [{}] already exists with {} bytes", destinationPath, expectedSize)
            return true
        }
        log.info("D>[{}]", destinationPath)
        val command = listOf("rclone") + certificateArguments() + listOf("--retries", "1", "copyurl", url, destinationPath)
        log.info(command.joinToString(" "))
        val exitCode = try {
            val process = ProcessBuilder(command)
                .directory(File(System.getProperty("user.home")))
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line -> if (isErrorLine(line)) log.error("rclone: {}", line) else log.info("rclone: {}", line) }
            }
            process.waitFor()
        } catch (e: IOException) {
            log.error("upload failed: {} - {}", destinationPath, e.message)
            return false
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            log.error("upload failed: {} - {}", destinationPath, e.message)
            return false
        }
        return if (exitCode != 0) {
            log.error("upload failed with exit code {}: {}", exitCode, destinationPath)
            false
        } else {
            log.info("DF>[{}]", destinationPath)
            true
        }
    }

    /** The size of the file at [path], null if there is no file (or rclone can't tell). */
    internal fun existingFileSize(path: String): Long? {
        val command = listOf("rclone") + certificateArguments() + listOf("lsjson", "--stat", path)
        return try {
            val process = ProcessBuilder(command)
                .directory(File(System.getProperty("user.home")))
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(STAT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0) return null
            val json = JsonParser.parseString(process.inputStream.bufferedReader().readText()).asJsonObject
            if (json["IsDir"]?.asBoolean == true) null else json["Size"]?.asLong?.takeIf { it >= 0 }
        } catch (e: Exception) {
            log.warn("could not check if [{}] exists: {}", path, e.message)
            null
        }
    }

    companion object {
        private const val STAT_TIMEOUT_SECONDS = 30L
        private val log by LoggerDelegate()
        // rclone log lines look like "2026/10/21 07:08:55 ERROR : Attempt 1/3 failed ..."
        private val errorLinePattern = Regex("^\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)? (?:ERROR|CRITICAL)\\s*:")

        /**
         * rclone verifies certificates unless `RCLONE_NO_CHECK_CERTIFICATE=true` is set, e.g. for a webdav remote
         * with a self-signed certificate.
         */
        fun certificateArguments(): List<String> =
            if (PropertiesHelper.getProperty("RCLONE_NO_CHECK_CERTIFICATE") == "true") listOf("--no-check-certificate") else emptyList()

        internal fun isErrorLine(line: String): Boolean = errorLinePattern.containsMatchIn(line)
    }
}
