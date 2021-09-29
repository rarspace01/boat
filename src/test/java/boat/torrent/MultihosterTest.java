package boat.torrent;

import java.util.ArrayList;
import java.util.List;

import boat.multifileHoster.Alldebrid;
import boat.multifileHoster.Premiumize;
import boat.utilities.HttpHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MultihosterTest {

    Premiumize premiumize;
    Alldebrid alldebrid;

    @BeforeEach
    void beforeEach() {
        premiumize = new Premiumize(new HttpHelper());
        alldebrid = new Alldebrid(new HttpHelper());
    }

    @Test
    void getCacheStateOfTorrent() {
        // Given
        List<Torrent> listOfTorrents = new ArrayList<>();
        Torrent torrentToBeChecked = new Torrent("Test");
        torrentToBeChecked.magnetUri = "magnet:?xt=urn:btih:e2467cbf021192c241367b892230dc1e05c0580e&dn=test";
        listOfTorrents.add(torrentToBeChecked);

        // When
        assertDoesNotThrow(() -> premiumize.enrichCacheStateOfTorrents(listOfTorrents));
        // Then
        //assertThat(listOfTorrents).allMatch(torrent -> torrent.cached.size() > 0);
    }

    @Test
    void getCacheStateOfTorrents() {
        // Given
        List<Torrent> listOfTorrents = new ArrayList<>();
        Torrent torrentToBeChecked = new Torrent("Test");
        torrentToBeChecked.magnetUri = "magnet:?xt=urn:btih:e2467cbf021192c241367b892230dc1e05c0580e&dn=test";
        for (int i = 0; i < 2; i++) {
            listOfTorrents.add(torrentToBeChecked);
        }

        // When
        assertDoesNotThrow(() -> premiumize.enrichCacheStateOfTorrents(listOfTorrents));
        // Then
        //assertThat(listOfTorrents).allMatch(torrent -> torrent.cached.size() > 0);
    }

    @Test
    void getCacheStateOfALotOfTorrents() {
        // Given
        List<Torrent> listOfTorrents = new ArrayList<>();
        Torrent torrentToBeChecked = new Torrent("Test");
        torrentToBeChecked.magnetUri = "magnet:?xt=urn:btih:e2467cbf021192c241367b892230dc1e05c0580e&dn=test";

        for (int i = 0; i < 100; i++) {
            listOfTorrents.add(torrentToBeChecked);
        }

        // When
        assertDoesNotThrow(() -> premiumize.enrichCacheStateOfTorrents(listOfTorrents));
        // Then
        //assertThat(listOfTorrents).allMatch(torrent -> torrent.cached.size() > 0);
    }

    @Test()
    void getCacheStateOfTorrentsWithEmptyDN() {
        // Given
        List<Torrent> listOfTorrents = new ArrayList<>();
        Torrent torrentToBeChecked = new Torrent("Test");
        torrentToBeChecked.magnetUri = "magnet:?xt=urn:btih:e2467cbf021192c241367b892230dc1e05c0580e&tracker=test";
        listOfTorrents.add(torrentToBeChecked);

        // When
        assertDoesNotThrow(() -> premiumize.enrichCacheStateOfTorrents(listOfTorrents));
        // Then
        //assertTrue(listOfTorrents.get(0).cached.size() > 0);
    }

    @Test()
    void getCacheStateOfTorrentsWithEmptyDNAllDebrid() {
        // Given
        List<Torrent> listOfTorrents = new ArrayList<>();
        Torrent torrentToBeChecked = new Torrent("Test");
        torrentToBeChecked.magnetUri = "magnet:?xt=urn:btih:e2467cbf021192c241367b892230dc1e05c0580e&tracker=test";
        listOfTorrents.add(torrentToBeChecked);

        // When
        assertDoesNotThrow(() -> alldebrid.enrichCacheStateOfTorrents(listOfTorrents));
        // Then
        //assertTrue(listOfTorrents.get(0).cached.size() > 0);
    }
}