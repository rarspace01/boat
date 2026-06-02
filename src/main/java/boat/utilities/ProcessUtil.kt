package boat.utilities

import java.io.File

object ProcessUtil {
    val isRcloneInstalled: Boolean
        get() = runCatching {
            ProcessBuilder("bash", "-c", "rclone version")
                .directory(File(System.getProperty("user.home")))
                .start()
                .inputStream.bufferedReader().use { it.readText() }
                .contains("rclone v")
        }.getOrDefault(false)
}
