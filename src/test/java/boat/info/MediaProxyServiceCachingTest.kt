package boat.info

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class MediaProxyServiceCachingTest {

    private val tmdbService: TheMovieDataBaseService = mockk()
    private val tvdbService: TheTVDBService = mockk()
    private lateinit var mediaProxyService: MediaProxyService

    @BeforeEach
    fun setUp(@TempDir tempDir: Path) {
        clearMocks(tmdbService, tvdbService)
        val tempCachePath = tempDir.resolve("mediaProxyCache").toString()
        mediaProxyService = MediaProxyService(tmdbService, tvdbService, tempCachePath)
    }

    @Test
    fun `search should cache results`() {
        val name = "Test Movie"
        val results = listOf(MediaItem(name, name, 2020, MediaType.Movie))

        every { tmdbService.search(name) } returns results
        every { tvdbService.search(name) } returns emptyList()

        // First call
        val result1 = mediaProxyService.search(name)
        assertThat(result1).isEqualTo(results)

        // Second call
        val result2 = mediaProxyService.search(name)
        assertThat(result2).isEqualTo(results)

        // Verify services were called only once
        verify(exactly = 1) { tmdbService.search(name) }
        verify(exactly = 1) { tvdbService.search(name) }
    }

    @Test
    fun `search with year should cache results`() {
        val name = "Test Movie"
        val year = 2020
        val results = listOf(MediaItem(name, name, year, MediaType.Movie))

        every { tmdbService.search(name, year) } returns results
        every { tvdbService.search(name, year) } returns emptyList()

        // First call
        val result1 = mediaProxyService.search(name, year)
        assertThat(result1).isEqualTo(results)

        // Second call
        val result2 = mediaProxyService.search(name, year)
        assertThat(result2).isEqualTo(results)

        // Verify services were called only once
        verify(exactly = 1) { tmdbService.search(name, year) }
        verify(exactly = 1) { tvdbService.search(name, year) }
    }

    @Test
    fun `search should rank items by query relevance`() {
        val query = "hack roots"
        val sommerCamp = MediaItem("SommerCamp – Back to the Roots", "SommerCamp – Back to the Roots", 2020, MediaType.Series)
        val hackRoots = MediaItem(".hack//Roots", ".hack//Roots", 2006, MediaType.Series)

        every { tvdbService.search(query) } returns listOf(sommerCamp, hackRoots)
        every { tmdbService.search(query) } returns emptyList()

        val results = mediaProxyService.search(query, MediaType.Series)
        assertThat(results).isEqualTo(listOf(hackRoots, sommerCamp))
    }

    @Test
    fun `rankResults should prioritize matching year when provided`() {
        val query = "Inception"
        val item2010 = MediaItem("Inception", "Inception", 2010, MediaType.Movie)
        val item2020 = MediaItem("Inception", "Inception", 2020, MediaType.Movie)

        val ranked = mediaProxyService.rankResults(query, listOf(item2020, item2010), 2010)
        assertThat(ranked).isEqualTo(listOf(item2010, item2020))
    }

    @Test
    fun `rankResults should prefer exact word count match over extra words in title`() {
        val query = "MASH"
        val mashUp = MediaItem("Mash-Up!", "Mash-Up!", null, MediaType.Series)
        val mashReport = MediaItem("The Mash Report", "The Mash Report", 2017, MediaType.Series)
        val mash = MediaItem("M*A*S*H", "M*A*S*H", 1972, MediaType.Series)
        val sportsMash = MediaItem("Sports Mash: Taking the Mic", "Sports Mash: Taking the Mic", 2010, MediaType.Series)

        val ranked = mediaProxyService.rankResults(query, listOf(sportsMash, mashReport, mashUp, mash))
        assertThat(ranked).isEqualTo(listOf(mash, mashUp, mashReport, sportsMash))
    }

    @Test
    fun `rankResults should prioritize found words first then closer word count`() {
        val query = "Doctor Who"
        val exact = MediaItem("Doctor Who", "Doctor Who", 2005, MediaType.Series)
        val extraWord = MediaItem("Doctor Who Classic", "Doctor Who Classic", 1963, MediaType.Series)
        val moreExtraWords = MediaItem("Doctor Who The Movie", "Doctor Who The Movie", 1996, MediaType.Movie)
        val partialMatchFewerWords = MediaItem("The Doctor", "The Doctor", 2020, MediaType.Series)

        val ranked = mediaProxyService.rankResults(query, listOf(partialMatchFewerWords, moreExtraWords, extraWord, exact))
        assertThat(ranked).isEqualTo(listOf(exact, extraWord, moreExtraWords, partialMatchFewerWords))
    }

    @Test
    fun `rankResults should filter out completely irrelevant results with no word or substring match`() {
        val query = "DOUG"
        val hongGilDong = MediaItem("Hong Gil Dong (1998)", "홍길동", 1998, MediaType.Series)
        val doug = MediaItem("Doug", "Doug", 1991, MediaType.Series)

        val rankedWithYear = mediaProxyService.rankResults(query, listOf(hongGilDong), 1998)
        assertThat(rankedWithYear).isEmpty()

        val rankedCombined = mediaProxyService.rankResults(query, listOf(hongGilDong, doug), 1998)
        assertThat(rankedCombined).isEqualTo(listOf(doug))
    }

    @Test
    fun `search should persist cache to file and reload on new service instance`(@TempDir tempDir: Path) {
        val cacheFile = tempDir.resolve("testMediaProxyCache").toFile()
        val cachePath = cacheFile.absolutePath

        val tmdb1: TheMovieDataBaseService = mockk()
        val tvdb1: TheTVDBService = mockk()
        val serviceInstance1 = MediaProxyService(tmdb1, tvdb1, cachePath)

        val movieName = "Interstellar"
        val movieResults = listOf(MediaItem(movieName, movieName, 2014, MediaType.Movie))
        every { tmdb1.search(movieName) } returns movieResults
        every { tvdb1.search(movieName) } returns emptyList()

        val seriesName = "Breaking Bad"
        val seriesResults = listOf(MediaItem(seriesName, seriesName, 2008, MediaType.Series))
        every { tvdb1.search(seriesName) } returns seriesResults
        every { tmdb1.search(seriesName) } returns emptyList()

        val result1 = serviceInstance1.search(movieName)
        val result2 = serviceInstance1.search(seriesName, MediaType.Series)
        serviceInstance1.saveCache()

        assertThat(result1).isEqualTo(movieResults)
        assertThat(result2).isEqualTo(seriesResults)
        assertThat(cacheFile).exists()
        assertThat(cacheFile.length()).isGreaterThan(0)

        verify(exactly = 1) { tmdb1.search(movieName) }
        verify(exactly = 1) { tvdb1.search(seriesName) }
        verify(exactly = 1) { tmdb1.search(seriesName) }

        // Second service instance pointing to the same cache file
        val tmdb2: TheMovieDataBaseService = mockk()
        val tvdb2: TheTVDBService = mockk()
        val serviceInstance2 = MediaProxyService(tmdb2, tvdb2, cachePath)

        val loadedResult1 = serviceInstance2.search(movieName)
        val loadedResult2 = serviceInstance2.search(seriesName, MediaType.Series)

        assertThat(loadedResult1).isEqualTo(movieResults)
        assertThat(loadedResult2).isEqualTo(seriesResults)

        // No external calls on serviceInstance2
        verify(exactly = 0) { tmdb2.search(any()) }
        verify(exactly = 0) { tmdb2.search(any(), any()) }
        verify(exactly = 0) { tvdb2.search(any()) }
        verify(exactly = 0) { tvdb2.search(any(), any()) }
    }
}
