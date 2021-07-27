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
        torrent.magnetUri = "btih:D540FC48EB12F2833163EED6421D449DD8F1CE1F&"
        val torrentList = mutableListOf(
            torrent
        )
        // When
        torrentInfoService.refreshSeedAndLeecherFromTracker(
            "http://tracker.opentrackr.org:1337/announce", torrentList
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
        torrent.magnetUri = "btih:D540FC48EB12F2833163EED6421D449DD8F1CE1F&"
        torrent2.magnetUri = "btih:e20efbee11972b4585387ed513de8fd29aeed0b3&"
        val torrentList = mutableListOf(
            torrent, torrent2
        )
        // When
        torrentInfoService.refreshSeedAndLeecherFromTracker(
            "http://tracker.opentrackr.org:1337/announce", torrentList
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
        torrent.magnetUri = "btih:4344503B7E797EBF31582327A5BAAE35B11BDA01&"
        torrent2.magnetUri = "btih:e20efbee11972b4585387ed513de8fd29aeed0b3&"
        val torrentList = mutableListOf(
            torrent, torrent2
        )
        // When
        val torrentList2 = torrentInfoService.refreshTorrentStats(torrentList)
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
        torrent.magnetUri = "btih:4344503B7E797EBF31582327A5BAAE35B11BDA01&"
        torrent2.magnetUri = "btih:e20efbee11972b4585387ed513de8fd29aeed0b3&"
        torrent3.magnetUri = "btih:12345bee11972b4585387ed513de8fd29aeed0b3&"
        val torrentList = mutableListOf(
            torrent, torrent2
        )
        for (i in 0 until 50) {
            torrentList.add(torrent)
            torrentList.add(torrent2)
            torrentList.add(torrent3)
        }
        // When
        val torrentList2 = torrentInfoService.refreshTorrentStats(torrentList)
        // Then
        assertThat(torrentList[99].leecher).isGreaterThan(-1)
        assertThat(torrentList[99].seeder).isGreaterThan(-1)
    }

}