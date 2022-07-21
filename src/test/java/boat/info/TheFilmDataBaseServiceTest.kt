package boat.info

import boat.torrent.Torrent
import boat.utilities.HttpHelper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TheFilmDataBaseServiceTest {
    private var tfdbs: TheFilmDataBaseService? = null
    @BeforeEach
    fun beforeMethod() {
        tfdbs = TheFilmDataBaseService(HttpHelper())
    }

    @Test
    fun search() {
        // Given
        // When
        val mediaItems = tfdbs!!.search("Planet")
        // Then
        Assertions.assertTrue(mediaItems!!.size > 0)
    }

    @Test
    fun searchEmpty() {
        // Given
        // When
        val mediaItems = tfdbs!!.search("TrestTrest")
        // Then
        Assertions.assertEquals(0, mediaItems!!.size)
    }

    @Test
    fun determineMediaType() {
        // Given
        val mockTorrent = Torrent("Test")
        mockTorrent.name = "Big Buck Bunny (2008) [720p] [PLA]"
        // When
        val mediaType = tfdbs!!.determineMediaType(mockTorrent)
        // Then
        Assertions.assertEquals(MediaType.Movie, mediaType)
    }

    @Test
    fun determineMediaTypeMore() {
        // Given
        val mockTorrent = Torrent("Test")
        mockTorrent.name = "Big.Buck.Bunny.2008.REMASTERED.1080p.BluRay.x264.DTS-FGT"
        // When
        val mediaType = tfdbs!!.determineMediaType(mockTorrent)
        // Then
        Assertions.assertEquals(MediaType.Movie, mediaType)
    }
}