package pirateboat.torrent;

import pirateboat.info.TheFilmDataBaseService;
import pirateboat.multifileHoster.Premiumize;
import pirateboat.utilities.HttpHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PremiumizeTest {

    Premiumize premiumize;

    @BeforeEach
    void beforeEach() {
        premiumize = new Premiumize(new HttpHelper());
    }

    @Test
    void getCacheStateOfTorrent() {
        // Given
        List<Torrent> listOfTorrents = new ArrayList<>();
        Torrent torrentToBeChecked = new Torrent("Test");
        torrentToBeChecked.magnetUri="magnet:?xt=urn:btih:e2467cbf021192c241367b892230dc1e05c0580e&dn=test";
        listOfTorrents.add(torrentToBeChecked);

        // When
        premiumize.enrichCacheStateOfTorrents(listOfTorrents);
        // Then
        assertTrue(listOfTorrents.get(0).cached.size()>0);
    }

    @Test
    void getCacheStateOfTorrents() {
        // Given
        List<Torrent> listOfTorrents = new ArrayList<>();
        Torrent torrentToBeChecked = new Torrent("Test");
        torrentToBeChecked.magnetUri="magnet:?xt=urn:btih:e2467cbf021192c241367b892230dc1e05c0580e&dn=test";
        listOfTorrents.add(torrentToBeChecked);
        listOfTorrents.add(torrentToBeChecked);

        // When
        premiumize.enrichCacheStateOfTorrents(listOfTorrents);
        // Then
        assertTrue(listOfTorrents.get(0).cached.size()>0);
    }

    @Test()
    void getCacheStateOfTorrentsWithEMptyDN() {
        // Given
        List<Torrent> listOfTorrents = new ArrayList<>();
        Torrent torrentToBeChecked = new Torrent("Test");
        torrentToBeChecked.magnetUri="magnet:?xt=urn:btih:e2467cbf021192c241367b892230dc1e05c0580e&tracker=test";
        listOfTorrents.add(torrentToBeChecked);

        // When
        premiumize.enrichCacheStateOfTorrents(listOfTorrents);
        // Then
        assertTrue(listOfTorrents.get(0).cached.size()>0);
    }
}