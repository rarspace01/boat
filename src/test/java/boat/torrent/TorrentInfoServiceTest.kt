package boat.torrent

import boat.utilities.HttpHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TorrentInfoServiceTest {

    @Test
    fun getSeedAndLeecherFromTracker() {
        // Given
        val torrentInfoService = TorrentInfoService(HttpHelper())
        val torrent = Torrent("test")
        torrent.magnetUri = "btih:4a3f5e08bcef825718eda30637230585e3330599&"
        val torrentList = mutableListOf(
            torrent
        )
        // When
        torrentInfoService.refreshSeedAndLeecherFromTracker(
            TorrentService().trackerUrls.first(), torrentList
        )
        // Then
        assertThat(torrentList[0].leecher).isGreaterThan(-1)
        assertThat(torrentList[0].seeder).isGreaterThan(-1)

    }

    @Test
    fun getSeedAndLeechersFromTracker() {
        // Given
        val torrentInfoService = TorrentInfoService(HttpHelper())
        val torrent = Torrent("test")
        val torrent2 = Torrent("test2")
        torrent.magnetUri = "btih:4a3f5e08bcef825718eda30637230585e3330599&"
        torrent2.magnetUri = "btih:41e6cd50ccec55cd5704c5e3d176e7b59317a3fb&"
        val torrentList = mutableListOf(
            torrent, torrent2
        )
        // When
        torrentInfoService.refreshSeedAndLeecherFromTracker(
            TorrentService().trackerUrls.first(), torrentList
        )
        // Then
        assertThat(torrentList[1].leecher).isGreaterThan(-1)
        assertThat(torrentList[1].seeder).isGreaterThan(-1)
    }

    @Test
    fun resfreshSeederAndLeechersFromTracker() {
        // Given
        val torrentInfoService = TorrentInfoService(HttpHelper())
        val torrent = Torrent("test")
        val torrent2 = Torrent("test2")
        torrent.magnetUri = "btih:4a3f5e08bcef825718eda30637230585e3330599&"
        torrent2.magnetUri = "btih:41e6cd50ccec55cd5704c5e3d176e7b59317a3fb&"
        val torrentList = mutableListOf(
            torrent, torrent2
        )
        // When
        torrentInfoService.refreshTorrentStats(torrentList)
        // Then
        assertThat(torrentList[1].leecher).isGreaterThan(-1)
        assertThat(torrentList[1].seeder).isGreaterThan(-1)
    }

    @Test
    fun resfreshAlotOfSeederAndLeechersFromTracker() {
        // Given
        val torrentInfoService = TorrentInfoService(HttpHelper())
        val torrent = Torrent("test")
        val torrent2 = Torrent("test2")
        val torrent3 = Torrent("test3")
        torrent.magnetUri = "btih:4a3f5e08bcef825718eda30637230585e3330599&"
        torrent2.magnetUri = "btih:41e6cd50ccec55cd5704c5e3d176e7b59317a3fb&"
        torrent3.magnetUri = "btih:2e8e44068b254814ea1a7d4969a9af1d78e0f51f&"
        val torrentList = mutableListOf(
            torrent, torrent2
        )
        for (i in 0 until 50) {
            torrentList.add(torrent)
            torrentList.add(torrent2)
            torrentList.add(torrent3)
        }
        // When
        torrentInfoService.refreshTorrentStats(torrentList)
        // Then
        assertThat(torrentList[99].leecher).isGreaterThan(-1)
        assertThat(torrentList[99].seeder).isGreaterThan(-1)
    }

}