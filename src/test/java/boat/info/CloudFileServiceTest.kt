package boat.info

import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled

class CloudFileServiceTest {

    @Disabled
    @Test
    fun getFreeSpaceInMegaBytes() {
        // Given
        // When
        val freeSpaceInMegaBytes = CloudFileService().getFreeSpaceInMegaBytes()
        // Then
        assertThat(freeSpaceInMegaBytes).isGreaterThan(-1.0)
    }
}