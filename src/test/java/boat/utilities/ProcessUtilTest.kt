package boat.utilities

import org.assertj.core.api.Assertions.assertThat
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
        assertThat(rcloneInstalled).isTrue()
    }
}