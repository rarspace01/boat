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
        every { mediaProxyService.cacheStatistics() } returns MediaProxyService.CacheStatistics(0, 100, 0, 0, null)
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

    private fun auth(name: String) = UsernamePasswordAuthenticationToken(name, "p", listOf(SimpleGrantedAuthority("ROLE_ADMIN")))

    @Test
    fun shouldDeleteAUserAndForgetTheCachedLogins() {
        val anna = User(username = "anna", password = "x")
        every { userRepository.findByUsername("anna") } returns anna

        val response = controller.deleteUser("anna", auth("root"))

        assertThat(location(response)).contains("deleted+user")
        verify { userRepository.delete(anna) }
        verify { provider.clearAuthenticationCache() }
    }

    @Test
    fun shouldNotLetAnAdminDeleteThemselves() {
        assertThat(location(controller.deleteUser("root", auth("root")))).contains("delete+yourself")
        verify(exactly = 0) { userRepository.delete(any()) }
    }

    @Test
    fun shouldNotDeleteTheLastAdmin() {
        val admin = User(username = "boss", password = "x", roles = listOf("USER", "ADMIN"))
        every { userRepository.findByUsername("boss") } returns admin
        every { userRepository.findAll() } returns listOf(admin, User(username = "anna", password = "x"))

        assertThat(location(controller.deleteUser("boss", auth("root")))).contains("last+admin")
        verify(exactly = 0) { userRepository.delete(any()) }
    }

    @Test
    fun shouldDeleteAnAdminIfThereIsAnother() {
        val admin = User(username = "boss", password = "x", roles = listOf("ADMIN"))
        every { userRepository.findByUsername("boss") } returns admin
        every { userRepository.findAll() } returns listOf(admin, User(username = "root", password = "x", roles = listOf("ADMIN")))

        assertThat(location(controller.deleteUser("boss", auth("root")))).contains("deleted+user")
        verify { userRepository.delete(admin) }
    }

    @Test
    fun shouldNotDeleteAnUnknownUser() {
        every { userRepository.findByUsername("nobody") } returns null

        assertThat(location(controller.deleteUser("nobody", auth("root")))).contains("not+found")
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
    fun shouldShowTheLastLoginOfTheUsers() {
        every { userRepository.findAll() } returns listOf(
            User(username = "anna", password = "p", lastLogin = java.time.Instant.parse("2026-01-02T03:04:05Z")),
            User(username = "bob", password = "p"),
        )

        val html = controller.admin(null)

        assertThat(html).contains("last login: 2026-01-02 03:04 UTC", "last login: unknown")
    }

    @Test
    fun shouldShowTheMediaCacheStatistics() {
        every { userRepository.findAll() } returns emptyList()
        every { mediaProxyService.cacheStatistics() } returns
            MediaProxyService.CacheStatistics(5, 100, 2, 17, java.time.Instant.parse("2026-01-02T03:04:05Z").toEpochMilli())

        val html = controller.admin(null)

        assertThat(html).contains("entries: 5 of 100 (2 expired)", "media items: 17", "oldest entry: 2026-01-02 03:04 UTC")
    }

    @Test
    fun shouldShowAnEmptyMediaCache() {
        assertThat(AdminController.cacheStatisticsHtml(MediaProxyService.CacheStatistics(0, 100, 0, 0, null)))
            .contains("entries: 0 of 100", "oldest entry: none")
    }

    @Test
    fun shouldRecognizeAdmins() {
        fun auth(role: String) = UsernamePasswordAuthenticationToken("u", "p", listOf(SimpleGrantedAuthority(role)))

        assertThat(AdminController.isAdmin(auth("ROLE_ADMIN"))).isTrue
        assertThat(AdminController.isAdmin(auth("ROLE_USER"))).isFalse
        assertThat(AdminController.isAdmin(null)).isFalse
    }
}
