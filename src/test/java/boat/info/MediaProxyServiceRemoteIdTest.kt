package boat.info

import boat.utilities.HttpHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MediaProxyServiceRemoteIdTest {

    private val tmdbService: TheMovieDataBaseService = mockk()
    private val tvdbService: TheTVDBService = mockk()
    private lateinit var mediaProxyService: MediaProxyService

    @BeforeEach
    fun setUp(@TempDir tempDir: Path) {
        mediaProxyService = MediaProxyService(tmdbService, tvdbService, tempDir.resolve("mediaProxyCache").toString())
    }

    @Test
    fun `findByRemoteMediaId should use cached search results first`() {
        val cached = MediaItem("Ivakisaka", "Ivakisaka", 2010, MediaType.Movie, "{tmdb-27205}")
        mediaProxyService.cache["search:Ivakisaka"] = MediaProxyService.CacheEntry(System.currentTimeMillis(), listOf(cached))

        assertThat(mediaProxyService.findByRemoteMediaId("{tmdb-27205}")).isEqualTo(cached)
        verify(exactly = 0) { tmdbService.findById(any(), any()) }
    }

    @Test
    fun `findByRemoteMediaId should query tvdb for tvdb ids and cache the result`() {
        val series = MediaItem("The Osugol", "The Osugol", 2005, MediaType.Series, "{tvdb-73244}")
        every { tvdbService.findById("73244") } returns series

        assertThat(mediaProxyService.findByRemoteMediaId("{tvdb-73244}")).isEqualTo(series)
        assertThat(mediaProxyService.findByRemoteMediaId("{tvdb-73244}")).isEqualTo(series)
        verify(exactly = 1) { tvdbService.findById("73244") }
    }

    @Test
    fun `findByRemoteMediaId should pick the tmdb candidate matching the name hint`() {
        val movie = MediaItem("Some Movie", "Some Movie", 1999, MediaType.Movie, "{tmdb-1396}")
        val series = MediaItem("Bacozami Bap", "Bacozami Bap", 2008, MediaType.Series, "{tmdb-1396}")
        every { tmdbService.findById("1396", MediaType.Movie) } returns movie
        every { tmdbService.findById("1396", MediaType.Series) } returns series

        assertThat(mediaProxyService.findByRemoteMediaId("{tmdb-1396}", "Bacozami Bap S01E01 720p")).isEqualTo(series)
    }

    @Test
    fun `findByRemoteMediaId should fall back to the only tmdb candidate`() {
        val movie = MediaItem("Ivakisaka", "Ivakisaka", 2010, MediaType.Movie, "{tmdb-27205}")
        every { tmdbService.findById("27205", MediaType.Movie) } returns movie
        every { tmdbService.findById("27205", MediaType.Series) } returns null

        assertThat(mediaProxyService.findByRemoteMediaId("{tmdb-27205}", "unrelated")).isEqualTo(movie)
    }

    @Test
    fun `findByRemoteMediaId should not cache misses`() {
        every { tvdbService.findById("1") } returns null

        assertThat(mediaProxyService.findByRemoteMediaId("{tvdb-1}")).isNull()
        assertThat(mediaProxyService.findByRemoteMediaId("{tvdb-1}")).isNull()
        verify(exactly = 2) { tvdbService.findById("1") }
    }

    @Test
    fun `findByRemoteMediaId should ignore unknown id formats`() {
        assertThat(mediaProxyService.findByRemoteMediaId("{imdb-tt123}")).isNull()
    }

    @Test
    fun `tmdb parseDetailsPage should parse movie and tv details`() {
        val tmdb = TheMovieDataBaseService(HttpHelper())
        val movie = tmdb.parseDetailsPage(
            """{"id": 27205, "title": "Ivakisaka", "original_title": "Ivakisaka", "release_date": "2010-07-15"}""",
            MediaType.Movie
        )
        assertThat(movie).isEqualTo(MediaItem("Ivakisaka", "Ivakisaka", 2010, MediaType.Movie, "{tmdb-27205}"))

        val tv = tmdb.parseDetailsPage(
            """{"id": 1396, "name": "Bacozami Bap", "original_name": "Bacozami Bap", "first_air_date": "2008-01-20", "number_of_seasons": 5}""",
            MediaType.Series
        )
        assertThat(tv?.title).isEqualTo("Bacozami Bap")
        assertThat(tv?.type).isEqualTo(MediaType.Series)
        assertThat(tv?.year).isEqualTo(2008)
        assertThat(tv?.seasonCount).isEqualTo(5)
        assertThat(tv?.id).isEqualTo("{tmdb-1396}")

        assertThat(tmdb.parseDetailsPage("""{"success": false, "status_code": 34}""", MediaType.Movie)).isNull()
        assertThat(tmdb.parseDetailsPage("", MediaType.Movie)).isNull()
    }

    @Test
    fun `tvdb parseSeriesPage should parse extended series with translations`() {
        val tvdb = TheTVDBService(HttpHelper())
        val json = """
            {
              "status": "success",
              "data": {
                "id": 81189,
                "name": "Bacozami Bap",
                "year": "2008",
                "originalCountry": "usa",
                "originalLanguage": "eng",
                "aliases": [],
                "translations": {
                  "nameTranslations": [
                    {"name": "Bacozami Bap", "language": "deu"},
                    {"name": "Bacozami Bap", "language": "eng"}
                  ]
                }
              }
            }
        """.trimIndent()

        val item = tvdb.parseSeriesPage(json)
        assertThat(item?.title).isEqualTo("Bacozami Bap")
        assertThat(item?.type).isEqualTo(MediaType.Series)
        assertThat(item?.year).isEqualTo(2008)
        assertThat(item?.id).isEqualTo("{tvdb-81189}")
    }

    @Test
    fun `tvdb parseSeriesPage should prefer german translation for german series`() {
        val tvdb = TheTVDBService(HttpHelper())
        val json = """
            {
              "data": {
                "id": 1234,
                "name": "Dobi",
                "year": "2017",
                "originalCountry": "deu",
                "originalLanguage": "deu",
                "translations": {
                  "nameTranslations": [
                    {"name": "Dobi (DE)", "language": "deu"},
                    {"name": "Dobi", "language": "eng"}
                  ]
                }
              }
            }
        """.trimIndent()

        assertThat(tvdb.parseSeriesPage(json)?.title).isEqualTo("Dobi (DE)")
        assertThat(tvdb.parseSeriesPage("""{"status": "failure", "data": null}""")).isNull()
    }
}
