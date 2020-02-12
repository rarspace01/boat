package hello.info;

import hello.torrent.Torrent;
import hello.utilities.HttpHelper;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

    @Disabled
    @Test
    void search() {
        // Given
        // When
        List<MediaItem> mediaItems = tfdbs.search("Planet");
        // Then
        assertTrue(mediaItems.size() > 0);
    }

    @Disabled
    @Test
    void searchEmpty() {
        // Given
        // When
        List<MediaItem> mediaItems = tfdbs.search("TrestTrest");
        // Then
        assertEquals(0, mediaItems.size());
    }

    @Disabled
    @Test
    void determineMediaType() {
        // Given
        Torrent mockTorrent = new Torrent();
        mockTorrent.name = "Big Buck Bunny (2008) [720p] [PLA]";
        // When
        MediaType mediaType = tfdbs.determineMediaType(mockTorrent);
        // Then
        assertEquals(MediaType.Movie, mediaType);

    }

}