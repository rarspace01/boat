package boat.torrent;

import java.util.ArrayList;
import java.util.List;

import boat.utilities.HttpHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RARBGTest {

    @Test
    void shouldFindTorrents() {
        // Given
        final List<Torrent> torrents = new ArrayList<>();
        // When
        assertDoesNotThrow(() -> {
            torrents.addAll(new RARBG(new HttpHelper()).searchTorrents("planet"));
        });
        // Then
    }

    @Test
    void shouldFindTorrentsWithURL() {
        // Given
        // When
        List<Torrent> torrentList = new RARBG(new HttpHelper()).searchTorrents("ThisshouldntbeafindableStringAtall");
        // Then
        assertEquals(0, torrentList.size());
    }

}