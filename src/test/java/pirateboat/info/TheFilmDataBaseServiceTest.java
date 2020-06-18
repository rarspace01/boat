package pirateboat.info;

import pirateboat.torrent.Torrent;
import pirateboat.utilities.HttpHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

class TheFilmDataBaseServiceTest {

    private TheFilmDataBaseService tfdbs;

    @BeforeEach
    public void beforeMethod() {
        this.tfdbs = new TheFilmDataBaseService(new HttpHelper());
    }

    @Test
    void search() {
        // Given
        // When
        List<MediaItem> mediaItems = tfdbs.search("Planet");
        // Then
        assertTrue(mediaItems.size() > 0);
    }

    @Test
    void searchEmpty() {
        // Given
        // When
        List<MediaItem> mediaItems = tfdbs.search("TrestTrest");
        // Then
        assertEquals(0, mediaItems.size());
    }

    @Test
    void determineMediaType() {
        // Given
        Torrent mockTorrent = new Torrent("Test");
        mockTorrent.name = "Big Buck Bunny (2008) [720p] [PLA]";
        // When
        MediaType mediaType = tfdbs.determineMediaType(mockTorrent);
        // Then
        assertEquals(MediaType.Movie, mediaType);

    }

    @Test
    void determineMediaTypeMore() {
        // Given
        Torrent mockTorrent = new Torrent("Test");
        mockTorrent.name = "Big.Buck.Bunny.2008.REMASTERED.1080p.BluRay.x264.DTS-FGT";
        // When
        MediaType mediaType = tfdbs.determineMediaType(mockTorrent);
        // Then
        assertEquals(MediaType.Movie, mediaType);

    }

}