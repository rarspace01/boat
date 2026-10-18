package boat.torrent.searchEngines

import boat.torrent.Torrent
import boat.utilities.HttpHelper
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class TorrentsCSVTest {

    @Disabled
    @Test
    fun shouldFindTorrents() {
        // Given
        // When
        assertDoesNotThrow<List<Torrent>> { TorrentsCSV(HttpHelper()).searchTorrents("planet") }
        // Then
    }

    @Disabled
    @Test
    fun shouldFindNoTorrents() {
        // Given
        // When
        val torrentList = TorrentsCSV(HttpHelper()).searchTorrents("ThisshouldntbeafindableStringAtall")
        // Then
        assertEquals(0, torrentList.size)
    }
}
