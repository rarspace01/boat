package boat.info

import boat.utilities.HttpHelper
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TheTVDBServiceFailureTest {

    private val httpHelper: HttpHelper = mockk()

    @Test
    fun shouldReturnNullIfTheLoginFails() {
        // needs an api key to try the login, boat.default.cfg has one
        every { httpHelper.getPage(match { it.endsWith("/login") }, anyNullable<String>(), anyNullable<Map<String, String>>()) } returns ""

        assertThat(TheTVDBService(httpHelper).search("Dark")).isNull()
    }

    @Test
    fun shouldReturnNullAndLoginAgainIfTheSearchFails() {
        every { httpHelper.getPage(match { it.endsWith("/login") }, anyNullable<String>(), anyNullable<Map<String, String>>()) } returns """{"data":{"token":"t"}}"""
        every { httpHelper.getPage(match { it.contains("/search?") }, anyNullable<String>(), anyNullable<Map<String, String>>()) } returns ""
        val service = TheTVDBService(httpHelper)

        assertThat(service.search("Dark")).isNull()
        assertThat(service.search("Dark")).isNull()

        io.mockk.verify(exactly = 2) { httpHelper.getPage(match { it.endsWith("/login") }, anyNullable<String>(), anyNullable<Map<String, String>>()) }
    }

    @Test
    fun shouldWaitLongerAfterEveryFailedLogin() {
        every { httpHelper.getPage(match { it.endsWith("/login") }, anyNullable<String>(), anyNullable<Map<String, String>>()) } returns ""
        var now = 0L
        val service = TheTVDBService(httpHelper) { now }
        fun noSearches() = io.mockk.verify(exactly = 0) { httpHelper.getPage(match { it.contains("/search?") }, anyNullable<String>(), anyNullable<Map<String, String>>()) }

        repeat(3) { assertThat(service.search("Dark")).isNull() }
        now += java.util.concurrent.TimeUnit.SECONDS.toNanos(59)
        assertThat(service.search("Dark")).isNull()
        io.mockk.verify(exactly = 1) { httpHelper.getPage(match { it.endsWith("/login") }, anyNullable<String>(), anyNullable<Map<String, String>>()) }

        now += java.util.concurrent.TimeUnit.SECONDS.toNanos(1)
        assertThat(service.search("Dark")).isNull()
        io.mockk.verify(exactly = 2) { httpHelper.getPage(match { it.endsWith("/login") }, anyNullable<String>(), anyNullable<Map<String, String>>()) }

        // the second pause is two minutes
        now += java.util.concurrent.TimeUnit.SECONDS.toNanos(119)
        assertThat(service.search("Dark")).isNull()
        io.mockk.verify(exactly = 2) { httpHelper.getPage(match { it.endsWith("/login") }, anyNullable<String>(), anyNullable<Map<String, String>>()) }
        noSearches()
    }

    @Test
    fun shouldLoginAgainIfALookupByIdFails() {
        every { httpHelper.getPage(match { it.endsWith("/login") }, anyNullable<String>(), anyNullable<Map<String, String>>()) } returns """{"data":{"token":"t"}}"""
        every { httpHelper.getPage(match { it.contains("/series/") }, anyNullable<String>(), anyNullable<Map<String, String>>()) } returns ""
        val service = TheTVDBService(httpHelper)

        assertThat(service.findById("81189")).isNull()
        assertThat(service.episodeRuntimes("81189", 1)).isEmpty()
        assertThat(service.findById("81189")).isNull()

        io.mockk.verify(exactly = 3) { httpHelper.getPage(match { it.endsWith("/login") }, anyNullable<String>(), anyNullable<Map<String, String>>()) }
    }

    @Test
    fun shouldReturnResultsOfAnswerWithoutSeries() {
        every { httpHelper.getPage(match { it.endsWith("/login") }, anyNullable<String>(), anyNullable<Map<String, String>>()) } returns """{"data":{"token":"t"}}"""
        every { httpHelper.getPage(match { it.contains("/search?") }, anyNullable<String>(), anyNullable<Map<String, String>>()) } returns """{"data":[]}"""

        assertThat(TheTVDBService(httpHelper).search("Dark")).isEmpty()
    }
}
