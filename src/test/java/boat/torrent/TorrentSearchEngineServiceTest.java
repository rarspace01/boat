package boat.torrent;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TorrentSearchEngineServiceTest {

    @Test
    void cleanDuplicates() {
        // Given
        final TorrentSearchEngineService tse = new TorrentSearchEngineService(null, null);

        final Torrent s1 = new Torrent("S1");
        final Torrent s2 = new Torrent("S2");
        s1.magnetUri = "btih:ABC&";
        s2.magnetUri = "btih:ABC&";
        s1.searchRatingOld = 1;
        s2.searchRatingOld = 2;
        // When
        final List<Torrent> torrentList = tse.cleanDuplicates(List.of(s1, s2));
        // Then
        assertThat(torrentList.get(0)).isEqualTo(s2);
        assertThat(torrentList.size()).isEqualTo(1);
    }
}