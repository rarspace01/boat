package boat

import boat.torrent.TorrentSearchEngineService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
internal class DownloadMonitorIntegrationTest {
    @Autowired
    var downloadMonitor: DownloadMonitor? = null

    @Autowired
    var torrentSearchEngineService: TorrentSearchEngineService? = null
    @Test
    fun refreshTorrentSearchEngines() {
        // Given
        // When
        downloadMonitor!!.refreshTorrentSearchEngines()
        // Then
        Assertions.assertTrue(torrentSearchEngineService!!.getActiveSearchEngines().size > 0)
    }
}