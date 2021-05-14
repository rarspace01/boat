package boat;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import boat.torrent.Torrent;
import boat.torrent.TorrentFile;
import boat.torrent.TorrentSearchEngineService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DownloadMonitorTest {

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

    @Test
    void shouldGetETABeforeFirstFile() {
        // Given
        Torrent torrent = new Torrent("Test");
        torrent.lsize = 1000.0;
        TorrentFile torrentFile = new TorrentFile();
        torrentFile.filesize = 1024 * 1024 * 500;
        // When
        final String uploadStatusString = downloadMonitor.getUploadStatusString(torrent, List.of(torrentFile), 0, null);
        // Then
        assertThat(uploadStatusString).doesNotMatch("Uploading: 0/1 done ETA: 00:00:00");
    }
}