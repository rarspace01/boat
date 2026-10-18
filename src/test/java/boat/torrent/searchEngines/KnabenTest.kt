package boat.torrent.searchEngines

import boat.torrent.Torrent
import boat.utilities.HttpHelper
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class KnabenTest {

    @Disabled
    @Test
    fun shouldFindTorrents() {
        // Given
        // When
        assertDoesNotThrow<List<Torrent>> { Knaben(HttpHelper()).searchTorrents("planet") }
        // Then
    }

    @Disabled
    @Test
    fun shouldFindNoTorrents() {
        // Given
        // When
        val torrentList = Knaben(HttpHelper()).searchTorrents("ThisshouldntbeafindableStringAtall")
        // Then
        assertEquals(0, torrentList.size)
    }
}
