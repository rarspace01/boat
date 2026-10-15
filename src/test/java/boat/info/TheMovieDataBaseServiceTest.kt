package boat.info

import boat.torrent.Torrent
import boat.utilities.HttpHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TheMovieDataBaseServiceTest {
    private var tfdbs: TheMovieDataBaseService? = null
    @BeforeEach
    fun beforeMethod() {
        tfdbs = TheMovieDataBaseService(HttpHelper())
    }

    @Test
    fun search() {
        // Given
        // When
        val mediaItems = tfdbs!!.search("Planet")
        // Then
        assertThat(mediaItems).isNotNull
        assertThat(mediaItems!!.size).isGreaterThan(0)
    }

    @Test
    fun searchEmpty() {
        // Given
        // When
        val mediaItems = tfdbs!!.search("blueasablue")
        // Then
        assertThat(mediaItems).isNotNull
        assertThat(mediaItems).isEmpty()
    }

    @Test
    fun determineMediaType() {
        // Given
        val mockTorrent = Torrent("Test")
        mockTorrent.name = "Big Buck Bunny (2008) [720p] [PLA]"
        // When
        val mediaType = tfdbs!!.determineMediaType(mockTorrent)
        // Then
        assertThat(mediaType).isEqualTo(MediaType.Movie)
    }

    @Test
    fun determineMediaTypeMore() {
        // Given
        val mockTorrent = Torrent("Test")
        mockTorrent.name = "Big.Buck.Bunny.2008.REMASTERED.1080p.BluRay.x264.DTS-FGT"
        // When
        val mediaType = tfdbs!!.determineMediaType(mockTorrent)
        // Then
        assertThat(mediaType).isEqualTo(MediaType.Movie)
    }
}