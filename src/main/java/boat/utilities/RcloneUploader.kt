package boat.utilities

import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException

/**
 * Copies a remote file into the rclone remote. The arguments are passed to rclone directly instead of through a
 * shell, so urls and paths can't break out of the command.
 */
@Component
class RcloneUploader {
    fun copyUrl(url: String, destinationPath: String): Boolean {
        log.info("D>[{}]", destinationPath)
        val command = listOf("rclone") + certificateArguments() + listOf("--retries", "1", "copyurl", url, destinationPath.replace("'", ""))
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

    companion object {
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
