package pirateboat.multifileHoster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pirateboat.torrent.Torrent;
import pirateboat.torrent.TorrentFile;
import pirateboat.utilities.HttpHelper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlldebridTest {

    private Alldebrid alldebrid;

    @BeforeEach
    void beforeEach() {
        alldebrid = new Alldebrid(new HttpHelper());
    }

    @Test
    void getRemoteTorrents() {
        // Given
        // When
        final List<Torrent> remoteTorrents = alldebrid.getRemoteTorrents();
        // Then
        assertTrue(remoteTorrents != null);
    }

    @Test
    void getFilesFromTorrent() {
        // Given
        final List<Torrent> remoteTorrents = alldebrid.getRemoteTorrents();
        // When
        final Torrent torrent = remoteTorrents.stream().findFirst().orElse(null);
        if(torrent != null) {
            final List<TorrentFile> filesFromTorrent = alldebrid.getFilesFromTorrent(torrent);
            assertNotNull(filesFromTorrent);
        }
        assertTrue(true);
    }

}