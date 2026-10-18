package boat.info

import boat.utilities.LoggerDelegate
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

@Service
class MediaProxyService(
    private val theMovieDataBaseService: TheMovieDataBaseService,
    private val theTVDBService: TheTVDBService,
    var cacheFileName: String = CACHE_FILE_NAME
) {
    @Autowired
    constructor(
        theMovieDataBaseService: TheMovieDataBaseService,
        theTVDBService: TheTVDBService
    ) : this(theMovieDataBaseService, theTVDBService, CACHE_FILE_NAME)

    companion object {
        const val CACHE_FILE_NAME = "mediaProxyCache"
    }

    private val logger by LoggerDelegate()
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val cacheLock = Any()
    internal val cache = ConcurrentHashMap<String, List<MediaItem>>()
    @Volatile
    private var dirty = false

    init {
        loadCache()
    }

    internal fun loadCache() {
        synchronized(cacheLock) {
            try {
                val file = File(cacheFileName)
                val fileToLoad = if (file.exists() && file.isFile) {
                    file
                } else {
                    val jsonVariant = File("$cacheFileName.json")
                    if (jsonVariant.exists() && jsonVariant.isFile) jsonVariant else null
                }
                if (fileToLoad != null) {
                    val json = fileToLoad.readText()
                    if (json.isNotBlank()) {
                        val type = object : TypeToken<Map<String, List<MediaItem>>>() {}.type
                        val loaded: Map<String, List<MediaItem>>? = gson.fromJson(json, type)
                        if (loaded != null) {
                            cache.putAll(loaded)
                        }
                    }
                }
                dirty = false
            } catch (e: Exception) {
                logger.warn("Failed to load media proxy cache from $cacheFileName", e)
            }
        }
    }

    fun saveCache(force: Boolean = false) {
        synchronized(cacheLock) {
            if (!dirty && !force) return
            try {
                val file = File(cacheFileName)
                file.parentFile?.mkdirs()
                val json = gson.toJson(cache)
                file.writeText(json)
                dirty = false
            } catch (e: Exception) {
                logger.warn("Failed to save media proxy cache to $cacheFileName", e)
            }
        }
    }

    private fun getCachedOrPut(cacheKey: String, compute: () -> List<MediaItem>): List<MediaItem> {
        val cached = cache[cacheKey]
        if (cached != null) {
            return cached
        }
        val computed = compute()
        cache[cacheKey] = computed
        dirty = true
        return computed
    }

    fun search(name: String?): List<MediaItem> {
        if (name.isNullOrBlank()) return emptyList()
        val cacheKey = "search:$name"
        return getCachedOrPut(cacheKey) {
            val tmdbResults = theMovieDataBaseService.search(name) ?: emptyList()
            val tvdbResults = theTVDBService.search(name)
            val combined = tmdbResults.plus(tvdbResults).distinctBy { it.title + it.year + it.type }
            rankResults(name, combined)
        }
    }

    fun findByRemoteMediaId(remoteMediaId: String?): MediaItem? {
        if (remoteMediaId.isNullOrBlank()) return null
        return cache.values.flatten().firstOrNull { it.id == remoteMediaId }
//            if(remoteMediaId.contains("tvdb")){
//                //theTVDBService.findById(remoteMediaId)
//            } else if(remoteMediaId.contains("tmdb")) {
//                //theMovieDataBaseService.findById(remoteMediaId)
//            } else {
//                logger.warn("couldn't find media item with remote media id: $remoteMediaId")
//            }
    }

    fun search(name: String?, year: Int): List<MediaItem> {
        if (name.isNullOrBlank()) return emptyList()
        val cacheKey = "search:$name:$year"
        return getCachedOrPut(cacheKey) {
            val tmdbResults = theMovieDataBaseService.search(name, year) ?: emptyList()
            val tvdbResults = theTVDBService.search(name, year)
            val combined = tmdbResults.plus(tvdbResults).distinctBy { it.title + it.year + it.type }
            rankResults(name, combined, year)
        }
    }

    fun search(name: String?, type: MediaType?): List<MediaItem> {
        if (name.isNullOrBlank()) return emptyList()
        val cacheKey = "search:$name:$type"
        return getCachedOrPut(cacheKey) {
            val results = when (type) {
                MediaType.Movie -> theMovieDataBaseService.search(name)?.filter { it.type == MediaType.Movie } ?: emptyList()
                MediaType.Series -> {
                    val tvdbResults = theTVDBService.search(name)
                    val tmdbResults = theMovieDataBaseService.search(name)?.filter { it.type == MediaType.Series } ?: emptyList()
                    tvdbResults.plus(tmdbResults).distinctBy { it.title + it.year + it.type }
                }
                else -> search(name)
            }
            rankResults(name, results)
        }
    }

    fun search(name: String?, year: Int, type: MediaType?): List<MediaItem> {
        if (name.isNullOrBlank()) return emptyList()
        val cacheKey = "search:$name:$year:$type"
        return getCachedOrPut(cacheKey) {
            val results = when (type) {
                MediaType.Movie -> theMovieDataBaseService.search(name, year)?.filter { it.type == MediaType.Movie } ?: emptyList()
                MediaType.Series -> {
                    val tvdbResults = theTVDBService.search(name, year)
                    val tmdbResults = theMovieDataBaseService.search(name, year)?.filter { it.type == MediaType.Series } ?: emptyList()
                    tvdbResults.plus(tmdbResults).distinctBy { it.title + it.year + it.type }
                }
                else -> search(name, year)
            }
            rankResults(name, results, year)
        }
    }

    internal fun rankResults(query: String, items: List<MediaItem>, year: Int? = null): List<MediaItem> {
        val scored = items.map { item ->
            item to calculateScore(query, item, year)
        }.filter { it.second > 0.0 }
        return scored.sortedByDescending { it.second }.map { it.first }
    }

    private fun normalizeString(input: String): String {
        val acronymPattern = "(?<=(?:^|[^a-zA-Z0-9]))(?:[a-zA-Z][.*]){1,}[a-zA-Z](?:[.*](?=[^a-zA-Z0-9]|$)|(?=[^a-zA-Z0-9]|$))".toRegex()
        val withoutAcronymDelimiters = input.replace(acronymPattern) { matchResult ->
            matchResult.value.replace("*", "").replace(".", "")
        }
        return withoutAcronymDelimiters.lowercase()
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
        if (totalScore <= 0.0) {
            return 0.0
        }

        if (targetYear != null && item.year != null) {
            if (item.year == targetYear) {
                totalScore += 200.0
            } else {
                val diff = abs(item.year - targetYear)
                totalScore -= diff * 20.0
            }
        }

        return maxOf(0.0, totalScore)
    }

    private fun scoreTitle(queryWords: List<String>, normalizedQuery: String, rawTitle: String): Double {
        val normalizedTitle = normalizeString(rawTitle)
        val titleWords = normalizedTitle.split(" ").filter { it.isNotBlank() }
        if (titleWords.isEmpty()) return 0.0

        val matchingWords = queryWords.count { qWord -> titleWords.contains(qWord) }
        val hasSubstringMatch = normalizedTitle.contains(normalizedQuery) || normalizedQuery.contains(normalizedTitle)
        if (matchingWords == 0 && !hasSubstringMatch) {
            return 0.0
        }

        var score = 0.0

        val titleWordCount = titleWords.size
        val queryWordCount = queryWords.size

        // Found words (b) is the most important factor
        score += matchingWords * 1000.0

        // Ratio of matched query words
        val queryMatchRatio = matchingWords.toDouble() / queryWordCount
        score += queryMatchRatio * 500.0

        if (matchingWords == queryWordCount) {
            score += 500.0 // All query words matched in title
        }

        // Exact match
        if (normalizedTitle == normalizedQuery) {
            score += 1000.0
        }

        // Word count comparison:
        // Prefer results that have the same number of words (titleWordCount == matchingWords or titleWordCount == queryWordCount)
        // If a (titleWordCount) and b (matchingWords) is the same, it's a perfect fit.
        // Otherwise penalize higher number of extra words in title (a > b)
        val extraWords = titleWordCount - matchingWords
        if (extraWords == 0) {
            score += 200.0 // Perfect fit: every word in the title was matched
        } else if (extraWords > 0) {
            score -= extraWords * 50.0
        }

        // Substring / prefix bonuses for tie breaking
        if (normalizedTitle.contains(normalizedQuery)) {
            score += 50.0
        }
        if (normalizedQuery.contains(normalizedTitle)) {
            score += 30.0
        }
        if (normalizedTitle.startsWith(normalizedQuery)) {
            score += 20.0
        }

        return maxOf(0.0, score)
    }
}
