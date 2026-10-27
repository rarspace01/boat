package boat.services

import boat.model.User
import boat.repositories.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class HtpasswdSyncServiceTest {

    private val userRepository: UserRepository = mockk()

    @TempDir
    lateinit var dir: File

    private val htpasswdFile by lazy { File(dir, "tmp/htpasswd") }
    private val htpasswdSyncService by lazy { HtpasswdSyncService(userRepository, htpasswdFile) }

    @Test
    fun `should sync users with bcrypt passwords to htpasswd file`() {
        every { userRepository.findAll() } returns listOf(
            User(id = "1", username = "testuser", password = "{bcrypt}\$2a\$10\$abcdefghijklmnopqrstuv"),
            User(id = "2", username = "otheruser", password = "plaintextpassword"), // Should be ignored
            User(id = "3", username = "admin", password = "{bcrypt}\$2y\$10\$zyxwvutsrqponmlkjihgfe")
        )

        htpasswdSyncService.syncUsersToHtpasswd()

        assertTrue(htpasswdFile.exists())
        val content = htpasswdFile.readText()
        val lines = content.trim().split("\n")
        
        assertEquals(2, lines.size)
        assertEquals("testuser:\$2a\$10\$abcdefghijklmnopqrstuv", lines[0])
        assertEquals("admin:\$2y\$10\$zyxwvutsrqponmlkjihgfe", lines[1])
    }

    @Test
    fun `should not write the file again if the users did not change`() {
        every { userRepository.findAll() } returns listOf(User(id = "1", username = "testuser", password = "{bcrypt}\$2a\$10\$abcdefghijklmnopqrstuv"))
        htpasswdSyncService.syncUsersToHtpasswd()
        htpasswdFile.setLastModified(1_000_000)

        htpasswdSyncService.syncUsersToHtpasswd()

        assertEquals(1_000_000, htpasswdFile.lastModified())
    }

    @Test
    fun `should default to a path in the home of the user`() {
        assertEquals(System.getProperty("user.home") + "/tmp/htpasswd", HtpasswdSyncService.defaultHtpasswdPath())
    }
}
