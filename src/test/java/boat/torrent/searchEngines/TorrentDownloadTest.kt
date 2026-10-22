package boat.torrent.searchEngines

import boat.torrent.Torrent
import boat.utilities.HttpHelper
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TorrentDownloadTest {

    @Tag("live")
    @Test
    fun shouldFindTorrents() {
        // Given
        // When
        assertDoesNotThrow<List<Torrent>> { TorrentDownload(HttpHelper()).searchTorrents("planet") }
        // Then
    }

    @Tag("live")
    @Test
    fun shouldFindNoTorrents() {
        // Given
        // When
        val torrentList = TorrentDownload(HttpHelper()).searchTorrents("ThisshouldntbeafindableStringAtall")
        // Then
        assertEquals(0, torrentList.size)
    }
}
