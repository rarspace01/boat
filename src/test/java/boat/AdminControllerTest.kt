package boat

import boat.info.MediaProxyService
import boat.model.User
import boat.repositories.UserRepository
import boat.security.CachedAuthenticationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.password.PasswordEncoder

internal class AdminControllerTest {

    private val userRepository: UserRepository = mockk(relaxed = true)
    private val passwordEncoder: PasswordEncoder = mockk()
    private val provider: CachedAuthenticationProvider = mockk(relaxed = true)
    private val mediaProxyService: MediaProxyService = mockk()
    private val controller = AdminController(userRepository, passwordEncoder, provider, mediaProxyService)

    init {
        every { passwordEncoder.encode(any()) } answers { "{bcrypt}hash-of-" + firstArg<String>() }
    }

    private fun location(response: org.springframework.http.ResponseEntity<Void>): String {
        assertThat(response.statusCode).isEqualTo(HttpStatus.SEE_OTHER)
        return response.headers.getFirst(HttpHeaders.LOCATION)!!
    }

    @Test
    fun shouldAddAUserWithAnEncodedPasswordAndTheUserRole() {
        every { userRepository.findByUsername("anna") } returns null
        val saved = slot<User>()
        every { userRepository.save(capture(saved)) } answers { firstArg() }

        val response = controller.addUser(" anna ", "longenough")

        assertThat(saved.captured.username).isEqualTo("anna")
        assertThat(saved.captured.password).isEqualTo("{bcrypt}hash-of-longenough")
        assertThat(saved.captured.roles).containsExactly("USER")
        assertThat(location(response)).startsWith("../admin?message=added+user")
    }

    @Test
    fun shouldNotAddAnExistingUser() {
        every { userRepository.findByUsername("anna") } returns User(username = "anna", password = "x")

        val response = controller.addUser("anna", "longenough")

        assertThat(location(response)).contains("already+exists")
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun shouldRejectUsernamesWhichBreakTheHtpasswdFileAndShortPasswords() {
        assertThat(location(controller.addUser("a:b", "longenough"))).contains("invalid+username")
        assertThat(location(controller.addUser("", "longenough"))).contains("invalid+username")
        assertThat(location(controller.addUser("anna", "short"))).contains("at+least")

        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun shouldResetThePasswordAndForgetTheCachedLogins() {
        every { userRepository.findByUsername("anna") } returns User(username = "anna", password = "old")
        val saved = slot<User>()
        every { userRepository.save(capture(saved)) } answers { firstArg() }

        val response = controller.resetPassword("anna", "newpassword")

        assertThat(saved.captured.password).isEqualTo("{bcrypt}hash-of-newpassword")
        assertThat(location(response)).contains("reset+the+password")
        verify { provider.clearAuthenticationCache() }
    }

    @Test
    fun shouldNotResetThePasswordOfAnUnknownUser() {
        every { userRepository.findByUsername("nobody") } returns null

        assertThat(location(controller.resetPassword("nobody", "newpassword"))).contains("not+found")
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun shouldClearTheMediaCache() {
        every { mediaProxyService.clearCache() } returns 3

        assertThat(location(controller.clearMediaCache())).contains("removed+3+entries")
    }

    @Test
    fun shouldEscapeUsernamesOnThePage() {
        every { userRepository.findAll() } returns listOf(User(username = "<b>x</b>", password = "p"))

        val html = controller.admin(null)

        assertThat(html).contains("&lt;b&gt;x&lt;/b&gt;").doesNotContain("<b>x</b>")
    }

    @Test
    fun shouldRecognizeAdmins() {
        fun auth(role: String) = UsernamePasswordAuthenticationToken("u", "p", listOf(SimpleGrantedAuthority(role)))

        assertThat(AdminController.isAdmin(auth("ROLE_ADMIN"))).isTrue
        assertThat(AdminController.isAdmin(auth("ROLE_USER"))).isFalse
        assertThat(AdminController.isAdmin(null)).isFalse
    }
}
