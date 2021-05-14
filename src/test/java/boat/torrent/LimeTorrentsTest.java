package boat.torrent;

import java.util.List;

import boat.utilities.HttpHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LimeTorrentsTest {

    @Test
    void shouldFindTorrents() {
        // Given
        // When
        assertThat(new LimeTorrents(new HttpHelper()).searchTorrents("planet").size()).isPositive();
    }

    @Test
    void shouldFindNoTorrents() {
        // Given
        // When
        List<Torrent> torrentList = new LimeTorrents(new HttpHelper())
            .searchTorrents("ThisshouldntbeafindableStringAtall");
        // Then
        assertEquals(0, torrentList.size());
    }
}