package pirateboat.info;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pirateboat.multifileHoster.MultifileHosterService;
import pirateboat.utilities.HttpHelper;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class TorrentMetaServiceTest {

    private TorrentMetaService torrentMetaService;

    @BeforeEach
    public void beforeMethod() {
        this.torrentMetaService = new TorrentMetaService(new MultifileHosterService(new HttpHelper()));
    }

    @Test
    void refreshTorrents() {
        // Given
        // When
        torrentMetaService.refreshTorrents();
        // Then
        assertNotNull(torrentMetaService.getActiveTorrents());
    }
}