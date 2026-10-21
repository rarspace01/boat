package boat.utilities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PropertiesHelperTest {

    @Test
    fun version() {
        // Given
        // When
        val version = PropertiesHelper.version
        // Then
        assertThat(version).isNotNull
        assertThat(version).doesNotContain("version-missing")
    }

    @Test
    fun property() {
        // Given
        // When
        val property = PropertiesHelper.getProperty("PATH")
        // Then
        assertThat(property).isNotNull
        assertThat(property).isNotEmpty
    }

    @Test
    fun missingPropertyFallsBackToDefault() {
        assertThat(PropertiesHelper.getProperty("BOAT_SURELY_NOT_SET_PROPERTY", "fallback")).isEqualTo("fallback")
        assertThat(PropertiesHelper.getProperty("BOAT_SURELY_NOT_SET_PROPERTY")).isNull()
    }
}
