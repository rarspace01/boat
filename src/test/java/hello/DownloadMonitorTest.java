package hello;

import hello.torrent.TorrentSearchEngineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@SpringBootTest
class DownloadMonitorTest {

    @Autowired
    DownloadMonitor downloadMonitor;

    @Autowired
    TorrentSearchEngineService torrentSearchEngineService;

    @Test
    void refreshTorrentSearchEngines() {
        // When
        downloadMonitor.refreshTorrentSearchEngines();
        // Then
        assertThat(torrentSearchEngineService.getActiveSearchEngines().size()>0,is(true));

    }
}