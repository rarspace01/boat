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
import org.springframework.security.web.authentication.WebAuthenticationDetails
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

internal class CachedAuthenticationProviderTest {

    private val userRepository: UserRepository = mockk()
    private val passwordEncoder: PasswordEncoder = mockk()
    private val loginTime = Instant.parse("2026-01-02T03:04:05Z")
    private val provider = CachedAuthenticationProvider(userRepository, passwordEncoder).apply {
        clock = Clock.fixed(loginTime, ZoneOffset.UTC)
    }

    init {
        every { userRepository.updateLastLoginByUsername(any(), any()) } returns 1L
    }

    private fun login(password: String, username: String = "denis", address: String = "203.0.113.7") =
        provider.authenticate(UsernamePasswordAuthenticationToken(username, password).apply {
            details = WebAuthenticationDetails(address, null)
        })

    @Test
    fun shouldNotAskTheDatabaseAgainForRepeatedLogins() {
        every { userRepository.findByUsername("denis") } returns User(username = "denis", password = "hash1")
        every { passwordEncoder.matches("secret", "hash1") } returns true

        login("secret")
        assertThat(login("secret").isAuthenticated).isTrue

        verify(exactly = 1) { userRepository.findByUsername("denis") }
        verify(exactly = 1) { passwordEncoder.matches(any(), any()) }
    }

    @Test
    fun shouldNotAcceptAnotherPasswordOfACachedUser() {
        every { userRepository.findByUsername("denis") } returns User(username = "denis", password = "hash1")
        every { passwordEncoder.matches("secret", "hash1") } returns true
        every { passwordEncoder.matches("wrong", "hash1") } returns false
        login("secret")

        assertThatThrownBy { login("wrong") }.isInstanceOf(BadCredentialsException::class.java)
    }

    @Test
    fun shouldDenyLoginIfThereAreNoUsers() {
        every { userRepository.findByUsername("denis") } returns null

        assertThatThrownBy { login("secret") }.isInstanceOf(BadCredentialsException::class.java)
    }

    @Test
    fun shouldNotRunBcryptForAnUnknownUser() {
        // a missing user waits like a BCrypt check instead of running one, no CPU load for a user who doesn't exist
        every { userRepository.findByUsername("nobody") } returns null

        assertThatThrownBy { login("secret", username = "nobody") }.isInstanceOf(BadCredentialsException::class.java)

        verify(exactly = 0) { passwordEncoder.matches(any(), any()) }
        verify(exactly = 0) { passwordEncoder.encode(any()) }
    }

    @Test
    fun shouldStopCheckingPasswordsAfterTooManyFailedLoginsFromAnAddress() {
        every { userRepository.findByUsername("denis") } returns User(username = "denis", password = "hash1")
        every { passwordEncoder.matches("secret", "hash1") } returns true
        every { passwordEncoder.matches("wrong", "hash1") } returns false

        repeat(CachedAuthenticationProvider.MAX_FAILED_LOGINS) {
            assertThatThrownBy { login("wrong") }.isInstanceOf(BadCredentialsException::class.java)
        }
        // even the right password isn't checked any more from there
        assertThatThrownBy { login("secret") }.isInstanceOf(BadCredentialsException::class.java)
        verify(exactly = CachedAuthenticationProvider.MAX_FAILED_LOGINS) { passwordEncoder.matches(any(), any()) }

        // other addresses are not affected
        assertThat(login("secret", address = "198.51.100.1").isAuthenticated).isTrue
    }

    @Test
    fun shouldKeepCachedLoginsWorkingWhileAnAddressIsBlocked() {
        every { userRepository.findByUsername("denis") } returns User(username = "denis", password = "hash1")
        every { passwordEncoder.matches("secret", "hash1") } returns true
        every { passwordEncoder.matches("wrong", "hash1") } returns false
        login("secret")

        repeat(CachedAuthenticationProvider.MAX_FAILED_LOGINS) {
            assertThatThrownBy { login("wrong") }.isInstanceOf(BadCredentialsException::class.java)
        }

        // e.g. the user behind the same reverse proxy as someone guessing
        assertThat(login("secret").isAuthenticated).isTrue
    }

    @Test
    fun shouldRecordTheLastLoginOnceForRepeatedLogins() {
        every { userRepository.findByUsername("denis") } returns User(username = "denis", password = "hash1")
        every { passwordEncoder.matches("secret", "hash1") } returns true

        login("secret")
        login("secret")

        // the cache hit doesn't write to the database
        verify(exactly = 1) { userRepository.updateLastLoginByUsername("denis", loginTime) }
    }

    @Test
    fun shouldNotRecordAFailedLogin() {
        every { userRepository.findByUsername("denis") } returns User(username = "denis", password = "hash1")
        every { passwordEncoder.matches("wrong", "hash1") } returns false

        assertThatThrownBy { login("wrong") }.isInstanceOf(BadCredentialsException::class.java)

        verify(exactly = 0) { userRepository.updateLastLoginByUsername(any(), any()) }
    }

    @Test
    fun shouldLoginEvenIfTheLastLoginCantBeRecorded() {
        every { userRepository.findByUsername("denis") } returns User(username = "denis", password = "hash1")
        every { passwordEncoder.matches("secret", "hash1") } returns true
        every { userRepository.updateLastLoginByUsername(any(), any()) } throws RuntimeException("database down")

        assertThat(login("secret").isAuthenticated).isTrue
    }

    @Test
    fun shouldGrantTheRolesOfTheUserAlsoFromTheCache() {
        every { userRepository.findByUsername("denis") } returns
            User(username = "denis", password = "hash1", roles = listOf("USER", "ADMIN"))
        every { passwordEncoder.matches("secret", "hash1") } returns true

        val fromDatabase = login("secret")
        val fromCache = login("secret")

        assertThat(fromDatabase.authorities.map { it.authority }).containsExactly("ROLE_USER", "ROLE_ADMIN")
        assertThat(fromCache.authorities.map { it.authority }).containsExactly("ROLE_USER", "ROLE_ADMIN")
    }

    @Test
    fun shouldCheckThePasswordAgainAfterTheCacheWasCleared() {
        every { userRepository.findByUsername("denis") } returns User(username = "denis", password = "hash1")
        every { passwordEncoder.matches("secret", "hash1") } returns true
        login("secret")

        provider.clearAuthenticationCache()
        login("secret")

        verify(exactly = 2) { passwordEncoder.matches(any(), any()) }
    }
}
