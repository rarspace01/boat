package boat.info

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MediaProxyServiceCachingTest {

    private val tmdbService: TheMovieDataBaseService = mockk()
    private val tvdbService: TheTVDBService = mockk()
    private val mediaProxyService = MediaProxyService(tmdbService, tvdbService)

    @Test
    fun `search should cache results`() {
        val name = "Test Movie"
        val results = listOf(MediaItem(name, name, 2020, MediaType.Movie))

        every { tmdbService.search(name) } returns results
        every { tvdbService.search(name) } returns emptyList()

        // First call
        val result1 = mediaProxyService.search(name)
        assertEquals(results, result1)

        // Second call
        val result2 = mediaProxyService.search(name)
        assertEquals(results, result2)

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
        assertEquals(results, result1)

        // Second call
        val result2 = mediaProxyService.search(name, year)
        assertEquals(results, result2)

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

        val results = mediaProxyService.search(query, MediaType.Series)
        assertEquals(listOf(hackRoots, sommerCamp), results)
    }

    @Test
    fun `rankResults should prioritize matching year when provided`() {
        val query = "Inception"
        val item2010 = MediaItem("Inception", "Inception", 2010, MediaType.Movie)
        val item2020 = MediaItem("Inception", "Inception", 2020, MediaType.Movie)

        val ranked = mediaProxyService.rankResults(query, listOf(item2020, item2010), 2010)
        assertEquals(listOf(item2010, item2020), ranked)
    }
}
