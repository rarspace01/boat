package boat.torrent

import boat.utilities.HttpHelper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class ZooqleTest {
    @Disabled
    @Test
    fun shouldFindTorrents() {
        // Given
        // When
        Assertions.assertDoesNotThrow<List<Torrent>> { Zooqle(HttpHelper()).searchTorrents("planet") }
    }

    @Disabled
    @Test
    fun shouldFindNoTorrents() {
        // Given
        // When
        val torrentList = Zooqle(HttpHelper()).searchTorrents("ThisshouldntbeafindableStringAtall")
        // Then
        Assertions.assertEquals(0, torrentList.size)
    }
}