package boat.info

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

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