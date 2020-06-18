package pirateboat.utilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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