package boat.torrent.searchEngines;

import boat.utilities.HttpHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PirateBayTest {

    @Tag("live")
    @Test
    void shouldFindTorrents() {
        // Given
        // When
        assertDoesNotThrow(() -> new PirateBay(new HttpHelper()).searchTorrents("planet"));
    }
}