package boat.torrent

import boat.multifileHoster.MultifileHosterService
import boat.torrent.searchEngines.*
import boat.utilities.HttpHelper
import boat.utilities.LoggerDelegate
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ForkJoinPool
import java.util.function.Consumer
import java.util.stream.Collectors

@Service
class TorrentSearchEngineService(
    final val httpHelper: HttpHelper,
    val multifileHosterService: MultifileHosterService,
    val torrentInfoService: TorrentInfoService,
    searchEngines: List<TorrentSearchEngine>? = null
) {

    companion object {
        private val logger by LoggerDelegate()
        const val DUMMY_SEEDERS_OF_CACHED_TORRENT = 1

        /** The most results a search returns, after everything is filtered and sorted. */
        const val MAX_SEARCH_RESULTS = 100

        /** The engines search at once and some fetch the pages of their torrents in parallel (in the same pool). */
        const val SEARCH_PARALLELISM = 32
    }

    // one pool for all searches, the parallel streams of the engines run in it too instead of the small common pool
    private val searchPool = ForkJoinPool(SEARCH_PARALLELISM)

    private val allSearchEngines: List<TorrentSearchEngine> = searchEngines ?: listOf<TorrentSearchEngine>(
        AnyBT(httpHelper),
        EZTV(httpHelper),
        ExtraTorrent(httpHelper),
        Kat(httpHelper),
        Knaben(httpHelper),
        LimeTorrents(httpHelper),
        NyaaSi(httpHelper),
        PirateBay(httpHelper),
        TorrentDownload(httpHelper),
        TorrentDownloads(httpHelper),
        TorrentProject(httpHelper),
        TorrentsCSV(httpHelper),
        Uindex(httpHelper),
        YTS(httpHelper),
    )
    // replaced as a whole, so searches running at the same time keep a consistent list
    @Volatile
    private var activeSearchEngines: List<TorrentSearchEngine> = allSearchEngines

    fun refreshTorrentSearchEngines() {
        activeSearchEngines = searchPool.submit(Callable {
            allSearchEngines.parallelStream()
                .filter { torrentSearchEngine: TorrentSearchEngine ->
                    runCatching { torrentSearchEngine.searchTorrents("blue").isNotEmpty() }.getOrDefault(false)
                }
                .collect(Collectors.toList())
        }).get()
    }

    @PreDestroy
    fun shutdown() {
        searchPool.shutdownNow()
    }

    fun getActiveSearchEngines(): List<TorrentSearchEngine> {
        return activeSearchEngines
    }

    val inActiveSearchEngines: List<TorrentSearchEngine>
        get() {
            val currentActiveSearchEngines = activeSearchEngines
            return allSearchEngines.filter { torrentSearchEngine: TorrentSearchEngine -> !currentActiveSearchEngines.contains(torrentSearchEngine) }
        }

    fun searchTorrents(searchString: String): List<Torrent> {
        val activeSearchEngines: List<TorrentSearchEngine> = getActiveSearchEngines()
        if (activeSearchEngines.isEmpty()) {
            logger.warn("no active search engines for [{}]", searchString)
            return emptyList()
        }
        var combineResults: List<Torrent> = emptyList()
        try {
            combineResults = searchPool.submit(
                Callable {
                    activeSearchEngines.parallelStream()
                        .map { torrentSearchEngine: TorrentSearchEngine ->
                            val start = Instant.now()
                            // one failing search engine doesn't drop the results of the others
                            val torrents = runCatching { torrentSearchEngine.searchTorrents(searchString) }
                                .onFailure { logger.error("{} failed for [{}]", torrentSearchEngine, searchString, it) }
                                .getOrDefault(emptyList())
                            logger.info(
                                "{} took {}ms", torrentSearchEngine,
                                Instant.now().toEpochMilli() - start.toEpochMilli()
                            )
                            torrents
                        }
                        .collect(Collectors.toList())
                        .flatten()
                }
            ).get()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.error("Parallel search execution failed", e)
        } catch (e: ExecutionException) {
            logger.error("Parallel search execution failed", e)
        }
        val returnResults: List<Torrent> = ArrayList(cleanDuplicates(prepareCandidates(combineResults, searchString)))
        val cacheStateOfTorrents = mutableListOf<Torrent>()
        cacheStateOfTorrents.addAll(multifileHosterService.getCachedStateOfTorrents(returnResults))

        // the search engines return as many results as they have, only now the list is cut to what is shown
        return rateResults(cacheStateOfTorrents, searchString).take(MAX_SEARCH_RESULTS)
    }

    /**
     * Drops what is never wanted (explicit content for this search, bad quality, no size or magnet) before the
     * lookups at the multifilehosters and trackers, and rates the rest (the better of two duplicates is kept).
     */
    internal fun prepareCandidates(torrents: List<Torrent>, searchString: String): List<Torrent> =
        torrents
            .filterNot { TorrentHelper.isExplicitContent(it, searchString) }
            .filter { TorrentHelper.isValidTorrent(it) }
            .onEach { TorrentHelper.evaluateRating(it, searchString) }

    /**
     * Asks the trackers for the seeders of the torrents which are not cached at a multifilehoster (cached ones
     * download from the hoster, so their stats don't matter), then drops the dead ones, rates and sorts the rest.
     */
    internal fun rateResults(torrents: MutableList<Torrent>, searchString: String): List<Torrent> {
        torrentInfoService.refreshTorrentStats(torrents)
        // a cached torrent is downloadable whatever the search engine says about its seeders (often nothing or 0)
        torrents.filter { it.cached.isNotEmpty() && it.seeder <= 0 }.forEach {
            it.seeder = DUMMY_SEEDERS_OF_CACHED_TORRENT
            it.leecher = maxOf(it.leecher, 0)
        }
        return torrents.filter { torrent: Torrent -> torrent.seeder > 0 }
            .map { torrent: Torrent -> TorrentHelper.evaluateRating(torrent, searchString) }
            .sortedWith(TorrentComparator)
    }

    /** The better rated one of torrents with the same info hash, in the order of the first one. */
    fun cleanDuplicates(combineResults: List<Torrent>): List<Torrent> {
        val cleanedTorrents = LinkedHashMap<Torrent, Torrent>()
        combineResults.forEach { result ->
            val existingTorrent = cleanedTorrents[result]
            if (existingTorrent == null || existingTorrent.searchRating < result.searchRating) {
                cleanedTorrents[result] = result
            }
        }
        return cleanedTorrents.values.toList()
    }

}
