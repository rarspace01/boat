package pirateboat.torrent;

import org.junit.Test;
import pirateboat.utilities.HttpHelper;

import java.util.List;

import static org.junit.Assert.assertTrue;

class KatcrTest {

    @Test
    void shouldFindTorrents() {
        // Given
        //when(httpHelper.getPage(anyString())).thenReturn("responseBody");
        // When
        List<Torrent> torrentList = new Katcr(new HttpHelper()).searchTorrents("planet");
        // Then
        assertTrue(torrentList.size() > 0);
    }
}