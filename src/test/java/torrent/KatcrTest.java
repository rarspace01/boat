package torrent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

class KatcrTest {

    @Test
    void shouldFindTorrents() {
        // Given
        // When
        List<Torrent> torrentList = new Katcr().searchTorrents("planet");
        // Then
        assertTrue(torrentList.size() > 0);
    }
}