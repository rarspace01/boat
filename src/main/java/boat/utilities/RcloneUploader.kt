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
        val command = listOf("rclone", "--no-check-certificate", "copyurl", url, destinationPath.replace("'", ""))
        log.info(command.joinToString(" "))
        val exitCode = try {
            val process = ProcessBuilder(command)
                .directory(File(System.getProperty("user.home")))
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().useLines { lines -> lines.forEach { log.info("rclone: {}", it) } }
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
    }
}
