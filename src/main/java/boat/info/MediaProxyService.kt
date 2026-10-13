package boat.info

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

@Service
class MediaProxyService @Autowired constructor(
    private val theMovieDataBaseService: TheMovieDataBaseService,
    private val theTVDBService: TheTVDBService
) {

    private val cache = ConcurrentHashMap<String, List<MediaItem>>()

    fun search(name: String?): List<MediaItem> {
        if (name.isNullOrBlank()) return emptyList()
        val cacheKey = "search:$name"
        return cache.getOrPut(cacheKey) {
            val tmdbResults = theMovieDataBaseService.search(name) ?: emptyList()
            val tvdbResults = theTVDBService.search(name)
            val combined = tmdbResults.plus(tvdbResults).distinctBy { it.title + it.year + it.type }
            rankResults(name, combined)
        }
    }

    fun search(name: String?, year: Int): List<MediaItem> {
        if (name.isNullOrBlank()) return emptyList()
        val cacheKey = "search:$name:$year"
        return cache.getOrPut(cacheKey) {
            val tmdbResults = theMovieDataBaseService.search(name, year) ?: emptyList()
            val tvdbResults = theTVDBService.search(name, year)
            val combined = tmdbResults.plus(tvdbResults).distinctBy { it.title + it.year + it.type }
            rankResults(name, combined, year)
        }
    }

    fun search(name: String?, type: MediaType?): List<MediaItem> {
        if (name.isNullOrBlank()) return emptyList()
        val cacheKey = "search:$name:$type"
        return cache.getOrPut(cacheKey) {
            val results = when (type) {
                MediaType.Movie -> theMovieDataBaseService.search(name) ?: emptyList()
                MediaType.Series -> theTVDBService.search(name)
                else -> search(name)
            }
            rankResults(name, results)
        }
    }

    fun search(name: String?, year: Int, type: MediaType?): List<MediaItem> {
        if (name.isNullOrBlank()) return emptyList()
        val cacheKey = "search:$name:$year:$type"
        return cache.getOrPut(cacheKey) {
            val results = when (type) {
                MediaType.Movie -> theMovieDataBaseService.search(name, year) ?: emptyList()
                MediaType.Series -> theTVDBService.search(name, year)
                else -> search(name, year)
            }
            rankResults(name, results, year)
        }
    }

    internal fun rankResults(query: String, items: List<MediaItem>, year: Int? = null): List<MediaItem> {
        if (items.size <= 1) return items
        return items.sortedByDescending { item ->
            calculateScore(query, item, year)
        }
    }

    private fun normalizeString(input: String): String {
        return input.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    private fun calculateScore(query: String, item: MediaItem, targetYear: Int? = null): Double {
        val normalizedQuery = normalizeString(query)
        val queryWords = normalizedQuery.split(" ").filter { it.isNotBlank() }
        if (queryWords.isEmpty()) return 0.0

        val titleScore = scoreTitle(queryWords, normalizedQuery, item.title)
        val originalTitleScore = item.originalTitle?.let { scoreTitle(queryWords, normalizedQuery, it) } ?: 0.0
        var totalScore = maxOf(titleScore, originalTitleScore)

        if (targetYear != null && item.year != null) {
            if (item.year == targetYear) {
                totalScore += 200.0
            } else {
                val diff = abs(item.year - targetYear)
                totalScore -= diff * 20.0
            }
        }

        return totalScore
    }

    private fun scoreTitle(queryWords: List<String>, normalizedQuery: String, rawTitle: String): Double {
        val normalizedTitle = normalizeString(rawTitle)
        val titleWords = normalizedTitle.split(" ").filter { it.isNotBlank() }
        if (titleWords.isEmpty()) return 0.0

        var score = 0.0

        // Exact match
        if (normalizedTitle == normalizedQuery) {
            score += 1000.0
        }

        // Substring matches
        if (normalizedTitle.contains(normalizedQuery)) {
            score += 150.0
        }
        if (normalizedQuery.contains(normalizedTitle)) {
            score += 100.0
        }
        if (normalizedTitle.startsWith(normalizedQuery)) {
            score += 50.0
        }

        // Word overlap
        val matchingWords = queryWords.count { qWord -> titleWords.contains(qWord) }
        score += matchingWords * 100.0

        val queryMatchRatio = matchingWords.toDouble() / queryWords.size
        score += queryMatchRatio * 200.0

        if (matchingWords == queryWords.size) {
            score += 300.0 // All query words matched in title
        }

        // Penalty for extra words in title that weren't in query
        val extraWords = titleWords.size - matchingWords
        if (extraWords > 0) {
            score -= extraWords * 10.0
        }

        return score
    }
}
