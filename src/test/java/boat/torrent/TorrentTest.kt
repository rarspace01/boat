package boat.torrent

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class TorrentTest {
    @Test
    fun shouldAddRemoteMediaIdAndMediaTypeToDownloadForm() {
        val torrent = Torrent("Test")
        torrent.magnetUri = "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&dn=Nametest"

        val html = torrent.toHtml("{tmdb-1396}", boat.info.MediaType.Series)

        Assertions.assertTrue(html.contains("<input type=\"hidden\" name=\"mid\" value=\"{tmdb-1396}\"><input type=\"hidden\" name=\"mt\" value=\"Series\">"), html)
        Assertions.assertFalse(torrent.toHtml().contains("name=\"mt\""))
    }

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
    fun shouldNeverHaveAnEmptyTorrentId() {
        // an empty id is contained in every uri and would match any transfer
        val torrent = Torrent("Test")
        Assertions.assertTrue(torrent.torrentId.isNotEmpty())
        Assertions.assertNull(torrent.infoHash)
    }

    @Test
    fun shouldUseUriAsTorrentIdWithoutInfoHash() {
        val torrent = Torrent.of("https://example.com/movie.mkv")
        Assertions.assertEquals("https://example.com/movie.mkv", torrent.torrentId)
        Assertions.assertNull(torrent.infoHash)
    }

    @Test
    fun shouldPreferRemoteIdOverUriWithoutInfoHash() {
        val torrent = Torrent.of("https://example.com/movie.mkv")
        torrent.remoteId = "remote-1"
        Assertions.assertEquals("remote-1", torrent.torrentId)
    }

    @Test
    fun shouldHaveSameHashCodeForEqualTorrents() {
        val torrent1 = Torrent("S1", name = "one", magnetUri = "magnet:?xt=urn:btih:ABC")
        val torrent2 = Torrent("S2", name = "two", magnetUri = "magnet:?xt=urn:btih:abc")
        Assertions.assertEquals(torrent1, torrent2)
        Assertions.assertEquals(torrent1.hashCode(), torrent2.hashCode())
    }

    @Test
    fun shouldSortTorrent() {
        // Given
        val torrent1 = Torrent("Test1")
        val torrent2 = Torrent("Test2")
        torrent1.sizeInMB = 1.0
        torrent2.sizeInMB = 2.0
        // When
        val list = listOf(torrent1, torrent2).stream().sorted().toList()
        // Then
        org.assertj.core.api.Assertions.assertThat(list[0]).isEqualTo(torrent1)
    }

    @Test
    fun shouldGetNameFromUriIfDnIsInTheMiddle() {
        val torrent = Torrent.of("magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&dn=Some.Movie.2020&tr=udp%3A%2F%2Ftracker.org%3A6969")
        Assertions.assertEquals("Some.Movie.2020", torrent.torrentNameFromUri)
    }

    @Test
    fun shouldGetNameFromUriIfDnIsTheLastParameter() {
        val torrent = Torrent.of("magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&tr=udp%3A%2F%2Ftracker.org%3A6969&dn=Some.Movie.2020")
        Assertions.assertEquals("Some.Movie.2020", torrent.torrentNameFromUri)
    }

    @Test
    fun shouldGetNameFromUriIfDnIsTheFirstParameter() {
        val torrent = Torrent.of("magnet:?dn=Some.Movie.2020&xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2")
        Assertions.assertEquals("Some.Movie.2020", torrent.torrentNameFromUri)
    }

    @Test
    fun shouldNotGetNameFromUriWithoutDn() {
        val torrent = Torrent.of("magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&tr=udp%3A%2F%2Ftracker.org%3A6969")
        Assertions.assertNull(torrent.torrentNameFromUri)
    }

    @Test
    fun shouldNotGetNameFromUriWithEmptyDn() {
        val torrent = Torrent.of("magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&dn=&tr=udp%3A%2F%2Ftracker.org%3A6969")
        Assertions.assertNull(torrent.torrentNameFromUri)
    }

    @Test
    fun shouldNotMistakeOtherParametersEndingWithDnAsName() {
        val torrent = Torrent.of("magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&xdn=wrong")
        Assertions.assertNull(torrent.torrentNameFromUri)
    }
}
