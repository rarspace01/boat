package pirateboat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pirateboat.info.CloudFileService;
import pirateboat.info.CloudService;
import pirateboat.info.TheFilmDataBaseService;
import pirateboat.info.TorrentMetaService;
import pirateboat.multifileHoster.MultifileHosterService;
import pirateboat.torrent.Torrent;
import pirateboat.torrent.TorrentFile;
import pirateboat.torrent.TorrentSearchEngineService;
import pirateboat.torrent.TorrentType;
import pirateboat.utilities.ProcessUtil;
import pirateboat.utilities.PropertiesHelper;
import pirateboat.utilities.StreamGobbler;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

@Component
public class DownloadMonitor {

    private final TorrentSearchEngineService torrentSearchEngineService;
    private final TorrentMetaService torrentMetaService;
    private final CloudService cloudService;
    private final MultifileHosterService multifileHosterService;

    private static final int SECONDS_BETWEEN_DOWNLOAD_POLLING = 30;
    private static final int SECONDS_BETWEEN_SEARCH_ENGINE_POLLING = 240;
    private static final int SECONDS_BETWEEN_CLEAR_TRANSFER_POLLING = 3600;
    private static final int SECONDS_BETWEEN_FILE_CACHE_REFRESH = 60 * 60 * 6;
    private static final Logger log = LoggerFactory.getLogger(DownloadMonitor.class);

    private boolean isDownloadInProgress = false;
    private final TheFilmDataBaseService theFilmDataBaseService;
    private final CloudFileService cloudFileService;
    private final CacheManager cacheManager;
    private Boolean isRcloneInstalled;

    public DownloadMonitor(TorrentSearchEngineService torrentSearchEngineService,
                           TheFilmDataBaseService theFilmDataBaseService,
                           TorrentMetaService torrentMetaService,
                           CloudService cloudService,
                           MultifileHosterService multifileHosterService,
                           CloudFileService cloudFileService,
                           CacheManager cacheManager
    ) {
        this.torrentSearchEngineService = torrentSearchEngineService;
        this.torrentMetaService = torrentMetaService;
        this.cloudService = cloudService;
        this.multifileHosterService = multifileHosterService;
        this.theFilmDataBaseService = theFilmDataBaseService;
        this.cloudFileService = cloudFileService;
        this.cacheManager = cacheManager;
    }

    @Scheduled(fixedRate = SECONDS_BETWEEN_SEARCH_ENGINE_POLLING * 1000)
    public void refreshTorrentSearchEngines() {
        log.debug("refreshTorrentSearchEngines()");
        torrentSearchEngineService.refreshTorrentSearchEngines();
    }

    @Scheduled(fixedRate = SECONDS_BETWEEN_FILE_CACHE_REFRESH * 1000)
    public void refreshCloudFileServiceCache() {
        log.info("refreshCloudFileServiceCache()");
        if(isRcloneInstalled()){
            final Cache filesCache = cacheManager.getCache("filesCache");
            Arrays.stream("abcdefghijklmnopqrstuvwxyzöäü0".split("")).forEach(searchName -> {
                Stream.of(TorrentType.values()).forEach(torrentType -> {
                    final String destinationPath = cloudService.buildDestinationPathWithTypeOfMedia(searchName, torrentType);
                    if (filesCache != null) {
                        filesCache.evictIfPresent(destinationPath);
                    }
                    cloudFileService.getFilesInPath(destinationPath);
                });
                log.info("Cache refresh done for: {}", searchName);
            });
        } else {
            log.warn("rclone not installed");
        }
    }

    @Scheduled(fixedRate = SECONDS_BETWEEN_DOWNLOAD_POLLING * 1000)
    public void checkForDownloadableTorrents() {
        if (!isDownloadInProgress && isRcloneInstalled()) {
            checkForDownloadableTorrentsAndDownloadTheFirst();
        }
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
        multifileHosterService.getRemoteTorrents().stream().filter(torrent -> torrent.status.contains("error")).forEach(multifileHosterService::delete);
    }

    private boolean checkForDownloadableTorrentsAndDownloadTheFirst() {
        final Torrent torrentToBeDownloaded = getTorrentToBeDownloaded();
        if (torrentToBeDownloaded != null) {
            isDownloadInProgress = true;
            boolean wasDownloadSuccessful = false;
            try {
                if (multifileHosterService.isSingleFileDownload(torrentToBeDownloaded)) {
                    updateUploadStatus(torrentToBeDownloaded, 0, 1);
                    String fileURLFromTorrent = multifileHosterService.getMainFileURLFromTorrent(torrentToBeDownloaded);
                    if (torrentToBeDownloaded.name.contains("magnet:?")) {
                        torrentToBeDownloaded.name = extractFileNameFromUrl(fileURLFromTorrent);
                    }
                    wasDownloadSuccessful = rcloneDownloadFileToGdrive(fileURLFromTorrent,
                            cloudService.buildDestinationPath(torrentToBeDownloaded.name) + buildFilename(torrentToBeDownloaded.name, fileURLFromTorrent)
                    );
                    updateUploadStatus(torrentToBeDownloaded, 1, 1);
                    multifileHosterService.delete(torrentToBeDownloaded);
                } else {
                    List<TorrentFile> filesFromTorrent = multifileHosterService.getFilesFromTorrent(torrentToBeDownloaded);
                    int currentFileNumber = 0;
                    int failedUploads = 0;
                    int maxFileCount = filesFromTorrent.size();
                    Instant startTime = Instant.now();
                    for (TorrentFile torrentFile : filesFromTorrent) {
                        // check fileSize to get rid of samples and NFO files?
                        updateUploadStatus(torrentToBeDownloaded, currentFileNumber, maxFileCount, startTime);
                        String destinationPath = cloudService.buildDestinationPath(torrentToBeDownloaded.name);
                        String targetFilePath;
                        if (destinationPath.contains("transfer")) {
                            targetFilePath = PropertiesHelper.getProperty("rclonedir") + "/transfer/multipart/" + torrentToBeDownloaded.name + "/" + torrentFile.name;
                        } else {
                            targetFilePath = destinationPath + torrentToBeDownloaded.name + "/" + torrentFile.name;
                        }
                        if (!rcloneDownloadFileToGdrive(torrentFile.url, targetFilePath)) {
                            failedUploads++;
                        }
                        currentFileNumber++;
                        updateUploadStatus(torrentToBeDownloaded, currentFileNumber, maxFileCount, startTime);
                    }
                    wasDownloadSuccessful = failedUploads > 0;
                    multifileHosterService.delete(torrentToBeDownloaded);
                }
            } catch (Exception exception) {
                log.error(String.format("Couldn't download Torrent: %s", torrentToBeDownloaded), exception);
            } finally {
                isDownloadInProgress = false;
            }
            if (!wasDownloadSuccessful) {
                log.error(String.format("Couldn't download Torrent: %s", torrentToBeDownloaded));
            }
            return wasDownloadSuccessful;
        } else {
            return true;
        }
    }

    public Torrent getTorrentToBeDownloaded() {
        torrentMetaService.refreshTorrents();
        List<Torrent> activeTorrents = torrentMetaService.getActiveTorrents();
        return activeTorrents.stream().filter(this::checkIfTorrentCanBeDownloaded).findFirst().orElse(null);
    }

    private void updateUploadStatus(Torrent torrentToBeDownloaded, int currentFileNumber, int fileCount) {
        torrentToBeDownloaded.status = getUploadStatusString(currentFileNumber, fileCount, null);
        torrentMetaService.updateTorrent(torrentToBeDownloaded);
    }

    private void updateUploadStatus(Torrent torrentToBeDownloaded, int currentFileNumber, int fileCount, Instant startTime) {
        torrentToBeDownloaded.status = getUploadStatusString(currentFileNumber, fileCount, startTime);
        torrentMetaService.updateTorrent(torrentToBeDownloaded);
    }

    private String getUploadStatusString(int currentFileNumber, int fileCount, Instant startTime) {
        if (startTime == null || currentFileNumber == 0) {
            return String.format("Uploading: %d/%d done", currentFileNumber, fileCount);
        } else {
            long diffTime = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            final long milliPerFile = diffTime / (long) currentFileNumber;
            final int remainingFileCount = fileCount - currentFileNumber;
            final long expectedMilliRemaining = milliPerFile * remainingFileCount;
            final Duration remainingDuration = Duration.of(expectedMilliRemaining, ChronoUnit.MILLIS);
            return String.format("Uploading: %d/%d done ETA: %02d:%02d:%02d",
                    currentFileNumber,
                    fileCount,
                    remainingDuration.toHours(),
                    remainingDuration.toMinutesPart(),
                    remainingDuration.toSecondsPart());
        }
    }

    private String buildFilename(String name, String fileURLFromTorrent) {
        String fileEndingFromUrl = extractFileEndingFromUrl(fileURLFromTorrent);
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
        final String commandToRun = String.format("rclone copyurl '%s' '%s'", fileURLFromTorrent, destinationPath.replaceAll("'", ""));
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
            log.error(e.getMessage());
        }
        if (exitCode != 0) {
            log.error("upload failed: {}", destinationPath);
            return false;
        } else {
            return true;
        }
    }


    private boolean checkIfTorrentCanBeDownloaded(Torrent remoteTorrent) {
        return List.of("finished", "seeding", "ready to upload", "Ready").stream().anyMatch(status -> remoteTorrent.status.contains(status));
    }

}
