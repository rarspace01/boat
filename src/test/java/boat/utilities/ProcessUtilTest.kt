package boat.utilities

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class ProcessUtilTest {
    @Disabled
    @Test
    fun isRcloneInstalled() {
        // Given
        // When
        val rcloneInstalled = ProcessUtil.isRcloneInstalled
        // Then
        Assertions.assertTrue(rcloneInstalled)
    }
}