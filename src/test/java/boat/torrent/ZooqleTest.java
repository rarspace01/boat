package boat.torrent;

import java.util.List;

import boat.utilities.HttpHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ZooqleTest {

    @Test
    void shouldFindTorrents() {
        // Given
        // When
        assertDoesNotThrow(() -> new Zooqle(new HttpHelper()).searchTorrents("planet"));
    }

    @Test
    void shouldExtractSize() {
        // Given
        // When
        assertDoesNotThrow(() -> new Zooqle(new HttpHelper()).searchTorrents("blackish+s02e09"));
    }

    @Test
    void shouldFindNoTorrents() {
        // Given
        // When
        List<Torrent> torrentList = new Zooqle(new HttpHelper()).searchTorrents("ThisshouldntbeafindableStringAtall");
        // Then
        assertEquals(0, torrentList.size());
    }

}