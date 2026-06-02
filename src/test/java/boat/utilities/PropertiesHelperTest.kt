package boat.utilities

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class PropertiesHelperTest {

    @Test
    fun version() {
        // Given
        // When
        val version = PropertiesHelper.version
        // Then
        Assertions.assertNotNull(version)
        Assertions.assertTrue(!version!!.contains("version-missing"))
    }

    @Test
    fun property() {
        // Given
        // When
        val property = PropertiesHelper.getProperty("PATH")
        // Then
        Assertions.assertNotNull(property)
        Assertions.assertTrue(property!!.isNotEmpty())
    }
}