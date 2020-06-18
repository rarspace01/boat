package pirateboat;

import pirateboat.info.TheFilmDataBaseService;
import pirateboat.info.TorrentMetaService;
import pirateboat.torrent.Premiumize;
import pirateboat.torrent.Torrent;
import pirateboat.torrent.TorrentFile;
import pirateboat.torrent.TorrentSearchEngineService;
import pirateboat.utilities.HttpHelper;
import pirateboat.utilities.ProcessUtil;
import pirateboat.utilities.PropertiesHelper;
import pirateboat.utilities.StreamGobbler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

@Component
public class DownloadMonitor {

    private final TorrentSearchEngineService torrentSearchEngineService;
    private final TorrentMetaService torrentMetaService;

    private static final int SECONDS_BETWEEN_DOWNLOAD_POLLING = 30;
    private static final int SECONDS_BETWEEN_SEARCH_ENGINE_POLLING = 60;
    private static final int SECONDS_BETWEEN_CLEAR_TRANSFER_POLLING = 3600;
    private static final Logger log = LoggerFactory.getLogger(DownloadMonitor.class);

    private boolean isDownloadInProgress = false;
    private Premiumize premiumize;
    private final TheFilmDataBaseService theFilmDataBaseService;
    private Boolean isRcloneInstalled;

    public DownloadMonitor(TorrentSearchEngineService torrentSearchEngineService,
                           HttpHelper httpHelper,
                           TheFilmDataBaseService theFilmDataBaseService,
                           TorrentMetaService torrentMetaService) {
        this.torrentSearchEngineService = torrentSearchEngineService;
        this.torrentMetaService = torrentMetaService;
        this.premiumize = new Premiumize(httpHelper, theFilmDataBaseService);
        this.theFilmDataBaseService = theFilmDataBaseService;
    }

    @Scheduled(fixedRate = SECONDS_BETWEEN_SEARCH_ENGINE_POLLING * 1000)
    public void refreshTorrentSearchEngines() {
        log.debug("refreshTorrentSearchEngines()");
        torrentSearchEngineService.refreshTorrentSearchEngines();
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
        premiumize.getRemoteTorrents().stream().filter(torrent -> torrent.status.contains("error")).forEach(torrent -> premiumize.delete(torrent));
    }

    private void checkForDownloadableTorrentsAndDownloadTheFirst() {
        final Torrent torrentToBeDownloaded = getTorrentToBeDownloaded();
        if(torrentToBeDownloaded != null) {
                isDownloadInProgress = true;
                try {
                    if (premiumize.isSingleFileDownload(torrentToBeDownloaded)) {
                        updateUploadStatus(torrentToBeDownloaded, 0, 1);
                        String fileURLFromTorrent = premiumize.getMainFileURLFromTorrent(torrentToBeDownloaded);
                        if (torrentToBeDownloaded.name.contains("magnet:?")) {
                            torrentToBeDownloaded.name = extractFileNameFromUrl(fileURLFromTorrent);
                        }
                        rcloneDownloadFileToGdrive(fileURLFromTorrent, PropertiesHelper.getProperty("rclonedir") + "/" + buildFilename(torrentToBeDownloaded.name, fileURLFromTorrent));
                        updateUploadStatus(torrentToBeDownloaded, 1, 1);
                    } else {
                        List<TorrentFile> filesFromTorrent = premiumize.getFilesFromTorrent(torrentToBeDownloaded);
                        int currentFileNumber = 0;
                        int maxFileCount = filesFromTorrent.size();
                        for (TorrentFile torrentFile : filesFromTorrent) {
                            // check fileSize to get rid of samples and NFO files?
                            updateUploadStatus(torrentToBeDownloaded, currentFileNumber, maxFileCount);
                            rcloneDownloadFileToGdrive(torrentFile.url, PropertiesHelper.getProperty("rclonedir") + "/multipart/" + torrentToBeDownloaded.name + "/" + torrentFile.name);
                            currentFileNumber++;
                            updateUploadStatus(torrentToBeDownloaded, currentFileNumber, maxFileCount);
                        }
                    }
                    premiumize.delete(torrentToBeDownloaded);
                } catch (Exception exception) {
                    log.error(String.format("Couldn't download Torrent: %s",torrentToBeDownloaded),exception);
                } finally {
                    isDownloadInProgress = false;
                }
        }
    }

    public Torrent getTorrentToBeDownloaded() {
        torrentMetaService.refreshTorrents();
        List<Torrent> activeTorrents = torrentMetaService.getActiveTorrents();
        return activeTorrents.stream().filter(this::checkIfTorrentCanBeDownloaded).findFirst().orElse(null);
    }

    private void updateUploadStatus(Torrent torrentToBeDownloaded, int currentFileNumber, int fileCount) {
        torrentToBeDownloaded.status = getUploadStatusString(currentFileNumber, fileCount);
        torrentMetaService.updateTorrent(torrentToBeDownloaded);
    }

    private String getUploadStatusString(int currentFileNumber, int fileCount) {
        return String.format("Uploading: %d/%d done", currentFileNumber, fileCount);
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

        return foundMatch;
    }

    private void rcloneDownloadFileToGdrive(String fileURLFromTorrent, String destinationPath) {
        log.info("D>[" + destinationPath + "]");
        ProcessBuilder builder = new ProcessBuilder();
        final String commandToRun = String.format("rclone copyurl '%s' '%s'", fileURLFromTorrent, destinationPath);
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
            e.printStackTrace();
        }
        assert exitCode == 0;
    }

    private void downloadFile(String fileURLFromTorrent, String localPath) throws IOException {
        log.info("About to download:" + fileURLFromTorrent + "\nto: " + localPath);
        HttpHelper.downloadFileToPath(fileURLFromTorrent, localPath);
    }


    private String addFilenameIfNotYetPresent(String name, String mainFileURLFromTorrent) {
        if (name.matches(".+[.].*]")) {
            return "";
        } else {
            return mainFileURLFromTorrent.substring(mainFileURLFromTorrent.lastIndexOf("/"));
        }
    }

    private boolean checkIfTorrentCanBeDownloaded(Torrent remoteTorrent) {
        return List.of("finished", "seeding", "ready to upload").stream().anyMatch(status -> remoteTorrent.status.contains(status));
    }

}
