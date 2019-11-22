package torrent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.*;

class SolidTorrentsTest {

    @Test
    void shouldFindTorrents() {
        // Given
        // When
        List<Torrent> torrentList = new LeetxTo().searchTorrents("search");
        // Then
        assertTrue(torrentList.size() > 0);
    }
}