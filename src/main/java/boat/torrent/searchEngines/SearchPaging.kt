package boat.torrent.searchEngines

import boat.torrent.Torrent
import boat.utilities.PropertiesHelper

/**
 * Search engines list more results on further pages. They are fetched until [maxResultsPerEngine] torrents are
 * there, the engine has no more or only repeats itself, or the results got to torrents without seeders (the engines
 * sort by seeders, nothing alive follows).
 */
internal object SearchPaging {
    const val DEFAULT_MAX_RESULTS_PER_ENGINE = 500
    private const val MAX_PAGES = 20

    /** `SEARCH_MAX_RESULTS_PER_ENGINE` in boat.cfg. */
    val maxResultsPerEngine: Int
        get() = PropertiesHelper.getProperty("SEARCH_MAX_RESULTS_PER_ENGINE")?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            ?: DEFAULT_MAX_RESULTS_PER_ENGINE

    /**
     * @param fetchPage the torrents of a page, the first page is 1. An empty list ends the search.
     */
    fun collect(maxResults: Int = maxResultsPerEngine, fetchPage: (page: Int) -> List<Torrent>): List<Torrent> {
        val collected = LinkedHashMap<String, Torrent>()
        for (page in 1..MAX_PAGES) {
            val torrents = fetchPage(page)
            val before = collected.size
            torrents.forEach { collected.putIfAbsent(it.torrentId.ifBlank { it.magnetUri.ifBlank { it.name } }, it) }
            val isNothingNew = collected.size == before
            val reachedDeadTorrents = torrents.lastOrNull()?.let { it.seeder <= 0 } ?: true
            if (isNothingNew || reachedDeadTorrents || collected.size >= maxResults) break
        }
        return collected.values.take(maxResults)
    }
}
