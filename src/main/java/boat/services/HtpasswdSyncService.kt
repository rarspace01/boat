package boat.services

import boat.repositories.UserRepository
import boat.utilities.LocalFiles
import boat.utilities.LoggerDelegate
import boat.utilities.PropertiesHelper
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
            
            // /tmp is shared with the other users of the machine, a link or a file of one of them is not written
            val file = File(PropertiesHelper.getProperty("HTPASSWD_FILE", HTPASSWD_FILE_PATH)!!)
            if (LocalFiles.writeIfChanged(file.toPath(), htpasswdContent)) logger.info("Synced {} users to {}", users.size, file)
        } catch (e: Exception) {
            logger.error("Failed to sync users to htpasswd file", e)
        }
    }
}
