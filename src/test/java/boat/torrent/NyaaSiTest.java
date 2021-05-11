package boat.torrent;

import java.util.List;

import boat.utilities.HttpHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NyaaSiTest {

    @Test
    public void shouldFindTorrents() {
        // Given
        // When
        List<Torrent> torrentList = new NyaaSi(new HttpHelper()).searchTorrents("planet");
        // Then
        assertTrue(torrentList.size() > 0);
    }
}