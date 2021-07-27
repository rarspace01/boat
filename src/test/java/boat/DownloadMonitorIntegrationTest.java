package boat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import boat.torrent.Torrent;
import boat.torrent.TorrentSearchEngineService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DownloadMonitorIntegrationTest {

    @Autowired
    DownloadMonitor downloadMonitor;

    @Autowired
    TorrentSearchEngineService torrentSearchEngineService;

    @Test
    void refreshTorrentSearchEngines() {
        // Given
        // When
        downloadMonitor.refreshTorrentSearchEngines();
        // Then
        assertTrue(torrentSearchEngineService.getActiveSearchEngines().size() > 0);

    }

    @Disabled
    @Test
    void getTorrentToBeDownloaded() {
        // Given
        // When
        Torrent torrentToBeDownloaded = downloadMonitor.getTorrentToBeDownloaded();
        // Then
        assertNotNull(torrentToBeDownloaded);
    }

}