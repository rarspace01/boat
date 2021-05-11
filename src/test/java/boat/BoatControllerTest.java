package boat;

import java.util.List;

import boat.torrent.Torrent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoatControllerTest {

    @Test
    void cleanDuplicates() {
        // Given
        final BoatController boatController = new BoatController(null, null, null, null, null, null, null);

        final Torrent s1 = new Torrent("S1");
        final Torrent s2 = new Torrent("S2");
        s1.magnetUri="btih:ABC&";
        s2.magnetUri="btih:ABC&";
        s1.searchRating = 1;
        s2.searchRating = 2;
        // When
        final List<Torrent> torrentList = boatController.cleanDuplicates(List.of(s1, s2));
        // Then
        assertThat(torrentList.get(0)).isEqualTo(s2);
        assertThat(torrentList.size()).isEqualTo(1);
    }
}