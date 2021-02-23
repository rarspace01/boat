package pirateboat.torrent;

import org.junit.jupiter.api.Test;
import pirateboat.utilities.HttpHelper;

import java.util.List;

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