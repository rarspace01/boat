package pirateboat.torrent;

import org.junit.jupiter.api.Test;
import pirateboat.utilities.HttpHelper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
        assertEquals(true, true);
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