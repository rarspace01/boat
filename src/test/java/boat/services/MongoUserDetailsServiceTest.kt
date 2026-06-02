package boat.services

import boat.model.User
import boat.repositories.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.core.userdetails.UsernameNotFoundException

class MongoUserDetailsServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val mongoUserDetailsService = MongoUserDetailsService(userRepository)

    @Test
    fun `should load user by username`() {
        // given
        val username = "testuser"
        val password = "password"
        val user = User(username = username, password = password, roles = listOf("USER", "ADMIN"))
        every { userRepository.findByUsername(username) } returns user

        // when
        val userDetails = mongoUserDetailsService.loadUserByUsername(username)

        // then
        assertEquals(username, userDetails.username)
        assertEquals(password, userDetails.password)
        assertEquals(2, userDetails.authorities.size)
    }

    @Test
    fun `should throw exception if user not found`() {
        // given
        val username = "nonexistent"
        every { userRepository.findByUsername(username) } returns null

        // when / then
        assertThrows(UsernameNotFoundException::class.java) {
            mongoUserDetailsService.loadUserByUsername(username)
        }
    }
}
