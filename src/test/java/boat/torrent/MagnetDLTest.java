package boat.torrent;

import java.util.List;

import boat.utilities.HttpHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MagnetDLTest {

    @Test
    void shouldFindTorrents() {
        // Given
        // When
        assertThat(new MagnetDL(new HttpHelper()).searchTorrents("planet").size()).isPositive();
    }

    @Test
    void shouldFindNoTorrents() {
        // Given
        // When
        List<Torrent> torrentList = new MagnetDL(new HttpHelper()).searchTorrents("ThisshouldntbeafindableStringAtall");
        // Then
        assertEquals(0, torrentList.size());
    }
}