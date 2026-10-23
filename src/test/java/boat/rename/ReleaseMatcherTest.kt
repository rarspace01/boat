package boat.rename

import boat.info.MediaItem
import boat.info.MediaProxyService
import boat.info.MediaType
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ReleaseMatcherTest {

    private val mediaProxyService: MediaProxyService = mockk(relaxed = true)
    private val matcher = ReleaseMatcher(mediaProxyService)

    private val inception = MediaItem("Inception", "Inception", 2010, MediaType.Movie, "{tmdb-27205}")
    private val breakingBad = MediaItem("Breaking Bad", "Breaking Bad", 2008, MediaType.Series, "{tvdb-81189}")

    @Test
    fun `should search a movie by its name and year`() {
        every { mediaProxyService.search("Inception", 2010, MediaType.Movie) } returns listOf(inception)

        assertThat(matcher.match("Inception.2010.1080p.BluRay.x264-SPARKS", null)).isEqualTo(inception)
    }

    @Test
    fun `should not take another title for the release`() {
        val other = MediaItem("Inception: The Cobol Job", "Inception: The Cobol Job", 2010, MediaType.Movie, "{tmdb-64956}")
        every { mediaProxyService.search(any(), any<Int>(), any()) } returns listOf(other)
        every { mediaProxyService.search(any(), any<MediaType>()) } returns listOf(other)
        every { mediaProxyService.search(any()) } returns listOf(other)

        assertThat(matcher.match("Inception.2010.1080p.BluRay.x264-SPARKS", null)).isNull()
    }

    @Test
    fun `should search a series by the name of the show`() {
        every { mediaProxyService.search("Breaking Bad", MediaType.Series) } returns listOf(breakingBad)

        assertThat(matcher.match("Breaking.Bad.S01.1080p.BluRay.x264-ROVERS", null)).isEqualTo(breakingBad)
        assertThat(matcher.match("Breaking.Bad.S01E05.720p.HDTV.x264", MediaType.Series)).isEqualTo(breakingBad)
    }

    @Test
    fun `should find nothing for an empty name`() {
        assertThat(matcher.match("", null)).isNull()
    }
}
