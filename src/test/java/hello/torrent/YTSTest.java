package hello.torrent;

import hello.utilities.HttpHelper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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