package boat.torrent.searchEngines;

import boat.utilities.HttpHelper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PirateBayTest {

    @Disabled
    @Test
    void shouldFindTorrents() {
        // Given
        // When
        assertDoesNotThrow(() -> new PirateBay(new HttpHelper()).searchTorrents("planet"));
    }
}