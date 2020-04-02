package hello.torrent;

import hello.utilities.HttpHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class LeetxToTest {

    @Test
    void shouldFindTorrents() {
        // Given
        // When
        assertDoesNotThrow(() -> new LeetxTo(new HttpHelper()).searchTorrents("planet"));
    }
}