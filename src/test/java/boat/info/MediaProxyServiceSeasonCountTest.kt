package boat.info

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

internal class MediaProxyServiceSeasonCountTest {

    private val tmdbService: TheMovieDataBaseService = mockk()
    private val tvdbService: TheTVDBService = mockk()

    private fun service(tempDir: Path) = MediaProxyService(tmdbService, tvdbService, tempDir.resolve("cache").toString())

    @Test
    fun shouldAskTvdbForTheSeasonsOfATvdbSeries(@TempDir tempDir: Path) {
        every { tvdbService.findById("334824") } returns MediaItem("Dark", year = 2017, type = MediaType.Series, id = "{tvdb-334824}").apply { seasonCount = 3 }

        val count = service(tempDir).seasonCountOf(MediaItem("Dark", year = 2017, type = MediaType.Series, id = "{tvdb-334824}"))

        assertThat(count).isEqualTo(3)
    }

    @Test
    fun shouldAskTmdbForTheSeasonsOfATmdbSeries(@TempDir tempDir: Path) {
        every { tmdbService.findById("2316", MediaType.Series) } returns MediaItem("The Office", type = MediaType.Series).apply { seasonCount = 9 }

        assertThat(service(tempDir).seasonCountOf(MediaItem("The Office", type = MediaType.Series, id = "{tmdb-2316}"))).isEqualTo(9)
    }

    @Test
    fun shouldUseTheCountOfTheItemAndNotAskAgainForTheSameSeries(@TempDir tempDir: Path) {
        val service = service(tempDir)
        every { tmdbService.findById("2316", MediaType.Series) } returns MediaItem("The Office", type = MediaType.Series).apply { seasonCount = 9 }
        val office = MediaItem("The Office", type = MediaType.Series, id = "{tmdb-2316}")

        assertThat(service.seasonCountOf(office)).isEqualTo(9)
        assertThat(service.seasonCountOf(MediaItem("The Office", type = MediaType.Series, id = "{tmdb-2316}"))).isEqualTo(9)
        verify(exactly = 1) { tmdbService.findById("2316", MediaType.Series) }

        val known = MediaItem("Known", type = MediaType.Series, id = "{tmdb-1}").apply { seasonCount = 4 }
        assertThat(service.seasonCountOf(known)).isEqualTo(4)
        verify(exactly = 0) { tmdbService.findById("1", any()) }
    }

    @Test
    fun shouldNotAskAgainAfterADayAndKeepNothingOfAFailedLookup(@TempDir tempDir: Path) {
        var now = 1_000_000_000_000L
        val service = service(tempDir).apply { clock = { now } }
        every { tmdbService.findById("2316", MediaType.Series) } returns null andThen MediaItem("The Office", type = MediaType.Series).apply { seasonCount = 9 }
        val office = MediaItem("The Office", type = MediaType.Series, id = "{tmdb-2316}")

        assertThat(service.seasonCountOf(office)).isNull()
        assertThat(service.seasonCountOf(office)).isEqualTo(9)
        now += 25 * 60 * 60 * 1000L
        service.seasonCountOf(office)
        verify(exactly = 3) { tmdbService.findById("2316", MediaType.Series) }
    }

    @Test
    fun shouldHaveNoSeasonsForMoviesItemsWithoutIdAndFailingLookups(@TempDir tempDir: Path) {
        val service = service(tempDir)
        every { tvdbService.findById(any()) } throws RuntimeException("down")

        assertThat(service.seasonCountOf(MediaItem("Dune", type = MediaType.Movie, id = "{tmdb-438631}"))).isNull()
        assertThat(service.seasonCountOf(MediaItem("Dark", type = MediaType.Series))).isNull()
        assertThat(service.seasonCountOf(MediaItem("Dark", type = MediaType.Series, id = "custom"))).isNull()
        assertThat(service.seasonCountOf(MediaItem("Dark", type = MediaType.Series, id = "{tvdb-334824}"))).isNull()
    }
}
