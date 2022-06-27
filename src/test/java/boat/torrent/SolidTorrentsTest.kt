package boat.torrent

import boat.utilities.HttpHelper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class SolidTorrentsTest {
    @Test
    fun shouldFindTorrents() {
        // Given
        // When
        Assertions.assertDoesNotThrow<List<Torrent>> { SolidTorrents(HttpHelper()).searchTorrents("planet") }
    }
}
