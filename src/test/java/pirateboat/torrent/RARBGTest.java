package pirateboat.torrent;

import org.junit.jupiter.api.Test;
import pirateboat.utilities.HttpHelper;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RARBGTest {

    @Test
    void shouldFindTorrents() {
        // Given
        // When
        assertDoesNotThrow(() -> new RARBG(new HttpHelper()).searchTorrents("planet"));
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