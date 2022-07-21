package boat.torrent

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.List
import java.util.stream.Collectors

internal class TorrentTest {
    @Test
    fun shouldGetTorrentId() {
        // Given
        val torrent = Torrent("Test")
        torrent.magnetUri =
            "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&dn=Nametest&tr=udp%3A%2F%2Ftracker.org%3A6969&tr=udp%3A%2F%2Ftracker.tracker.com%3A80&tr=udp%3A%2F%2Fopen.tracker.com%3A1337&tr=udp%3A%2F%2Ftracker.tracker.tk%3A6969&tr=udp%3A%2F%tracker.com%3A6969"
        // When
        val torrentTorrentId = torrent.torrentId
        // Then
        Assertions.assertEquals("10e93389d579ba4538c68b8d297268a81620fae2", torrentTorrentId)
    }

    @Test
    fun shouldGetTorrentIdIfEmpty() {
        // Given
        val torrent = Torrent("Test")
        // When
        val torrentTorrentId = torrent.torrentId
        // Then
        org.assertj.core.api.Assertions.assertThat(torrentTorrentId).isOfAnyClassIn(String::class.java)
    }

    @Test
    fun shouldSortTorrent() {
        // Given
        val torrent1 = Torrent("Test1")
        val torrent2 = Torrent("Test2")
        torrent1.sizeInMB = 1.0
        torrent2.sizeInMB = 2.0
        // When
        val list = List.of(torrent1, torrent2).stream().sorted().collect(Collectors.toList())
        // Then
        org.assertj.core.api.Assertions.assertThat(list[0]).isEqualTo(torrent1)
    }
}