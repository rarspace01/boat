package boat.utilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProcessUtilTest {


    @Test
    void isRcloneInstalled() {
        // Given
        // When
        boolean rcloneInstalled = ProcessUtil.isRcloneInstalled();
        // Then
        assertNotNull(rcloneInstalled);
    }
}