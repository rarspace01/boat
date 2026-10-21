package boat.info

import boat.utilities.HttpHelper
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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
    fun `search should match titles regardless of accents and spaces`() {
        val selfless = MediaItem("Self/less", "Self/less", 2015, MediaType.Movie)
        val amelie = MediaItem("Amélie", "Le Fabuleux Destin d'Amélie Poulain", 2001, MediaType.Movie)
        val liebeAmelie = MediaItem("Liebe Amelie", "Liebe Amelie", 2005, MediaType.Movie)

        assertThat(mediaProxyService.rankResults("Selfless", listOf(selfless))).containsExactly(selfless)
        assertThat(mediaProxyService.rankResults("Amelie", listOf(liebeAmelie, amelie))).containsExactly(amelie, liebeAmelie)
    }

    @Test
    fun `movie search with year should keep movies found by alternative titles`() {
        val dayOfTheWoman = MediaItem("Day of the Woman", "Day of the Woman", 1978, MediaType.Movie)
        val iSpit = MediaItem("I Spit on Your Grave", "I Spit on Your Grave", 2010, MediaType.Movie)
        every { tmdbService.search("I Spit on Your Grave", 1978, MediaType.Movie) } returns listOf(dayOfTheWoman, iSpit)

        assertThat(mediaProxyService.search("I Spit on Your Grave", 1978, MediaType.Movie)).containsExactly(iSpit, dayOfTheWoman)

        every { tmdbService.search("Dune", 2000, MediaType.Movie) } returns listOf(MediaItem("The Story of a Rose", null, 2000, MediaType.Movie))
        assertThat(mediaProxyService.search("Dune", 2000, MediaType.Movie)).isEmpty()
    }

    @Test
    fun `search with trailing year should find titles released in that year`() {
        val documentary = MediaItem("Predators", "Predators", 2025, MediaType.Movie)
        val movie = MediaItem("Predators", "Predators", 2010, MediaType.Movie)
        every { tmdbService.search("Predators", 2025, MediaType.Movie) } returns listOf(documentary)
        every { tmdbService.search("Predators", 2025, MediaType.Series) } returns emptyList()
        every { tvdbService.search("Predators", 2025) } returns emptyList()
        // the search apis take the year as part of the title
        every { tmdbService.search("Predators 2025") } returns emptyList()
        every { tvdbService.search("Predators 2025") } returns emptyList()
        every { tmdbService.search("Predators (2025)") } returns emptyList()
        every { tvdbService.search("Predators (2025)") } returns emptyList()

        assertThat(mediaProxyService.searchWithTrailingYear("Predators 2025")).containsExactly(documentary)
        assertThat(mediaProxyService.searchWithTrailingYear("Predators (2025)")).containsExactly(documentary)

        every { tmdbService.search("Predators") } returns listOf(movie, documentary)
        every { tvdbService.search("Predators") } returns emptyList()
        assertThat(mediaProxyService.searchWithTrailingYear("Predators")).containsExactlyInAnyOrder(movie, documentary)
    }

    @Test
    fun `search with trailing year should keep titles ending with a number`() {
        val bladeRunner = MediaItem("Blade Runner 2049", "Blade Runner 2049", 2017, MediaType.Movie)
        every { tmdbService.search("Blade Runner", 2049, MediaType.Movie) } returns emptyList()
        every { tmdbService.search("Blade Runner", 2049, MediaType.Series) } returns emptyList()
        every { tvdbService.search("Blade Runner", 2049) } returns emptyList()
        every { tmdbService.search("Blade Runner 2049") } returns listOf(bladeRunner)
        every { tvdbService.search("Blade Runner 2049") } returns emptyList()

        assertThat(mediaProxyService.searchWithTrailingYear("Blade Runner 2049")).containsExactly(bladeRunner)

        val movie2012 = MediaItem("2012", "2012", 2009, MediaType.Movie)
        every { tmdbService.search("2012") } returns listOf(movie2012)
        every { tvdbService.search("2012") } returns emptyList()
        assertThat(mediaProxyService.searchWithTrailingYear("2012")).containsExactly(movie2012)
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

    @Test
    fun `clearCache should remove cached results in memory and on disk`(@TempDir tempDir: Path) {
        val cacheFile = tempDir.resolve("clearCache").toString()
        val service = MediaProxyService(tmdbService, tvdbService, cacheFile)
        val name = "Predators"
        every { tmdbService.search(name) } returns listOf(MediaItem(name, name, 2010, MediaType.Movie))
        every { tvdbService.search(name) } returns emptyList()
        service.search(name)
        service.saveCache()
        assertThat(File(cacheFile)).exists()

        assertThat(service.clearCache()).isEqualTo(1)

        assertThat(service.cache).isEmpty()
        assertThat(File(cacheFile)).doesNotExist()
        service.search(name)
        verify(exactly = 2) { tmdbService.search(name) }
    }

    @Test
    fun `refreshCache should search the cached search strings again and update their results`(@TempDir tempDir: Path) {
        val cacheFile = tempDir.resolve("refreshCache").toString()
        val service = MediaProxyService(tmdbService, tvdbService, cacheFile)
        val oldMovie = MediaItem("Predators", "Predators", 2010, MediaType.Movie)
        val newMovie = MediaItem("Predators", "Predators", 2010, MediaType.Movie, "{tmdb-34851}")
        val documentary = MediaItem("Predators", "Predators", 2025, MediaType.Movie, "{tmdb-1}")
        val oldDocumentary = MediaItem("Predators", "Predators", 2025, MediaType.Movie)
        val oldSeries = MediaItem("Dark", "Dark", 2017, MediaType.Series)
        val newSeries = MediaItem("Dark", "Dark", 2017, MediaType.Series, "{tvdb-334824}")
        val oldIdMovie = MediaItem("Inception", "Inception", 2010, MediaType.Movie, "{tmdb-27205}")
        val newIdMovie = MediaItem("Inception", "Inception (updated)", 2010, MediaType.Movie, "{tmdb-27205}")
        val kept = MediaItem("Doug", "Doug", 1991, MediaType.Series)
        every { tmdbService.search("Predators") } returns listOf(oldMovie)
        every { tvdbService.search("Predators") } returns emptyList()
        every { tmdbService.search("Predators", 2025, MediaType.Movie) } returns listOf(oldDocumentary)
        every { tvdbService.search("Dark") } returns listOf(oldSeries)
        every { tmdbService.search("Dark") } returns emptyList()
        every { tmdbService.findById("27205", MediaType.Movie) } returns oldIdMovie
        every { tmdbService.findById("27205", MediaType.Series) } returns null
        every { tvdbService.search("Doug", 1991) } returns listOf(kept)
        every { tmdbService.search("Doug", 1991) } returns emptyList()
        service.search("Predators")
        service.search("Predators", 2025, MediaType.Movie)
        service.search("Dark", MediaType.Series)
        service.findByRemoteMediaId("{tmdb-27205}")
        service.search("Doug", 1991)
        service.saveCache()
        val cacheKeys = service.cache.keys.toSet()

        every { tmdbService.search("Predators") } returns listOf(newMovie, documentary)
        every { tmdbService.search("Predators", 2025, MediaType.Movie) } returns listOf(documentary)
        every { tvdbService.search("Dark") } returns listOf(newSeries)
        every { tmdbService.findById("27205", MediaType.Movie) } returns newIdMovie
        // a failing api finds nothing, the cached results stay
        every { tvdbService.search("Doug", 1991) } returns emptyList()

        assertThat(service.refreshCache(parallelism = 3, entriesPerSecond = 100)).isEqualTo(4)
        service.saveCache()

        assertThat(service.cache.keys).isEqualTo(cacheKeys)
        val reloaded = MediaProxyService(mockk(), mockk(), cacheFile)
        assertThat(reloaded.search("Predators")).containsExactlyInAnyOrder(newMovie, documentary)
        assertThat(reloaded.search("Predators", 2025, MediaType.Movie)).containsExactly(documentary)
        assertThat(reloaded.search("Dark", MediaType.Series)).containsExactly(newSeries)
        assertThat(reloaded.findByRemoteMediaId("{tmdb-27205}")).isEqualTo(newIdMovie)
        assertThat(reloaded.search("Doug", 1991)).containsExactly(kept)
        // the refresh looks up the remote id with the type of the cached item
        verify(exactly = 2) { tmdbService.findById("27205", MediaType.Movie) }
        verify(exactly = 1) { tmdbService.findById("27205", MediaType.Series) }
    }

    @Test
    fun `refreshCache should refresh entries in parallel within the given limits`() {
        val names = (1..6).map { "Movie $it" }
        names.forEach { mediaProxyService.cache["search:$it"] = listOf(MediaItem(it, it, 2000, MediaType.Movie)) }
        val runningSearches = AtomicInteger()
        val maxRunningSearches = AtomicInteger()
        every { tmdbService.search(any<String>()) } answers {
            maxRunningSearches.accumulateAndGet(runningSearches.incrementAndGet(), ::maxOf)
            Thread.sleep(200)
            runningSearches.decrementAndGet()
            listOf(MediaItem(firstArg(), firstArg(), 2001, MediaType.Movie))
        }
        every { tvdbService.search(any<String>()) } returns emptyList()

        assertThat(mediaProxyService.refreshCache(parallelism = 3, entriesPerSecond = 1000)).isEqualTo(6)
        assertThat(maxRunningSearches.get()).isEqualTo(3)
        names.forEach { assertThat(mediaProxyService.cache["search:$it"]).containsExactly(MediaItem(it, it, 2001, MediaType.Movie)) }

        // 6 entries at 20 per second start over at least 250ms, even if the searches return at once
        every { tmdbService.search(any<String>()) } returns emptyList()
        val start = System.nanoTime()
        mediaProxyService.refreshCache(parallelism = 6, entriesPerSecond = 20)
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isGreaterThanOrEqualTo(250)
    }

    /**
     * Refreshes the local media proxy cache file of a boat installation (run from its working directory) against the
     * live TMDB and TVDB apis.
     */
    @Disabled
    @Test
    fun refreshLocalMediaProxyCache() {
        val httpHelper = HttpHelper()
        val service = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        println("refreshing ${service.cache.size} cache entries of ${File(service.cacheFileName).absolutePath}")
        println("updated ${service.refreshCache()} cache entries")
        service.saveCache()
    }

    @Test
    fun `searches without result should not be cached or saved`(@TempDir tempDir: Path) {
        val cacheFile = tempDir.resolve("emptyResults").toString()
        val service = MediaProxyService(tmdbService, tvdbService, cacheFile)
        every { tmdbService.search("Nothing") } returns emptyList()
        every { tvdbService.search("Nothing") } returns emptyList()

        assertThat(service.search("Nothing")).isEmpty()
        service.saveCache(force = true)

        assertThat(service.cache).isEmpty()
        assertThat(MediaProxyService(tmdbService, tvdbService, cacheFile).cache).isEmpty()
    }

    @Test
    fun `searches without result should only be remembered for a short time`() {
        val service = MediaProxyService(tmdbService, tvdbService, "unusedCacheFile")
        every { tmdbService.search("Nothing") } returns emptyList()
        every { tvdbService.search("Nothing") } returns emptyList()

        service.search("Nothing")
        service.search("Nothing")

        verify(exactly = 1) { tmdbService.search("Nothing") }
    }

    @Test
    fun `cache should not grow beyond the maximum`() {
        val service = MediaProxyService(tmdbService, tvdbService, "unusedCacheFile")
        service.maxCacheEntries = 5
        (1..20).forEach { index ->
            every { tmdbService.search("Movie $index") } returns listOf(MediaItem("Movie $index", year = 2000, type = MediaType.Movie))
            every { tvdbService.search("Movie $index") } returns emptyList()
            service.search("Movie $index")
        }

        assertThat(service.cache).hasSize(5)
        assertThat(service.cache.keys).contains("search:Movie 20")
    }
}
