package boat.torrent

import boat.multifileHoster.MultifileHosterService
import boat.utilities.HttpHelper
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TorrentSearchEngineServiceNoEnginesTest {

    @Test
    fun shouldReturnNoTorrentsIfNoSearchEngineIsActive() {
        // Given all search engines are unreachable
        val httpHelper: HttpHelper = mockk(relaxed = true)
        val torrentSearchEngineService = TorrentSearchEngineService(httpHelper, mockk(relaxed = true), mockk(relaxed = true))
        torrentSearchEngineService.refreshTorrentSearchEngines()
        assertThat(torrentSearchEngineService.getActiveSearchEngines()).isEmpty()
        // When
        val torrents = torrentSearchEngineService.searchTorrents("Ivakisaka")
        // Then
        assertThat(torrents).isEmpty()
    }
}
