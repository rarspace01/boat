package pirateboat.torrent;

import org.junit.jupiter.api.Test;
import pirateboat.utilities.HttpHelper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class KatTest {

    @Test
    public void shouldFindTorrents() {
        // Given
        //when(httpHelper.getPage(anyString())).thenReturn("responseBody");
        // When
        List<Torrent> torrentList = new Kat(new HttpHelper()).searchTorrents("planet");
        // Then
        assertNotNull(torrentList);
    }
}