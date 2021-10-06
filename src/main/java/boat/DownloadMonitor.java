package boat;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import boat.info.BluRayComService;
import boat.info.CloudFileService;
import boat.info.CloudService;
import boat.info.MediaItem;
import boat.info.QueueService;
import boat.info.TorrentMetaService;
import boat.model.Transfer;
import boat.multifileHoster.MultifileHosterService;
import boat.services.TransferService;
import boat.torrent.Torrent;
import boat.torrent.TorrentFile;
import boat.torrent.TorrentHelper;
import boat.torrent.TorrentSearchEngineService;
import boat.torrent.TorrentStatus;
import boat.torrent.TorrentType;
import boat.utilities.HttpHelper;
import boat.utilities.ProcessUtil;
import boat.utilities.PropertiesHelper;
import boat.utilities.StreamGobbler;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.UTF_8;

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
    private static final int SECONDS_BETWEEN_VERSION_CHECK = 60*60;
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
        if (isRcloneInstalled()) {
            final Cache filesCache = cacheManager.getCache("filesCache");
            Arrays.stream("abcdefghijklmnopqrstuvwxyzöäü0".split("")).forEach(searchName -> {
                Stream.of(TorrentType.values())
                    .forEach(torrentType -> {
                        final String destinationPath = cloudService
                            .buildDestinationPathWithTypeOfMediaWithoutSubFolders(searchName, torrentType);
                        if (filesCache != null) {
                            filesCache.evictIfPresent(destinationPath);
                        }
                        List<String> filesInPath = cloudFileService.getFilesInPath(destinationPath);
                        log.info("Files in Path: {}",filesInPath.size());
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
        log.info("addTransfersToDownloadQueueAndUpdateTransferStatus()");
        multifileHosterService.addTransfersToDownloadQueue();
        // refresh remote torrents to local
        multifileHosterService.updateTransferStatus();
    }

    @Scheduled(fixedRate = SECONDS_BETWEEN_DOWNLOAD_POLLING * 1000)
    public void checkForDownloadableTorrents() {
        log.info("checkForDownloadableTorrents()");
        if (!isDownloadInProgress && isRcloneInstalled() && cloudService.isCloudTokenValid()) {
            checkForDownloadableTorrentsAndDownloadTheFirst();
        }
    }

    @Scheduled(fixedRate = SECONDS_BETWEEN_VERSION_CHECK * 1000, initialDelay = 60 * 5 * 1000)
    public void checkForUpdatedVersionAndShutdownIfUpdateAvailable() {
        log.info("checkForUpdatedVersionAndShutdown()");
        String pageContent = httpHelper.getPage("https://api.github.com/repos/rarspace01/boat/releases/latest");
        if(!Strings.isEmpty(pageContent)){
            var jsonRoot = JsonParser.parseString(pageContent);
            var githubVersion = jsonRoot.getAsJsonObject().get("name").getAsString();
            if(!PropertiesHelper.getVersion().contains(githubVersion)) {
                log.info("Local [{}] != Github [{}]",PropertiesHelper.getVersion(),githubVersion);
                log.warn("version out of date, shutting down");
            } else {
                 log.info("Local [{}] == Github [{}]",PropertiesHelper.getVersion(),githubVersion);
                log.info("version up to date, keep working");
            }
        }
    }

    @Scheduled(fixedRate = SECONDS_BETWEEN_QUEUE_POLLING * 1000)
    public void checkForQueueEntries() {
        log.info("checkForQueueEntries()");
        if (!isDownloadInProgress && cloudService.isCloudTokenValid()) {
            checkForQueueEntryAndAddToTransfers();
        }
    }

    private void checkForQueueEntryAndAddToTransfers() {
        final List<Torrent> remoteTorrents = multifileHosterService.getRemoteTorrents();
        final long numberOfActiveRemoteTorrents = remoteTorrents
            .stream().filter(torrent -> !torrent.remoteTorrentStatus.equals(TorrentStatus.READY_TO_BE_DOWNLOADED))
            .count();
        final long numberOfTorrentsReadyToDownload = remoteTorrents
            .stream().filter(torrent -> torrent.remoteTorrentStatus.equals(TorrentStatus.READY_TO_BE_DOWNLOADED))
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

    private boolean isRcloneInstalled() {
        if (isRcloneInstalled == null) {
            isRcloneInstalled = ProcessUtil.isRcloneInstalled();
            if (!isRcloneInstalled) {
                log.error("no rclone found. Downloads not possible");
            }
        }
        return isRcloneInstalled;
    }

    @Scheduled(fixedRate = SECONDS_BETWEEN_CLEAR_TRANSFER_POLLING * 1000)
    public void clearTransferTorrents() {
        log.info("clearTransferTorrents()");
        multifileHosterService.getRemoteTorrents().stream()
            .filter(this::isTorrentStuckOnErrror)
            .forEach(multifileHosterService::delete);
    }

    private boolean isTorrentStuckOnErrror(Torrent torrent) {
        return torrent.remoteTorrentStatus.equals(TorrentStatus.ERROR);
    }

    private boolean checkForDownloadableTorrentsAndDownloadTheFirst() {
        final Torrent torrentToBeDownloaded = getTorrentToBeDownloaded();
        if (torrentToBeDownloaded != null) {
            Optional<Transfer> transferToBeDownloaded = transferService.getAll().stream()
                .filter(transfer -> transfer.uri != null && transfer.uri.toLowerCase(Locale.ROOT).contains(torrentToBeDownloaded.getTorrentId().toLowerCase(
                    Locale.ROOT))).findFirst();
            isDownloadInProgress = true;
            boolean wasDownloadSuccessful = false;
            if (!transferToBeDownloaded.isPresent()) {
                log.warn("Torrent not in transfers but downloading it: {}", torrentToBeDownloaded);
            }
            try {
                if (multifileHosterService.isSingleFileDownload(torrentToBeDownloaded)) {
                    TorrentFile fileToDownload = multifileHosterService
                        .getMainFileURLFromTorrent(torrentToBeDownloaded);
                    updateUploadStatus(torrentToBeDownloaded, List.of(fileToDownload), 0, null);
                    if (torrentToBeDownloaded.name.contains("magnet:?")) {
                        torrentToBeDownloaded.name = extractFileNameFromUrl(fileToDownload.url);
                    }
                    wasDownloadSuccessful = rcloneDownloadFileToGdrive(fileToDownload.url,
                        cloudService.buildDestinationPath(torrentToBeDownloaded.name) + buildFilename(
                            torrentToBeDownloaded.name, fileToDownload.url)
                    );
                    updateUploadStatus(torrentToBeDownloaded, List.of(fileToDownload), 1, null);
                    multifileHosterService.delete(torrentToBeDownloaded);
                    transferToBeDownloaded.ifPresent(transferService::delete);
                } else {
                    List<TorrentFile> filesFromTorrent = multifileHosterService
                        .getFilesFromTorrent(torrentToBeDownloaded);
                    int currentFileNumber = 0;
                    int failedUploads = 0;
                    Instant startTime = Instant.now();
                    for (TorrentFile torrentFile : filesFromTorrent) {
                        // check fileSize to get rid of samples and NFO files?
                        updateUploadStatus(torrentToBeDownloaded, filesFromTorrent, currentFileNumber, startTime);
                        String destinationPath = cloudService
                            .buildDestinationPath(torrentToBeDownloaded.name, filesFromTorrent);
                        String targetFilePath;
                        if (destinationPath.contains("transfer")) {
                            targetFilePath = PropertiesHelper.getProperty("RCLONEDIR") + "/transfer/multipart/"
                                + torrentToBeDownloaded.name + "/" + torrentFile.name;
                        } else {
                            if (destinationPath.contains(TorrentType.SERIES_SHOWS.getType())) {
                                targetFilePath = destinationPath + torrentFile.name;
                            } else {
                                targetFilePath = destinationPath + torrentToBeDownloaded.name + "/" + torrentFile.name;
                            }
                        }
                        if (!rcloneDownloadFileToGdrive(torrentFile.url, targetFilePath)) {
                            failedUploads++;
                        }
                        currentFileNumber++;
                        updateUploadStatus(torrentToBeDownloaded, filesFromTorrent, currentFileNumber, startTime);
                    }
                    wasDownloadSuccessful = failedUploads == 0;
                    multifileHosterService.delete(torrentToBeDownloaded);
                    transferToBeDownloaded.ifPresent(transferService::delete);
                }
            } catch (Exception exception) {
                log.error(String.format("Couldn't download Torrent: %s", torrentToBeDownloaded), exception);
            } finally {
                isDownloadInProgress = false;
            }
            if (!wasDownloadSuccessful) {
                log.error("Couldn't download Torrent: {}", torrentToBeDownloaded);
            }
            return wasDownloadSuccessful;
        } else {
            return true;
        }
    }

    public Torrent getTorrentToBeDownloaded() {
        List<Torrent> activeTorrents = torrentMetaService.getTorrentsToDownload();
        final double remainingTrafficInMB = multifileHosterService.getRemainingTrafficInMB();
        return activeTorrents
            .stream()
            .filter(this::checkIfTorrentCanBeDownloaded)
            .filter(torrent -> multifileHosterService.getSizeOfTorrentInMB(torrent) < remainingTrafficInMB)
            .sorted()
            .findFirst().orElse(null);
    }

    private void updateUploadStatus(Torrent torrentToBeDownloaded, List<TorrentFile> listOfFiles, int currentFileNumber,
                                    Instant startTime) {
        Optional<Transfer> transferOptional = transferService.getAll().stream().filter(transfer -> transfer.uri != null && transfer.uri.toLowerCase(Locale.ROOT).contains(torrentToBeDownloaded.getTorrentId().toLowerCase(
            Locale.ROOT))).findFirst();

        if (transferOptional.isPresent()) {
            Transfer transfer = transferOptional.get();
            transfer.torrentStatus = TorrentStatus.UPLOADING_TO_DRIVE;
            transfer.progressInPercentage = (double) currentFileNumber / (double) listOfFiles.size();
            transfer.eta = getUploadDuration(listOfFiles, currentFileNumber, startTime);
            transferService.save(transfer);
        }

        torrentToBeDownloaded.remoteStatusText = getUploadStatusString(torrentToBeDownloaded, listOfFiles, currentFileNumber,
            startTime);
        torrentMetaService.updateTorrent(torrentToBeDownloaded);
    }

    public Duration getUploadDuration(List<TorrentFile> listOfFiles,
                                        int currentFileNumber, Instant startTime) {
        Duration remainingDuration;
        int fileCount = listOfFiles.size();
        if (startTime == null || currentFileNumber == 0) {
            final long size = listOfFiles.stream()
                .map(torrentFile -> torrentFile.filesize)
                .reduce(0L, Long::sum);
            double lsize = (double) size / 1024.0 / 1024.0;
            long expectedSecondsRemaining = (long) (lsize / 10.0);
            remainingDuration = Duration.of(expectedSecondsRemaining, ChronoUnit.SECONDS);
        } else {
            long diffTime = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            final long milliPerFile = diffTime / (long) currentFileNumber;
            final int remainingFileCount = fileCount - currentFileNumber;
            final long expectedMilliRemaining = milliPerFile * remainingFileCount;
            remainingDuration = Duration.of(expectedMilliRemaining, ChronoUnit.MILLIS);
        }
        return remainingDuration;
    }

    public String getUploadStatusString(Torrent torrentToBeDownloaded, List<TorrentFile> listOfFiles,
                                        int currentFileNumber, Instant startTime) {
        Duration remainingDuration;
        int fileCount = listOfFiles.size();
        if (startTime == null || currentFileNumber == 0) {
            final long size = listOfFiles.stream()
                .map(torrentFile -> torrentFile.filesize)
                .reduce(0L, Long::sum);
            double lsize = (double) size / 1024.0 / 1024.0;
            long expectedSecondsRemaining = (long) (lsize / 10.0);
            remainingDuration = Duration.of(expectedSecondsRemaining, ChronoUnit.SECONDS);
        } else {
            long diffTime = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            final long milliPerFile = diffTime / (long) currentFileNumber;
            final int remainingFileCount = fileCount - currentFileNumber;
            final long expectedMilliRemaining = milliPerFile * remainingFileCount;
            remainingDuration = Duration.of(expectedMilliRemaining, ChronoUnit.MILLIS);
        }
        return String.format("Uploading: %d/%d done ETA: %02d:%02d:%02d",
            currentFileNumber,
            fileCount,
            remainingDuration.toHours(),
            remainingDuration.toMinutesPart(),
            remainingDuration.toSecondsPart());
    }

    public String buildFilename(String name, String fileURLFromTorrent) {
        String fileEndingFromUrl = extractFileEndingFromUrl(fileURLFromTorrent);
        name = StringUtils.hasText(name) ? name : fileURLFromTorrent;
        name = name.replaceAll("\"", "");
        name = name.replaceAll("\\.torrent", "");
        name = name.replaceAll("[wW][wW][wW]\\.[A-Za-z0-9-]*\\.[a-zA-Z]+[\\s-]*", "").trim();
        name = name.replaceAll("\\s", ".");
        if (!name.contains(fileEndingFromUrl)) {
            return name + "." + fileEndingFromUrl;
        } else {
            return name;
        }
    }

    private String extractFileNameFromUrl(String fileURLFromTorrent) {
        String fileString = URLDecoder.decode(fileURLFromTorrent, UTF_8);
        Pattern pattern = Pattern.compile("([\\w.%\\-]+)$");
        String foundMatch = null;
        Matcher matcher = pattern.matcher(fileString);

        while (matcher.find()) {
            foundMatch = matcher.group();
        }
        if (foundMatch != null) {
            foundMatch.replaceAll("\\s", ".");
        }
        return foundMatch;
    }

    private String extractFileEndingFromUrl(String fileURLFromTorrent) {
        Pattern pattern = Pattern.compile("[A-Za-z0-9]+$");
        String foundMatch = null;
        Matcher matcher = pattern.matcher(fileURLFromTorrent);

        while (matcher.find()) {
            foundMatch = matcher.group();
        }
        // remove quotes && .torrent
        return foundMatch != null ? foundMatch.replaceAll("\"", "").replaceAll(".torrent", "") : fileURLFromTorrent;
    }

    private boolean rcloneDownloadFileToGdrive(String fileURLFromTorrent, String destinationPath) {
        log.info("D>[{}]", destinationPath);
        ProcessBuilder builder = new ProcessBuilder();
        final String commandToRun = String
            .format("rclone copyurl '%s' '%s'", fileURLFromTorrent, destinationPath.replaceAll("'", ""));
        builder.command("bash", "-c", commandToRun);
        builder.directory(new File(System.getProperty("user.home")));
        Process process = null;
        int exitCode = -1;
        try {
            process = builder.start();
            StreamGobbler streamGobbler =
                new StreamGobbler(process.getInputStream(), System.out::println);
            Executors.newSingleThreadExecutor().submit(streamGobbler);
            exitCode = process.waitFor();
        } catch (IOException | InterruptedException e) {
            log.error("upload failed: {}", destinationPath);
            log.error(e.getMessage());
            return false;
        }
        if (exitCode != 0) {
            log.error("upload failed: {}", destinationPath);
            return false;
        } else {
            log.info("DF>[{}]", destinationPath);
            return true;
        }
    }


    private boolean checkIfTorrentCanBeDownloaded(Torrent remoteTorrent) {
        return TorrentStatus.READY_TO_BE_DOWNLOADED == remoteTorrent.remoteTorrentStatus;
    }

}
