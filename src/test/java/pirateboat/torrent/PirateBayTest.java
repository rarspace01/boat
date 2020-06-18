package pirateboat.torrent;

import pirateboat.utilities.HttpHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PirateBayTest {

    @Test
    void shouldFindTorrents() {
        // Given
        // When
        assertDoesNotThrow(() -> new PirateBay(new HttpHelper()).searchTorrents("planet"));
    }
}