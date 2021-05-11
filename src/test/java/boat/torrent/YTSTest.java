package boat.torrent;

import java.util.List;

import boat.utilities.HttpHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YTSTest {

    @Test
    void shouldFindTorrents() {
        // Given
        // When
        List<Torrent> torrentList = new YTS(new HttpHelper()).searchTorrents("planet");
        // Then
        assertTrue(torrentList.size() > 0);
    }

    @Test
    void shouldFindTorrentsWithURL() {
        // Given
        // When
        List<Torrent> torrentList = new YTS(new HttpHelper()).searchTorrents("ThisshouldntbeafindableStringAtall");
        // Then
        assertEquals(0, torrentList.size());
    }
}