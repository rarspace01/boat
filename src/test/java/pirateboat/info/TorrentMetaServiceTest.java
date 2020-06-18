package pirateboat.info;

import pirateboat.utilities.HttpHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TorrentMetaServiceTest {

    private TorrentMetaService torrentMetaService;

    @BeforeEach
    public void beforeMethod() {
        this.torrentMetaService = new TorrentMetaService(new HttpHelper(), new TheFilmDataBaseService(new HttpHelper()));
    }

    @Test
    void refreshTorrents() {
        // Given
        // When
        torrentMetaService.refreshTorrents();
        // Then
        assertTrue(torrentMetaService.getActiveTorrents() != null);
    }
}