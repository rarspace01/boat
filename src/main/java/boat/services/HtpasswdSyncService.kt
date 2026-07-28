package boat.services

import boat.repositories.UserRepository
import boat.utilities.LoggerDelegate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.File

@Service
class HtpasswdSyncService(private val userRepository: UserRepository) {
    
    companion object {
        private val logger by LoggerDelegate()
        const val HTPASSWD_FILE_PATH = "/tmp/htpasswd"
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
            
            val file = File(HTPASSWD_FILE_PATH)
            file.writeText(htpasswdContent)
            logger.debug("Successfully synced {} users to {}", users.size, HTPASSWD_FILE_PATH)
        } catch (e: Exception) {
            logger.error("Failed to sync users to htpasswd file", e)
        }
    }
}
