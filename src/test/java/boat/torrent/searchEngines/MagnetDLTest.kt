package boat.torrent.searchEngines

import boat.torrent.Torrent
import boat.utilities.HttpHelper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class MagnetDLTest {
    @Test
    fun shouldFindTorrents() {
        // Given
        // When
        Assertions.assertDoesNotThrow<List<Torrent>> { MagnetDL(HttpHelper()).searchTorrents("planet") }
    }

    @Test
    fun shouldFindNoTorrents() {
        // Given
        // When
        val torrentList = MagnetDL(HttpHelper()).searchTorrents("ThisshouldntbeafindableStringAtall")
        // Then
        Assertions.assertEquals(0, torrentList.size)
    }
}