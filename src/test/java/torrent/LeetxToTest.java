package torrent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

class LeetxToTest {

    @Test
    void shouldFindTorrents() {
        // Given
        // When
        List<Torrent> torrentList = new LeetxTo().searchTorrents("search");
        // Then
        assertTrue(torrentList.size() > 0);
    }
}