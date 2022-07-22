package boat.utilities;

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
        assertTrue(!version.contains("version-missing"));
    }

    @Test
    void getProperty() {
        // Given
        // When
        String property = PropertiesHelper.getProperty("RCLONEDIR");
        // Then
        assertNotNull(property);
        assertTrue(property.length() > 0);
    }
}