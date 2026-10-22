package boat.torrent

import boat.multifileHoster.MultifileHosterService
import boat.torrent.searchEngines.*
import boat.utilities.HttpHelper
import boat.utilities.LoggerDelegate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.stream.Collectors

@Service
class TorrentSearchEngineService(
    final val httpHelper: HttpHelper,
    val multifileHosterService: MultifileHosterService,
    val torrentInfoService: TorrentInfoService
) {

    companion object {
        private val logger by LoggerDelegate()
        const val DUMMY_SEEDERS_OF_CACHED_TORRENT = 1
    }

    private val allSearchEngines: List<TorrentSearchEngine> = listOf<TorrentSearchEngine>(
        AnyBT(httpHelper),
        EZTV(httpHelper),
        ExtraTorrent(httpHelper),
        Kat(httpHelper),
        Knaben(httpHelper),
        //LeetxTo(httpHelper),
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
        activeSearchEngines = allSearchEngines.parallelStream()
            .filter { torrentSearchEngine: TorrentSearchEngine ->
                runCatching { torrentSearchEngine.searchTorrents("blue").isNotEmpty() }.getOrDefault(false)
            }
            .collect(Collectors.toList())
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
        val forkJoinPool = ForkJoinPool(activeSearchEngines.size)
        try {
            combineResults = forkJoinPool.submit(
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
        } finally {
            forkJoinPool.shutdown()
            val awaitTermination = forkJoinPool.awaitTermination(30, TimeUnit.SECONDS)
            if (!awaitTermination) {
                forkJoinPool.shutdownNow()
                logger.error("searchTorrents() terminated after timeout")
            }
        }
        val returnResults: List<Torrent> = ArrayList(cleanDuplicates(combineResults.filterNot { TorrentHelper.isExplicitContent(it, searchString) }))
        val cacheStateOfTorrents = mutableListOf<Torrent>()
        cacheStateOfTorrents.addAll(multifileHosterService.getCachedStateOfTorrents(returnResults))

        return rateResults(cacheStateOfTorrents, searchString)
    }

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

    fun cleanDuplicates(combineResults: List<Torrent>): List<Torrent> {
        val cleanedTorrents = ArrayList<Torrent>()
        combineResults.forEach(
            Consumer { result: Torrent ->
                if (!cleanedTorrents.contains(result)) {
                    cleanedTorrents.add(result)
                } else {
                    val existingTorrentIndex = cleanedTorrents.indexOf(result)
                    val existingTorrent = cleanedTorrents[existingTorrentIndex]
                    if (existingTorrent.searchRating < result.searchRating) {
                        cleanedTorrents.remove(existingTorrent)
                        cleanedTorrents.add(result)
                    }
                }
            }
        )
        return cleanedTorrents
    }

}
