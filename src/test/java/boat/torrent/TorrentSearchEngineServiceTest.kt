package boat.torrent

import boat.multifileHoster.MultifileHosterService
import io.mockk.mockk
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

internal class TorrentSearchEngineServiceTest {
    private val torrentInfoService: TorrentInfoService = mockk()
    private val multifileHosterService: MultifileHosterService = mockk()

    @Test
    fun cleanDuplicates() {
        // Given
        val tse = TorrentSearchEngineService(null, multifileHosterService, torrentInfoService)
        val s1 = Torrent("S1")
        val s2 = Torrent("S2")
        s1.magnetUri = "btih:ABC&"
        s2.magnetUri = "btih:ABC&"
        s1.searchRating = 1.0
        s2.searchRating = 2.0
        // When
        val torrentList = tse.cleanDuplicates(listOf(s1, s2))
        // Then
        Assertions.assertThat(torrentList[0]).isEqualTo(s2)
        Assertions.assertThat(torrentList.size).isEqualTo(1)
    }
}
