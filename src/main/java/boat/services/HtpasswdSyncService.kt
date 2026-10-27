package boat.services

import boat.repositories.UserRepository
import boat.utilities.LoggerDelegate
import boat.utilities.PropertiesHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.File

@Service
class HtpasswdSyncService internal constructor(private val userRepository: UserRepository, private val htpasswdFile: File) {

    @Autowired
    constructor(userRepository: UserRepository) : this(userRepository, File(PropertiesHelper.getProperty("HTPASSWD_FILE", defaultHtpasswdPath())!!))

    companion object {
        private val logger by LoggerDelegate()

        /** In the home of the user boat runs as, not in /tmp: other users of the machine can't link or create files there. */
        fun defaultHtpasswdPath(): String = System.getProperty("user.home") + "/tmp/htpasswd"
        const val BCRYPT_PREFIX = "{bcrypt}"
    }

    @Scheduled(fixedDelay = 60000)
    fun syncUsersToHtpasswd() {
        try {
            val users = userRepository.findAll()
            val htpasswdContent = buildString {
                for (user in users) {
                    val password = user.password
                    if (password.startsWith(BCRYPT_PREFIX)) {
                        val strippedPassword = password.substring(BCRYPT_PREFIX.length)
                        append(user.username).append(":").append(strippedPassword).append("\n")
                    } else {
                        logger.warn("User {} has a non-bcrypt password, skipping for htpasswd sync", user.username)
                    }
                }
            }
            
            // only when it changed, so the proxy doesn't read a half written file every minute
            if (!htpasswdFile.isFile || htpasswdFile.readText() != htpasswdContent) {
                htpasswdFile.parentFile?.mkdirs()
                htpasswdFile.writeText(htpasswdContent)
                logger.info("Synced {} users to {}", users.size, htpasswdFile)
            }
        } catch (e: Exception) {
            logger.error("Failed to sync users to htpasswd file", e)
        }
    }
}
