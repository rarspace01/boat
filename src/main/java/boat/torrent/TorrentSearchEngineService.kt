package boat.torrent

import boat.multifileHoster.MultifileHosterService
import boat.utilities.HttpHelper
import lombok.extern.slf4j.Slf4j
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ExecutionException
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.stream.Collectors

@Slf4j
@Service
class TorrentSearchEngineService @Autowired constructor(
    httpHelper: HttpHelper,
    multifileHosterService: MultifileHosterService,
    torrentInfoService: TorrentInfoService
) {
    private val activeSearchEngines: MutableList<TorrentSearchEngine> = ArrayList()
    private val allSearchEngines: List<TorrentSearchEngine>
    private val multifileHosterService: MultifileHosterService
    private val torrentInfoService: TorrentInfoService
    fun refreshTorrentSearchEngines() {
        val tempActiveSearchEngines: MutableList<TorrentSearchEngine> = ArrayList()
        allSearchEngines.parallelStream().forEach { torrentSearchEngine: TorrentSearchEngine ->
            val torrents = torrentSearchEngine.searchTorrents("blue")
            if (torrents.isNotEmpty()) {
                tempActiveSearchEngines.add(torrentSearchEngine)
            }
        }
        activeSearchEngines.clear()
        activeSearchEngines.addAll(tempActiveSearchEngines)
    }

    fun getActiveSearchEngines(): List<TorrentSearchEngine> {
        return activeSearchEngines
    }

    val inActiveSearchEngines: List<TorrentSearchEngine>
        get() = allSearchEngines.stream()
            .filter { torrentSearchEngine: TorrentSearchEngine -> !activeSearchEngines.contains(torrentSearchEngine) }
            .collect(Collectors.toList())

    fun searchTorrents(searchString: String?): List<Torrent?> {
        val combineResults: MutableList<Torrent> = ArrayList()
        val activeSearchEngines: List<TorrentSearchEngine> = ArrayList(
            getActiveSearchEngines()
        )
        val parallelism = activeSearchEngines.size
        var forkJoinPool: ForkJoinPool? = null
        try {
            forkJoinPool = ForkJoinPool(parallelism)
            forkJoinPool.submit(
                Runnable {
                    activeSearchEngines.parallelStream()
                        .forEach { torrentSearchEngine: TorrentSearchEngine ->
                            val start = Instant.now()
                            combineResults.addAll(torrentSearchEngine.searchTorrents(searchString))
                            log.info(
                                "{} took {}ms", torrentSearchEngine,
                                Instant.now().toEpochMilli() - start.toEpochMilli()
                            )
                        }
                }
            ).get()
        } catch (e: InterruptedException) {
            log.error("Parallel search execution failed", e)
        } catch (e: ExecutionException) {
            log.error("Parallel search execution failed", e)
        } finally {
            if (forkJoinPool != null) {
                forkJoinPool.shutdown()
                val awaitTermination = forkJoinPool.awaitTermination(30, TimeUnit.SECONDS)
                if (!awaitTermination) {
                    forkJoinPool.shutdownNow()
                    log.error("searchTorrents() terminated after timeout")
                }
                forkJoinPool = null
            }
        }

        // log.info("RemoteSearch took {}ms", Instant.now().toEpochMilli() - startRemoteSearch.toEpochMilli());
        // final Instant afterRemoteSearch = Instant.now();
        val returnResults: List<Torrent> = ArrayList(cleanDuplicates(combineResults))
        // log.info("Cleanup took {}ms", Instant.now().toEpochMilli() - afterRemoteSearch.toEpochMilli());
        // final Instant afterCleanup = Instant.now();
        val cacheStateOfTorrents = mutableListOf<Torrent>()
        cacheStateOfTorrents.addAll(multifileHosterService.getCachedStateOfTorrents(returnResults))

        // final Instant preRefresh = Instant.now();
        torrentInfoService.refreshTorrentStats(cacheStateOfTorrents)
        // log.info("refreshTorrentStats took {}ms", Instant.now().toEpochMilli() - preRefresh.toEpochMilli());

        // log.info("Cache info took {}ms", Instant.now().toEpochMilli() - afterCleanup.toEpochMilli());
        return cacheStateOfTorrents
            .stream()
            .filter { torrent: Torrent -> torrent.seeder > 0 }
            .map { torrent: Torrent? -> TorrentHelper.evaluateRating(torrent, searchString) }
            .sorted(TorrentHelper.torrentSorter)
            .collect(Collectors.toList())
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

    init {
        allSearchEngines = listOf<TorrentSearchEngine>(
            PirateBay(httpHelper),
            NyaaSi(httpHelper),
            SolidTorrents(httpHelper),
            LeetxTo(httpHelper),
            YTS(httpHelper), // new Torrentz(httpHelper),
            MagnetDL(httpHelper),
            LimeTorrents(httpHelper),
            // Zooqle(httpHelper),
        )
        this.multifileHosterService = multifileHosterService
        this.torrentInfoService = torrentInfoService
        activeSearchEngines.addAll(allSearchEngines)
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }
}
