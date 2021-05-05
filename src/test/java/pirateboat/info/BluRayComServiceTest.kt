package pirateboat.info

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import pirateboat.utilities.HttpHelper

internal class BluRayComServiceTest {

    private val bluRayComService: BluRayComService = BluRayComService(HttpHelper())

    @Test
    fun getReleasesForMonthAndYear() {

        // Given
        // When
        val releasesForMonthAndYear = bluRayComService.getReleasesForMonthAndYear(0, 2021)
        // Then
        assertThat(releasesForMonthAndYear.size).isGreaterThan(0)
    }
}