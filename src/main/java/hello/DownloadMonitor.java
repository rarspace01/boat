package hello;

import hello.info.TheFilmDataBaseService;
import hello.info.TorrentMetaService;
import hello.torrent.Premiumize;
import hello.torrent.Torrent;
import hello.torrent.TorrentFile;
import hello.torrent.TorrentSearchEngineService;
import hello.utilities.HttpHelper;
import hello.utilities.PropertiesHelper;
import hello.utilities.StreamGobbler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DownloadMonitor {

    private final TorrentSearchEngineService torrentSearchEngineService;
    private final TorrentMetaService torrentMetaService;

    private static final int SECONDS_BETWEEN_DOWNLOAD_POLLING = 30;
    private static final int SECONDS_BETWEEN_SEARCH_ENGINE_POLLING = 60;
    private static final Logger log = LoggerFactory.getLogger(DownloadMonitor.class);

    private boolean isDownloadInProgress = false;
    private Premiumize premiumize;
    private final TheFilmDataBaseService theFilmDataBaseService;

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
        torrentSearchEngineService.refreshTorrentSearchEngines();
    }

    @Scheduled(fixedRate = SECONDS_BETWEEN_DOWNLOAD_POLLING * 1000)
    public void checkForDownloadableTorrents() {
        log.debug("checkForDownloadableTorrents()");
        if (!isDownloadInProgress) {
            checkForDownloadbleTorrentsAndDownloadTheFirst();
        }
    }

    private void checkForDownloadbleTorrentsAndDownloadTheFirst() {
        torrentMetaService.refreshTorrents();
        boolean returnToMonitor = false;
        for (Torrent remoteTorrent : torrentMetaService.getActiveTorrents()) {
            if (checkIfTorrentCanBeDownloaded(remoteTorrent) && !returnToMonitor) {
                isDownloadInProgress = true;

                Runnable runnable = () -> {
                    log.info("determining MediaType");
                    // try to determine MediaType for torrent to download
                    log.info(String.format("determineMediaType: %s", theFilmDataBaseService.determineMediaType(remoteTorrent)));
                };
                Thread thread = new Thread(runnable);
                thread.start();
                //createDownloadFolderIfNotExists(remoteTorrent);

                // check if SingleFileDownload
                if (premiumize.isSingleFileDownload(remoteTorrent)) {
                    remoteTorrent.status = "Uploading: 0/1 done";
                    torrentMetaService.updateTorrent(remoteTorrent);
                    String fileURLFromTorrent = premiumize.getMainFileURLFromTorrent(remoteTorrent);
                    //downloadFile(fileURLFromTorrent, localPath);
                    rcloneDownloadFileToGdrive(fileURLFromTorrent, PropertiesHelper.getProperty("rclonedir") + "/" + buildFilename(remoteTorrent.name, fileURLFromTorrent));
                    //uploadFile()
                    // cleanup afterwards
                    remoteTorrent.status = "Uploading: 1/1 done";
                    torrentMetaService.updateTorrent(remoteTorrent);
                    premiumize.delete(remoteTorrent);
                } else { // start multifile download
                    // download every file
                    List<TorrentFile> filesFromTorrent = premiumize.getFilesFromTorrent(remoteTorrent);
                    int fileNumber = 0;
                    int fileCount = filesFromTorrent.size();
                    for (TorrentFile torrentFile : filesFromTorrent) {
                        // check filesize to get rid of samples and NFO files?
                        remoteTorrent.status = String.format("Uploading: %d/%d done",fileNumber,fileCount);
                        torrentMetaService.updateTorrent(remoteTorrent);
                        // downloadFile(torrentFile.url, localPath);
                        rcloneDownloadFileToGdrive(torrentFile.url, PropertiesHelper.getProperty("rclonedir") + "/multipart/" + remoteTorrent.name + "/" + torrentFile.name);
                        fileNumber++;
                        remoteTorrent.status = String.format("Uploading: %d/%d done",fileNumber,fileCount);
                        torrentMetaService.updateTorrent(remoteTorrent);
                    }
                    // cleanup afterwards
                    premiumize.delete(remoteTorrent);
                }
                isDownloadInProgress = false;
                returnToMonitor = true;
            }
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

    private String extractFileNameFromTorrent(TorrentFile torrentFile) {
        return extractFileNameFromUrl(torrentFile.name);
    }

    private String extractFileNameFromUrl(String fileURLFromTorrent) {
        Pattern pattern = Pattern.compile("([\\w.%\\-]+)$");
        String foundMatch = null;
        Matcher matcher = pattern.matcher(fileURLFromTorrent);

        while (matcher.find()) {
            foundMatch = matcher.group();
        }
        if (foundMatch != null) {
            foundMatch.replaceAll("%20", ".");
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
        log.info("D[" + fileURLFromTorrent + "]");
        ProcessBuilder builder = new ProcessBuilder();
        builder.command("rclone", "copyurl", fileURLFromTorrent, destinationPath);
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
        log.info("DF[" + destinationPath + "]");
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
        boolean remoteStatusIsFinished = remoteTorrent.status.contains("finished") || remoteTorrent.status.contains("seeding");
        boolean isAlreadyDownloaded = new File("./downloads/" + remoteTorrent.name).exists();
        return remoteStatusIsFinished && !isAlreadyDownloaded;
    }

}
