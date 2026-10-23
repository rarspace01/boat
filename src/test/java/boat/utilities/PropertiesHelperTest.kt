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

    @Test
    fun requirePropertyFailsWithAClearMessage() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            PropertiesHelper.requireProperty("BOAT_SURELY_NOT_SET_PROPERTY")
        }.also { assertThat(it.message).contains("BOAT_SURELY_NOT_SET_PROPERTY", "boat.cfg") }
        assertThat(PropertiesHelper.requireProperty("PATH")).isNotEmpty()
    }

    @Test
    fun shouldReadTheFileAgainWhenItChanged(@org.junit.jupiter.api.io.TempDir dir: java.io.File) {
        val file = java.io.File(dir, "boat.cfg").apply { writeText("BOAT_TEST_SETTING = one\n") }
        val before = System.getProperty(PropertiesHelper.CONFIG_FILE_PROPERTY)
        System.setProperty(PropertiesHelper.CONFIG_FILE_PROPERTY, file.absolutePath)
        try {
            assertThat(PropertiesHelper.getProperty("BOAT_TEST_SETTING")).isEqualTo("one")
            file.writeText("BOAT_TEST_SETTING = second\n")
            file.setLastModified(file.lastModified() + 2000)
            assertThat(PropertiesHelper.getProperty("BOAT_TEST_SETTING")).isEqualTo("second")
        } finally {
            if (before == null) System.clearProperty(PropertiesHelper.CONFIG_FILE_PROPERTY) else System.setProperty(PropertiesHelper.CONFIG_FILE_PROPERTY, before)
        }
    }
}
