package boat.torrent.searchEngines

import boat.torrent.Torrent
import boat.utilities.HttpHelper
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class AnyBTTest {

    @Tag("live")
    @Test
    fun shouldFindTorrents() {
        // Given
        // When
        assertDoesNotThrow<List<Torrent>> { AnyBT(HttpHelper()).searchTorrents("planet") }
        // Then
    }
}