package boat.torrent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import boat.multifileHoster.MultifileHosterService;
import boat.utilities.HttpHelper;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@Service
public class TorrentSearchEngineService {

    private final List<TorrentSearchEngine> activeSearchEngines = new ArrayList<>();
    private final List<TorrentSearchEngine> allSearchEngines;
    private final MultifileHosterService multifileHosterService;
    private final TorrentInfoService torrentInfoService;

    @Autowired
    public TorrentSearchEngineService(HttpHelper httpHelper, MultifileHosterService multifileHosterService,
                                      TorrentInfoService torrentInfoService) {
        this.allSearchEngines = Arrays.asList(
            new PirateBay(httpHelper),
            new NyaaSi(httpHelper),
            new SolidTorrents(httpHelper),
            new LeetxTo(httpHelper),
            new YTS(httpHelper),
            new Torrentz(httpHelper),
            new MagnetDL(httpHelper),
            new LimeTorrents(httpHelper),
            new Zooqle(httpHelper)
        );
        this.multifileHosterService = multifileHosterService;
        this.torrentInfoService = torrentInfoService;
        this.activeSearchEngines.addAll(allSearchEngines);
    }

    public void refreshTorrentSearchEngines() {
        final List<TorrentSearchEngine> tempActiveSearchEngines = new ArrayList<>();
        allSearchEngines.parallelStream().forEach(torrentSearchEngine -> {
            final List<Torrent> torrents = torrentSearchEngine.searchTorrents("blue");
            if (!torrents.isEmpty()) {
                tempActiveSearchEngines.add(torrentSearchEngine);
            }
        });
        activeSearchEngines.clear();
        activeSearchEngines.addAll(tempActiveSearchEngines);
    }

    public List<TorrentSearchEngine> getActiveSearchEngines() {
        return activeSearchEngines;
    }

    public List<TorrentSearchEngine> getInActiveSearchEngines() {
        return allSearchEngines.stream()
            .filter(torrentSearchEngine -> !activeSearchEngines.contains(torrentSearchEngine))
            .collect(Collectors.toList());
    }

    @Cacheable("searchCache")
    public Torrent cachedSearchTorrent(String searchString) {
        return searchTorrents(searchString).stream().findFirst().orElse(null);
    }

    @NotNull
    public List<Torrent> searchTorrents(String searchString) {
        //final Instant startRemoteSearch = Instant.now();
        List<Torrent> combineResults = new ArrayList<>();
        final List<TorrentSearchEngine> activeSearchEngines = new ArrayList<>(
            getActiveSearchEngines());

        final int parallelism = activeSearchEngines.size();
        ForkJoinPool forkJoinPool = null;
        try {
            forkJoinPool = new ForkJoinPool(parallelism);
            forkJoinPool.submit(() ->
                activeSearchEngines.parallelStream()
                    .forEach(torrentSearchEngine -> {
                        final Instant start = Instant.now();
                        combineResults.addAll(torrentSearchEngine.searchTorrents(searchString));
                        log.info("{} took {}ms", torrentSearchEngine,
                            Instant.now().toEpochMilli() - start.toEpochMilli());
                    })
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Parallel search execution failed", e);
        } finally {
            if (forkJoinPool != null) {
                forkJoinPool.shutdown();
                forkJoinPool = null;
            }
        }

        //log.info("RemoteSearch took {}ms", Instant.now().toEpochMilli() - startRemoteSearch.toEpochMilli());
        //final Instant afterRemoteSearch = Instant.now();

        List<Torrent> returnResults = new ArrayList<>(cleanDuplicates(combineResults));
        //log.info("Cleanup took {}ms", Instant.now().toEpochMilli() - afterRemoteSearch.toEpochMilli());
        //final Instant afterCleanup = Instant.now();
        List<Torrent> cacheStateOfTorrents = multifileHosterService.getCachedStateOfTorrents(returnResults);

        //final Instant preRefresh = Instant.now();
        torrentInfoService.refreshTorrentStats(cacheStateOfTorrents);
        //log.info("refreshTorrentStats took {}ms", Instant.now().toEpochMilli() - preRefresh.toEpochMilli());

        //log.info("Cache info took {}ms", Instant.now().toEpochMilli() - afterCleanup.toEpochMilli());

        return cacheStateOfTorrents
            .stream()
            .map(torrent -> TorrentHelper.evaluateRating(torrent, searchString))
            .sorted(TorrentHelper.torrentSorter)
            .collect(Collectors.toList());
    }

    public List<Torrent> cleanDuplicates(List<Torrent> combineResults) {
        ArrayList<Torrent> cleanedTorrents = new ArrayList<>();
        combineResults.forEach(result -> {
            if (!cleanedTorrents.contains(result)) {
                cleanedTorrents.add(result);
            } else {
                final int existingTorrentIndex = cleanedTorrents.indexOf(result);
                final Torrent existingTorrent = cleanedTorrents.get(existingTorrentIndex);
                if (existingTorrent.searchRating < result.searchRating) {
                    cleanedTorrents.remove(existingTorrent);
                    cleanedTorrents.add(result);
                }
            }
        });
        return cleanedTorrents;
    }

}
