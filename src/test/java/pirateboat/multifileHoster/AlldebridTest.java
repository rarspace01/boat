package pirateboat.multifileHoster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pirateboat.torrent.Torrent;
import pirateboat.utilities.HttpHelper;

import java.util.List;

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
        assertTrue(remoteTorrents.size() > 0);
    }
}