package torrent;

import org.junit.jupiter.api.Test;

import static junit.framework.TestCase.assertEquals;

class TorrentTest {

    @Test
    void getTorrentId() {
        // Given
        Torrent torrent = new Torrent();
        torrent.magnetUri = "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&dn=Nametest&tr=udp%3A%2F%2Ftracker.org%3A6969&tr=udp%3A%2F%2Ftracker.tracker.com%3A80&tr=udp%3A%2F%2Fopen.tracker.com%3A1337&tr=udp%3A%2F%2Ftracker.tracker.tk%3A6969&tr=udp%3A%2F%tracker.com%3A6969";
        // When
        String torrentTorrentId = torrent.getTorrentId();
        // Then
        assertEquals("10e93389d579ba4538c68b8d297268a81620fae2", torrentTorrentId);
    }
}