package torrent;

import org.junit.jupiter.api.Test;
import utilities.HttpHelper;

import java.util.List;

import static org.junit.Assert.assertTrue;

class SolidTorrentsTest {

    @Test
    void shouldFindTorrents() {
        // Given
        // When
        List<Torrent> torrentList = new SolidTorrents(new HttpHelper()).searchTorrents("planet");
        // Then
        assertTrue(torrentList.size() > 0);
    }
}