package boat.security

import boat.model.User
import boat.repositories.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder

internal class CachedAuthenticationProviderTest {

    private val userRepository: UserRepository = mockk()
    private val passwordEncoder: PasswordEncoder = mockk()
    private val provider = CachedAuthenticationProvider(userRepository, passwordEncoder)

    private fun login(password: String) = provider.authenticate(UsernamePasswordAuthenticationToken("denis", password))

    @Test
    fun shouldSkipTheExpensiveCheckForRepeatedLogins() {
        every { userRepository.findByUsername("denis") } returns User(username = "denis", password = "hash1")
        every { passwordEncoder.matches("secret", "hash1") } returns true

        login("secret")
        assertThat(login("secret").isAuthenticated).isTrue

        verify(exactly = 1) { passwordEncoder.matches(any(), any()) }
    }

    @Test
    fun shouldRejectCachedLoginAfterThePasswordChanged() {
        every { userRepository.findByUsername("denis") } returns User(username = "denis", password = "hash1")
        every { passwordEncoder.matches("secret", "hash1") } returns true
        login("secret")

        every { userRepository.findByUsername("denis") } returns User(username = "denis", password = "hash2")
        every { passwordEncoder.matches("secret", "hash2") } returns false

        assertThatThrownBy { login("secret") }.isInstanceOf(BadCredentialsException::class.java)
    }

    @Test
    fun shouldRejectCachedLoginOfRemovedUser() {
        every { userRepository.findByUsername("denis") } returns User(username = "denis", password = "hash1")
        every { passwordEncoder.matches("secret", "hash1") } returns true
        login("secret")

        every { userRepository.findByUsername("denis") } returns null

        assertThatThrownBy { login("secret") }.isInstanceOf(BadCredentialsException::class.java)
    }
}
