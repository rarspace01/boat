package boat.info

import boat.utilities.LoggerDelegate
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.File
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
        private val DIACRITICS_PATTERN = "\\p{InCombiningDiacriticalMarks}+".toRegex()
        private val REMOTE_MEDIA_ID_PATTERN = "\\{?(tmdb|tvdb)-(\\d+)}?".toRegex(RegexOption.IGNORE_CASE)
        private val ACRONYM_PATTERN = "(?<=(?:^|[^a-zA-Z0-9]))(?:[a-zA-Z][.*]){1,}[a-zA-Z](?:[.*](?=[^a-zA-Z0-9]|$)|(?=[^a-zA-Z0-9]|$))".toRegex()
        private val NON_ALPHANUMERIC_PATTERN = "[^a-z0-9]+".toRegex()
        private val TRAILING_YEAR_PATTERN = "^(.*\\S)\\s+\\(?((?:19|20)\\d{2})\\)?$".toRegex()
        private const val SEARCH_KEY_PREFIX = "search:"
        private const val REMOTE_ID_KEY_PREFIX = "id:"
        private val YEAR_SEARCH_KEY_PATTERN = "^yearSearch:(.+):(\\d+):(\\w+)$".toRegex()
        private val SEARCH_WITH_YEAR_KEY_PATTERN = "^search:(.+):(\\d+)$".toRegex()
        private val SEARCH_WITH_TYPE_KEY_PATTERN = "^search:(.+):(\\w+)$".toRegex()
        // TMDB allows about 50 requests per second and 20 connections per IP, TVDB publishes no limit. A cache entry
        // takes up to two requests, so stay well below that.
        const val REFRESH_PARALLELISM = 8
        const val REFRESH_ENTRIES_PER_SECOND = 10

        /**
         * Lowercase title without accents and punctuation, words separated by a single space.
         */
        fun normalizeTitle(input: String): String {
            val withoutAcronymDelimiters = input.replace(ACRONYM_PATTERN) { matchResult ->
                matchResult.value.replace("*", "").replace(".", "")
            }
            // strip accents, so Amelie matches Amélie
            return Normalizer.normalize(withoutAcronymDelimiters.replace("¢", "c").replace("²", "2").replace("³", "3"), Normalizer.Form.NFD)
                .replace(DIACRITICS_PATTERN, "")
                .lowercase()
                .replace(NON_ALPHANUMERIC_PATTERN, " ")
                .trim()
        }
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

    /**
     * Removes all cached media items, in memory and on disk. Returns the number of removed cache entries.
     */
    fun clearCache(): Int {
        synchronized(cacheLock) {
            val removedEntries = cache.size
            cache.clear()
            listOf(File(cacheFileName), File("$cacheFileName.json")).filter { it.isFile }.forEach { file ->
                if (!file.delete()) {
                    logger.warn("Failed to delete media proxy cache file ${file.path}")
                }
            }
            dirty = false
            return removedEntries
        }
    }

    /**
     * Results are cached as returned by the services and ranked when read, so ranking changes apply to cached searches.
     */
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

    /**
     * Searches the services again for every cached search string and remote media id, so the cache keeps its keys but
     * gets current results. An entry keeps its results if the services find nothing for it anymore, so a transient API
     * failure doesn't wipe the cache. The entries are refreshed by [parallelism] threads, starting at most
     * [entriesPerSecond] entries per second to stay within the API rate limits. Returns the number of updated cache entries.
     */
    fun refreshCache(parallelism: Int = REFRESH_PARALLELISM, entriesPerSecond: Int = REFRESH_ENTRIES_PER_SECOND): Int {
        val updatedEntries = AtomicInteger()
        val startIntervalNanos = TimeUnit.SECONDS.toNanos(1) / entriesPerSecond
        val nextStart = AtomicLong(System.nanoTime())
        val executor = Executors.newFixedThreadPool(parallelism)
        try {
            cache.entries.toList().map { (cacheKey, cachedResults) ->
                executor.submit {
                    val waitNanos = nextStart.getAndAdd(startIntervalNanos) - System.nanoTime()
                    if (waitNanos > 0) TimeUnit.NANOSECONDS.sleep(waitNanos)
                    val refreshed = try {
                        fetchForCacheKey(cacheKey, cachedResults)
                    } catch (e: Exception) {
                        logger.warn("Failed to refresh media proxy cache entry $cacheKey", e)
                        null
                    }
                    if (!refreshed.isNullOrEmpty() && refreshed != cachedResults) {
                        cache[cacheKey] = refreshed
                        dirty = true
                        updatedEntries.incrementAndGet()
                    }
                }
            }.forEach { it.get() }
        } finally {
            executor.shutdownNow()
        }
        return updatedEntries.get()
    }

    private fun fetchForCacheKey(cacheKey: String, cachedResults: List<MediaItem>): List<MediaItem>? {
        YEAR_SEARCH_KEY_PATTERN.matchEntire(cacheKey)?.let { match ->
            val (name, year, type) = match.destructured
            return if (isMediaType(type)) fetchByYear(name, year.toInt(), parseMediaType(type)) else null
        }
        if (cacheKey.startsWith(REMOTE_ID_KEY_PREFIX)) {
            val cachedItem = cachedResults.firstOrNull()
            return listOfNotNull(fetchByRemoteMediaId(cacheKey.removePrefix(REMOTE_ID_KEY_PREFIX), cachedItem?.title, cachedItem?.type))
        }
        if (!cacheKey.startsWith(SEARCH_KEY_PREFIX)) return null
        SEARCH_WITH_YEAR_KEY_PATTERN.matchEntire(cacheKey)?.let { match ->
            val (name, year) = match.destructured
            return fetch(name, year.toInt())
        }
        SEARCH_WITH_TYPE_KEY_PATTERN.matchEntire(cacheKey)?.let { match ->
            val (name, type) = match.destructured
            if (isMediaType(type)) return fetch(name, parseMediaType(type))
        }
        return fetch(cacheKey.removePrefix(SEARCH_KEY_PREFIX))
    }

    // cache keys hold the media type as its name or "null"
    private fun isMediaType(type: String) = type == "null" || parseMediaType(type) != null

    private fun parseMediaType(type: String) = MediaType.entries.firstOrNull { it.name == type }

    fun search(name: String?): List<MediaItem> {
        if (name.isNullOrBlank()) return emptyList()
        val cacheKey = "$SEARCH_KEY_PREFIX$name"
        val results = getCachedOrPut(cacheKey) { fetch(name) }
        return rankResults(name, results)
    }

    private fun fetch(name: String): List<MediaItem> {
        val tmdbResults = theMovieDataBaseService.search(name) ?: emptyList()
        val tvdbResults = theTVDBService.search(name)
        return tmdbResults.plus(tvdbResults).distinctBy { it.title + it.year + it.type }
    }

    /**
     * Searches user input like "Predators 2025". TMDB and TVDB treat a year in the query as part of the title, so a
     * trailing year is also searched as release year of the name before it. The input as typed is still searched, for
     * titles like "Blade Runner 2049".
     */
    fun searchWithTrailingYear(query: String?): List<MediaItem> {
        if (query.isNullOrBlank()) return emptyList()
        val match = TRAILING_YEAR_PATTERN.find(query.trim()) ?: return search(query)
        val name = match.groupValues[1]
        val year = match.groupValues[2].toInt()
        val yearResults = search(name, year, MediaType.Movie) + search(name, year, MediaType.Series)
        return (yearResults + search(query)).distinctBy { it.title + it.year + it.type }
    }

    /**
     * Resolves a remote media id like `{tmdb-27205}` or `{tvdb-81189}`. TMDB ids are only unique per
     * media type, so [mediaType] restricts the lookup if known. Otherwise, if both a movie and a series
     * match, [nameHint] (e.g. the torrent name) picks one.
     */
    fun findByRemoteMediaId(remoteMediaId: String?, nameHint: String? = null, mediaType: MediaType? = null): MediaItem? {
        if (remoteMediaId.isNullOrBlank()) return null
        cache.values.flatten().firstOrNull { it.id == remoteMediaId && (mediaType == null || it.type == mediaType) }?.let { return it }
        val mediaItem = fetchByRemoteMediaId(remoteMediaId, nameHint, mediaType) ?: return null
        // only cache hits, so a transient API failure doesn't stick
        cache["$REMOTE_ID_KEY_PREFIX$remoteMediaId"] = listOf(mediaItem)
        dirty = true
        return mediaItem
    }

    private fun fetchByRemoteMediaId(remoteMediaId: String, nameHint: String?, mediaType: MediaType?): MediaItem? {
        val match = REMOTE_MEDIA_ID_PATTERN.find(remoteMediaId.trim())
        if (match == null) {
            logger.warn("couldn't parse remote media id: $remoteMediaId")
            return null
        }
        val (provider, id) = match.destructured
        val mediaItem = when (provider.lowercase()) {
            "tvdb" -> theTVDBService.findById(id)
            else -> {
                val typesToQuery = mediaType?.let { listOf(it) } ?: listOf(MediaType.Movie, MediaType.Series)
                val candidates = typesToQuery.mapNotNull { theMovieDataBaseService.findById(id, it) }
                if (candidates.size > 1 && !nameHint.isNullOrBlank()) {
                    rankResults(nameHint, candidates).firstOrNull() ?: candidates.first()
                } else {
                    candidates.firstOrNull()
                }
            }
        }
        if (mediaItem == null) {
            logger.warn("couldn't find media item with remote media id: $remoteMediaId")
        }
        return mediaItem
    }

    fun search(name: String?, year: Int): List<MediaItem> {
        if (name.isNullOrBlank()) return emptyList()
        val cacheKey = "$SEARCH_KEY_PREFIX$name:$year"
        val results = getCachedOrPut(cacheKey) { fetch(name, year) }
        return rankResults(name, results, year)
    }

    private fun fetch(name: String, year: Int): List<MediaItem> {
        val tmdbResults = theMovieDataBaseService.search(name, year) ?: emptyList()
        val tvdbResults = theTVDBService.search(name, year)
        return tmdbResults.plus(tvdbResults).distinctBy { it.title + it.year + it.type }
    }

    fun search(name: String?, type: MediaType?): List<MediaItem> {
        if (name.isNullOrBlank()) return emptyList()
        val cacheKey = "$SEARCH_KEY_PREFIX$name:$type"
        val results = getCachedOrPut(cacheKey) { fetch(name, type) }
        return rankResults(name, results)
    }

    private fun fetch(name: String, type: MediaType?): List<MediaItem> = when (type) {
        MediaType.Movie -> theMovieDataBaseService.search(name)?.filter { it.type == MediaType.Movie } ?: emptyList()
        MediaType.Series -> {
            val tvdbResults = theTVDBService.search(name)
            val tmdbResults = theMovieDataBaseService.search(name)?.filter { it.type == MediaType.Series } ?: emptyList()
            tvdbResults.plus(tmdbResults).distinctBy { it.title + it.year + it.type }
        }
        else -> fetch(name)
    }

    fun search(name: String?, year: Int, type: MediaType?): List<MediaItem> {
        if (name.isNullOrBlank()) return emptyList()
        // typed TMDB searches filter by year, they don't share cache entries with the former search/multi results nor
        // with the former typed searches, which were cached after ranking and so missed titles like "Self/less"
        val cacheKey = "yearSearch:$name:$year:$type"
        val results = getCachedOrPut(cacheKey) { fetchByYear(name, year, type) }
        val ranked = rankResults(name, results, year)
        // TMDB also finds movies by alternative titles ("I Spit on Your Grave" is "Day of the Woman"), they come after the
        // ranked ones. Short names like "Dune" or "nde" match too many unrelated titles.
        val isDistinctiveName = name.count { it.isLetter() } >= 8
        return if (type == MediaType.Movie && isDistinctiveName) ranked + results.filter { it !in ranked } else ranked
    }

    private fun fetchByYear(name: String, year: Int, type: MediaType?): List<MediaItem> = when (type) {
        MediaType.Movie -> theMovieDataBaseService.search(name, year, MediaType.Movie) ?: emptyList()
        MediaType.Series -> {
            val tvdbResults = theTVDBService.search(name, year)
            val tmdbResults = theMovieDataBaseService.search(name, year, MediaType.Series) ?: emptyList()
            tvdbResults.plus(tmdbResults).distinctBy { it.title + it.year + it.type }
        }
        else -> fetch(name, year)
    }

    internal fun rankResults(query: String, items: List<MediaItem>, year: Int? = null): List<MediaItem> {
        val scored = items.map { item ->
            item to calculateScore(query, item, year)
        }.filter { it.second > 0.0 }
        return scored.sortedByDescending { it.second }.map { it.first }
    }

    private fun calculateScore(query: String, item: MediaItem, targetYear: Int? = null): Double {
        val normalizedQuery = normalizeTitle(query)
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
        val normalizedTitle = normalizeTitle(rawTitle)
        val titleWords = normalizedTitle.split(" ").filter { it.isNotBlank() }
        if (titleWords.isEmpty()) return 0.0

        val matchingWords = queryWords.count { qWord -> titleWords.contains(qWord) }
        // titles written apart or together, e.g. "Self/less" for "Selfless"
        val isSameWithoutSpaces = normalizedTitle.replace(" ", "") == normalizedQuery.replace(" ", "")
        val hasSubstringMatch = normalizedTitle.contains(normalizedQuery) || normalizedQuery.contains(normalizedTitle) || isSameWithoutSpaces
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
        if (normalizedTitle == normalizedQuery || isSameWithoutSpaces) {
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
