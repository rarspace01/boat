package boat.torrent.searchEngines;

import boat.torrent.Torrent;
import boat.utilities.HttpHelper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TorrentzTest {

    @Test
    void shouldFindTorrents() {
        // Given
        // When
        assertDoesNotThrow(() -> {
            new Torrentz(new HttpHelper()).searchTorrents("planet").size();
        });
    }

    @Test
    void shouldFindNoTorrents() {
        // Given
        // When
        List<Torrent> torrentList = new Torrentz(new HttpHelper()).searchTorrents("ThisshouldntbeafindableStringAtall");
        // Then
        assertEquals(0, torrentList.size());
    }
}