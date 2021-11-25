package boat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import boat.info.BluRayComService;
import boat.info.CloudFileService;
import boat.info.CloudService;
import boat.info.MediaItem;
import boat.info.QueueService;
import boat.info.TorrentMetaService;
import boat.model.TransferStatus;
import boat.multifileHoster.MultifileHosterService;
import boat.services.TransferService;
import boat.torrent.Torrent;
import boat.torrent.TorrentHelper;
import boat.torrent.TorrentSearchEngineService;
import boat.torrent.TorrentType;
import boat.utilities.HttpHelper;
import boat.utilities.PropertiesHelper;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class DownloadMonitor {

    public static final int MIN_GB_FOR_QUEUE = 150;
    public static final int MAX_QUEUE_DOWNLOADS_LIMIT = 20;
    private final TorrentSearchEngineService torrentSearchEngineService;
    private final TorrentMetaService torrentMetaService;
    private final CloudService cloudService;
    private final MultifileHosterService multifileHosterService;

    private static final int SECONDS_BETWEEN_DOWNLOAD_POLLING = 30;
    private static final int SECONDS_BETWEEN_TRANSFER_POLLING = 30;
    private static final int SECONDS_BETWEEN_VERSION_CHECK = 60 * 60;
    private static final int SECONDS_BETWEEN_QUEUE_POLLING = 30;
    private static final int SECONDS_BETWEEN_SEARCH_ENGINE_POLLING = 240;
    private static final int SECONDS_BETWEEN_CLEAR_TRANSFER_POLLING = 3600;
    private static final int SECONDS_BETWEEN_FILE_CACHE_REFRESH = 60 * 60 * 6;
    private static final Logger log = LoggerFactory.getLogger(DownloadMonitor.class);

    private boolean isDownloadInProgress = false;
    private final CloudFileService cloudFileService;
    private final CacheManager cacheManager;
    private final QueueService queueService;
    private final BluRayComService bluRayComService;
    private final TransferService transferService;
    private final HttpHelper httpHelper;
    private Boolean isRcloneInstalled;

    public DownloadMonitor(TorrentSearchEngineService torrentSearchEngineService,
                           TorrentMetaService torrentMetaService,
                           CloudService cloudService,
                           MultifileHosterService multifileHosterService,
                           CloudFileService cloudFileService,
                           CacheManager cacheManager,
                           QueueService queueService,
                           BluRayComService bluRayComService,
                           TransferService transferService,
                           HttpHelper httpHelper
    ) {
        this.torrentSearchEngineService = torrentSearchEngineService;
        this.torrentMetaService = torrentMetaService;
        this.cloudService = cloudService;
        this.multifileHosterService = multifileHosterService;
        this.cloudFileService = cloudFileService;
        this.cacheManager = cacheManager;
        this.queueService = queueService;
        this.bluRayComService = bluRayComService;
        this.transferService = transferService;
        this.httpHelper = httpHelper;
    }

    @Scheduled(fixedRate = SECONDS_BETWEEN_SEARCH_ENGINE_POLLING * 1000)
    public void refreshTorrentSearchEngines() {
        log.debug("refreshTorrentSearchEngines()");
        torrentSearchEngineService.refreshTorrentSearchEngines();
    }

//    @Scheduled(fixedRate = SECONDS_BETWEEN_FILE_CACHE_REFRESH * 1000)
//    public void fillQueueWithMovies() {
//        log.info("fillQueueWithMovies()");
//        final List<MediaItem> completeList = new java.util.ArrayList<>(Collections.emptyList());
//        IntStream.rangeClosed(2006,2021)
//            .parallel()
//            .forEach(year -> {
//                IntStream.rangeClosed(1,12)
//                    .parallel()
//                    .forEach(month -> {
//                        log.info("adding Queue for {}/{}", month, year);
//                        completeList.addAll(bluRayComService.getReleasesForMonthAndYear(month, year));
//                        log.info("Queue Size: {}",completeList.size());
//                    });
//            });
//        queueService.addAll(completeList);
//    }

    @Scheduled(fixedRate = SECONDS_BETWEEN_FILE_CACHE_REFRESH * 1000)
    public void refreshCloudFileServiceCache() {
        log.info("refreshCloudFileServiceCache()");
        final long startOfCache = System.currentTimeMillis();
        if (multifileHosterService.isRcloneInstalled()) {
            final Cache filesCache = cacheManager.getCache("filesCache");
            Arrays.stream("abcdefghijklmnopqrstuvwxyz+0".split("")).forEach(searchName -> {
                Stream.of(TorrentType.values())
                    .forEach(torrentType -> {
                        final String destinationPath = cloudService
                            .buildDestinationPathWithTypeOfMediaWithoutSubFolders(searchName, torrentType);
                        if (filesCache != null) {
                            filesCache.evictIfPresent(destinationPath);
                        }
                        List<String> filesInPath = cloudFileService.getFilesInPath(destinationPath);
                        log.info("Files in Path: {}", filesInPath.size());
                    });
                log.info("Cache refresh done for: {}", searchName);
            });
            log.info("Cache refresh done in: {}ms", System.currentTimeMillis() - startOfCache);
            cloudFileService.setCacheFilled(true);
        } else {
            log.warn("rclone not installed");
        }
    }

    @Scheduled(fixedRate = SECONDS_BETWEEN_TRANSFER_POLLING * 1000)
    public void addTransfersToDownloadQueueAndUpdateTransferStatus() {
        multifileHosterService.addTransfersToDownloadQueue();
        multifileHosterService.updateTransferStatus();
    }

    @Scheduled(fixedRate = SECONDS_BETWEEN_DOWNLOAD_POLLING * 1000)
    public void checkForDownloadableTorrents() {
        multifileHosterService.checkForDownloadableTorrents();
    }

    @Scheduled(fixedRate = SECONDS_BETWEEN_VERSION_CHECK * 1000, initialDelay = 60 * 5 * 1000)
    public void checkForUpdatedVersionAndShutdownIfUpdateAvailable() {
        log.info("checkForUpdatedVersionAndShutdown()");
        String pageContent = httpHelper.getPage("https://api.github.com/repos/rarspace01/boat/releases/latest");
        if (!Strings.isEmpty(pageContent)) {
            var jsonRoot = JsonParser.parseString(pageContent);
            var githubVersion = jsonRoot.getAsJsonObject().get("name").getAsString();
            if (!PropertiesHelper.getVersion().contains(githubVersion)) {
                log.info("Local [{}] != Github [{}]", PropertiesHelper.getVersion(), githubVersion);
                log.warn("version out of date, shutting down");
                System.exit(0);
            }
        }
    }

    @Scheduled(fixedRate = SECONDS_BETWEEN_QUEUE_POLLING * 1000)
    public void checkForQueueEntries() {
        if (!isDownloadInProgress && cloudService.isCloudTokenValid()) {
            checkForQueueEntryAndAddToTransfers();
        }
    }

    private void checkForQueueEntryAndAddToTransfers() {
        final List<Torrent> remoteTorrents = multifileHosterService.getRemoteTorrents();
        final long numberOfActiveRemoteTorrents = remoteTorrents
            .stream().filter(torrent -> !torrent.remoteTransferStatus.equals(TransferStatus.READY_TO_BE_DOWNLOADED))
            .count();
        final long numberOfTorrentsReadyToDownload = remoteTorrents
            .stream().filter(torrent -> torrent.remoteTransferStatus.equals(TransferStatus.READY_TO_BE_DOWNLOADED))
            .count();
        if (numberOfTorrentsReadyToDownload == 0 && numberOfActiveRemoteTorrents < MAX_QUEUE_DOWNLOADS_LIMIT
            && multifileHosterService.getRemainingTrafficInMB() > MIN_GB_FOR_QUEUE * 1024) {

            queueService.getQueue().stream().findFirst().ifPresent(mediaItem -> {
                log.info("picked {}", mediaItem);
                String searchName = TorrentHelper.getSearchNameFrom(mediaItem);
                final List<String> existingFiles = cloudService.findExistingFiles(searchName);
                if (existingFiles.isEmpty()) {
                    torrentSearchEngineService.searchTorrents(searchName).stream()
                        .findFirst()
                        .ifPresent(multifileHosterService::addTorrentToTransfer);
                } else {
                    log.warn("Looks like Torrent was already downloaded, skipped {} - matched files: {}", mediaItem, existingFiles);
                }
                removeFromQueue(mediaItem);
            });
        }
    }

    private void removeFromQueue(MediaItem mediaItem) {
        queueService.remove(mediaItem);
        queueService.saveQueue();
    }

    @Scheduled(fixedRate = SECONDS_BETWEEN_CLEAR_TRANSFER_POLLING * 1000)
    public void clearTransferAndTorrentsWithErrors() {
        log.info("clearTransferTorrents()");
        multifileHosterService.getRemoteTorrents().stream()
            .filter(this::isTorrentStuckOnErrror)
            .forEach(multifileHosterService::delete);
        transferService.getAll().stream()
            .filter(transfer -> TransferStatus.ERROR.equals(transfer.getTransferStatus()) || transfer.updated.isBefore(Instant.now().minus(1, ChronoUnit.DAYS)))
            .forEach(
                transferService::delete);

    }

    private boolean isTorrentStuckOnErrror(Torrent torrent) {
        return torrent.remoteTransferStatus.equals(TransferStatus.ERROR);
    }

}
