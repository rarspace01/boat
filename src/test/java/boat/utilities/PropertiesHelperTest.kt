package boat.utilities

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class PropertiesHelperTest {
    // Given
    // When
    @get:Test
    val version: Unit
        // Then
        get() {
            // Given
            // When
            val version = PropertiesHelper.getVersion()
            // Then
            Assertions.assertTrue(!version.contains("version-missing"))
        }

    // Given
    // When
    @get:Test
    val property: Unit
        // Then
        get() {
            // Given
            // When
            val property = PropertiesHelper.getProperty("RCLONEDIR")
            // Then
            Assertions.assertNotNull(property)
            Assertions.assertTrue(property.length > 0)
        }
}