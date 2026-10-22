package boat.torrent.searchEngines;

import boat.torrent.Torrent;
import boat.utilities.HttpHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LimeTorrentsTest {

    @Tag("live")
    @Test
    void shouldFindTorrents() {
        // Given
        // When
        assertDoesNotThrow(() -> new LimeTorrents(new HttpHelper()).searchTorrents("planet").size());
    }

    @Tag("live")
    @Test
    void shouldFindNoTorrents() {
        // Given
        // When
        List<Torrent> torrentList = new LimeTorrents(new HttpHelper())
            .searchTorrents("ThisshouldntbeafindableStringAtall");
        // Then
        assertEquals(0, torrentList.size());
    }
}