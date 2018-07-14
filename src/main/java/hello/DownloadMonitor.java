package hello;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import torrent.Premiumize;
import torrent.Torrent;
import torrent.TorrentFile;
import utilities.HttpHelper;
import utilities.PropertiesHelper;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class DownloadMonitor {
    private static final int SECONDS_BETWEEN_POLLING = 30;
    private static final Logger log = LoggerFactory.getLogger(DownloadMonitor.class);

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
    private boolean isDownloadInProgress = false;
    private Premiumize premiumize = new Premiumize();

    @Scheduled(fixedRate = SECONDS_BETWEEN_POLLING * 1000)
    public void checkForDownloadableTorrents() {
        log.info("The time is now {%s}", dateFormat.format(new Date()));
        this.premiumize = new Premiumize();
        if (!isDownloadInProgress) {
            checkForDownloadbleTorrentsAndDownloadTheFirst();
        }
    }

    private void checkForDownloadbleTorrentsAndDownloadTheFirst() {
        ArrayList<Torrent> remoteTorrents = premiumize.getRemoteTorrents();
        boolean returnToMonitor = false;
        for (Torrent remoteTorrent : remoteTorrents) {
            if (checkIfTorrentCanBeDownloaded(remoteTorrent) && !returnToMonitor) {
                try {
                    isDownloadInProgress = true;
                    createDownloadFolderIfNotExists(remoteTorrent);

                    // check if SingleFileDownload
                    if (premiumize.isSingleFileDownload(remoteTorrent)) {
                        String fileURLFromTorrent = premiumize.getMainFileURLFromTorrent(remoteTorrent);
                        String localPath = PropertiesHelper.getProperty("downloaddir") + remoteTorrent.name + addFilenameIfNotYetPresent(remoteTorrent.name, fileURLFromTorrent);
                        downloadFile(fileURLFromTorrent, localPath);
                        // cleanup afterwards
                        premiumize.delete(remoteTorrent);
                    } else { // start multifile download
                        // download every file
                        List<TorrentFile> filesFromTorrent = premiumize.getFilesFromTorrent(remoteTorrent);
                        for (TorrentFile torrentFile : filesFromTorrent) {
                            // check filesize to get rid of samples and NFO files?
                            String localPath = PropertiesHelper.getProperty("downloaddir") + remoteTorrent.name + addFilenameIfNotYetPresent(remoteTorrent.name, torrentFile.url);
                            downloadFile(torrentFile.url, localPath);
                        }
                        // cleanup afterwards
                        premiumize.delete(remoteTorrent);
                    }
                    isDownloadInProgress = false;
                    returnToMonitor = true;
                } catch (IOException e) {
                    isDownloadInProgress = false;
                    e.printStackTrace();
                }
            }
        }
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

    private boolean createDownloadFolderIfNotExists(Torrent remoteTorrent) {
        if (remoteTorrent.name.matches(".+[.].*]")) {
            return new File(PropertiesHelper.getProperty("downloaddir")).mkdirs();
        } else {
            return new File(PropertiesHelper.getProperty("downloaddir") + remoteTorrent.name).mkdirs();
        }
    }

    private boolean checkIfTorrentCanBeDownloaded(Torrent remoteTorrent) {
        boolean remoteStatusIsFinished = remoteTorrent.status.contains("finished");
        boolean isAlreadyDownloaded = new File("./downloads/" + remoteTorrent.name).exists();
        return remoteStatusIsFinished && !isAlreadyDownloaded;
    }
}
