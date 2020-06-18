package pirateboat.torrent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TorrentServiceTest {

    @Test
    void getTrackerUrls() {
        // Given
        TorrentService torrentService = new TorrentService();
        // When
        List<String> trackerUrls = torrentService.getTrackerUrls();
        // Then
        assertTrue(trackerUrls.size()>0);
    }

    @Test
    void getReleaseTags() {
        // Given
        TorrentService torrentService = new TorrentService();
        // When
        List<String> releaseTags = torrentService.getReleaseTags();
        // Then
        assertTrue(releaseTags.size()>0);
    }
}