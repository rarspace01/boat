package boat.torrent;

import java.util.List;

import boat.utilities.HttpHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TorrentServiceTest {

    @Test
    void getTrackerUrls() {
        // Given
        TorrentService torrentService = new TorrentService(new HttpHelper());
        // When
        List<String> trackerUrls = torrentService.getTrackerUrls();
        // Then
        assertTrue(trackerUrls.size()>0);
    }

    @Test
    void getReleaseTags() {
        // Given
        TorrentService torrentService = new TorrentService(null);
        // When
        List<String> releaseTags = torrentService.getReleaseTags();
        // Then
        assertTrue(releaseTags.size()>0);
    }
}