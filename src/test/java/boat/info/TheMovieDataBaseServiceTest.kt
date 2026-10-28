package boat.info

import boat.utilities.HttpHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@Tag("live")
internal class TheMovieDataBaseServiceTest {
    private var tmdbs: TheMovieDataBaseService? = null
    @BeforeEach
    fun beforeMethod() {
        tmdbs = TheMovieDataBaseService(HttpHelper())
    }

    @Test
    fun search() {
        // Given
        // When
        val mediaItems = tmdbs!!.search("Planet")
        // Then
        assertThat(mediaItems).isNotNull
        assertThat(mediaItems!!.size).isGreaterThan(0)
    }

    @Test
    fun searchEmpty() {
        // Given
        // When
        val mediaItems = tmdbs!!.search("blueasablue")
        // Then
        assertThat(mediaItems).isNotNull
        assertThat(mediaItems).isEmpty()
    }
}
