package boat.utilities

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class ProcessUtilTest {
    // Given
    // When
    @Test
    fun isRcloneInstalled() {
        // Given
        // When
        val rcloneInstalled = ProcessUtil.isRcloneInstalled
        // Then
        Assertions.assertTrue(rcloneInstalled)
    }
}