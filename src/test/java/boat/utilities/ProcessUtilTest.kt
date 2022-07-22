package boat.utilities

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class ProcessUtilTest {
    // Given
    // When
    @get:Test
    val isRcloneInstalled:
    // Then
            Unit
        get() {
            // Given
            // When
            val rcloneInstalled = ProcessUtil.isRcloneInstalled()
            // Then
            Assertions.assertNotNull(rcloneInstalled)
        }
}