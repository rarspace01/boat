package boat.info

import boat.utilities.HttpHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class BluRayComServiceTest {

    private val bluRayComService: BluRayComService = BluRayComService(HttpHelper())

    @Disabled
    @Test
    fun getReleasesForMonthAndYear() {

        // Given
        // When
        val releasesForMonthAndYear = bluRayComService.getReleasesForMonthAndYear(1, 2021)
        // Then
        assertThat(releasesForMonthAndYear.size).isGreaterThan(0)
    }
}