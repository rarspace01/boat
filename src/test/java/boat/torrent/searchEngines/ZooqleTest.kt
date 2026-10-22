package boat.torrent.searchEngines

import boat.torrent.Torrent
import boat.utilities.HttpHelper
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class ZooqleTest {
    @Tag("live")
    @Test
    fun shouldFindTorrents() {
        // Given
        // When
        Assertions.assertDoesNotThrow<List<Torrent>> { Zooqle(HttpHelper()).searchTorrents("planet") }
    }

    @Tag("live")
    @Test
    fun shouldFindNoTorrents() {
        // Given
        // When
        val torrentList = Zooqle(HttpHelper()).searchTorrents("ThisshouldntbeafindableStringAtall")
        // Then
        Assertions.assertEquals(0, torrentList.size)
    }
}