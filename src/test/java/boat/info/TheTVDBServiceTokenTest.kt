package boat.info

import boat.utilities.HttpHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

class TheTVDBServiceTokenTest {

    private val httpHelper: HttpHelper = mockk()
    private val now = Instant.parse("2026-10-19T12:00:00Z")

    private fun jwt(expiresAt: Instant?): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val payload = if (expiresAt != null) """{"exp":${expiresAt.epochSecond}}""" else "{}"
        return "${encoder.encodeToString("""{"alg":"none"}""".toByteArray())}.${encoder.encodeToString(payload.toByteArray())}.signature"
    }

    private fun service(tokenFile: Path, at: Instant = now) =
        TheTVDBService(httpHelper, tokenFile.toString(), { "apikey" }, Clock.fixed(at, ZoneOffset.UTC))

    private fun givenLoginReturns(vararg tokens: String) {
        every { httpHelper.getPage(match { it.endsWith("/login") }, any<String>(), any<Map<String, String>>()) } returnsMany
            tokens.map { """{"status": "success", "data": {"token": "$it"}}""" }
        every { httpHelper.getPage(match { it.contains("/search") }, null, any<Map<String, String>>()) } returns """{"data": []}"""
    }

    private fun verifySearchedWith(token: String, times: Int) {
        verify(exactly = times) { httpHelper.getPage(match { it.contains("/search") }, null, mapOf("Authorization" to "Bearer $token")) }
    }

    @Test
    fun `token should be reused by later requests and service instances`(@TempDir tempDir: Path) {
        val tokenFile = tempDir.resolve("tvdbToken")
        val token = jwt(now.plus(Duration.ofDays(30)))
        givenLoginReturns(token)

        val service = service(tokenFile)
        service.search("Breaking Bad")
        service.search("The Office")
        service(tokenFile).search("Breaking Bad")

        verify(exactly = 1) { httpHelper.getPage(match { it.endsWith("/login") }, any<String>(), any<Map<String, String>>()) }
        verifySearchedWith(token, 3)
    }

    @Test
    fun `token should be renewed when it is about to expire`(@TempDir tempDir: Path) {
        val tokenFile = tempDir.resolve("tvdbToken")
        val firstToken = jwt(now.plus(Duration.ofDays(30)))
        val secondToken = jwt(now.plus(Duration.ofDays(60)))
        givenLoginReturns(firstToken, secondToken)

        service(tokenFile).search("Breaking Bad")
        // the token expires in less than a day
        service(tokenFile, now.plus(Duration.ofDays(29).plusHours(1))).search("Breaking Bad")

        verify(exactly = 2) { httpHelper.getPage(match { it.endsWith("/login") }, any<String>(), any<Map<String, String>>()) }
        verifySearchedWith(firstToken, 1)
        verifySearchedWith(secondToken, 1)
    }

    @Test
    fun `token without expiry should be used for a month`(@TempDir tempDir: Path) {
        val tokenFile = tempDir.resolve("tvdbToken")
        val firstToken = jwt(null)
        val secondToken = jwt(null) + "2"
        givenLoginReturns(firstToken, secondToken)

        service(tokenFile).search("Breaking Bad")
        service(tokenFile, now.plus(Duration.ofDays(28))).search("Breaking Bad")
        service(tokenFile, now.plus(Duration.ofDays(30))).search("Breaking Bad")

        verifySearchedWith(firstToken, 2)
        verifySearchedWith(secondToken, 1)
    }
}
