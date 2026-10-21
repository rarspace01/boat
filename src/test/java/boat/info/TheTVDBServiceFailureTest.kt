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
    fun shouldReturnResultsOfAnswerWithoutSeries() {
        every { httpHelper.getPage(match { it.endsWith("/login") }, anyNullable<String>(), anyNullable<Map<String, String>>()) } returns """{"data":{"token":"t"}}"""
        every { httpHelper.getPage(match { it.contains("/search?") }, anyNullable<String>(), anyNullable<Map<String, String>>()) } returns """{"data":[]}"""

        assertThat(TheTVDBService(httpHelper).search("Dark")).isEmpty()
    }
}
