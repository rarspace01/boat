package boat.torrent.searchEngines;

import boat.torrent.Torrent;
import boat.utilities.HttpHelper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class YTSTest {

    @Test
    void shouldFindTorrents() {
        // Given
        // When
        assertDoesNotThrow(() -> new YTS(new HttpHelper()).searchTorrents("planet"));
        // Then
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