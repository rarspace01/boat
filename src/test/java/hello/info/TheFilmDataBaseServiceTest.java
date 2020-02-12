package hello.info;

import hello.utilities.HttpHelper;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

class TheFilmDataBaseServiceTest {

    private TheFilmDataBaseService tfdbs;

    @BeforeEach
    public void beforeMethod() {
        this.tfdbs = new TheFilmDataBaseService(new HttpHelper());
    }

    @Ignore
    @Test
    void search() {
        // Given
        // When
        List<MediaItem> mediaItems = tfdbs.search("Planet");
        // Then
        assertTrue(mediaItems.size() > 0);
    }

}