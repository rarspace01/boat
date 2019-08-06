package torrent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

class NyaaSiTest {

    @Test
    public void shouldFindTorrents() {
        // Given
        // When
        List<Torrent> torrentList = new NyaaSi().searchTorrents("search");
        // Then
        assertTrue(torrentList.size() > 0);
    }
}