package utilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertiesHelperTest {

    @Test
    void getVersion() {
        // Given
        // When
        String version = PropertiesHelper.getVersion();
        // Then
        assertTrue(version.contains("."));
    }

    @Test
    void getProperty() {
        // Given
        // When
        String property = PropertiesHelper.getProperty("rclonedir");
        // Then
        assertNotNull(property);
        assertTrue(property.length() > 0);
    }
}