package hello;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import torrent.Premiumize;
import torrent.Torrent;
import utilities.PropertiesHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

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
        for (Torrent remoteTorrent : remoteTorrents) {
            if (checkIfTorrentCanBeDownloaded(remoteTorrent)) {
                try {
                    isDownloadInProgress = true;
                    createDownloadFolderIfNotExists();
                    log.info("About to download:" + remoteTorrent.toString());
                    String mainFileURLFromTorrent = premiumize.getMainFileURLFromTorrent(remoteTorrent);
                    if (mainFileURLFromTorrent != null) {
                        URL website = new URL(mainFileURLFromTorrent);
                        ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                        FileOutputStream fos = new FileOutputStream(PropertiesHelper.getProperty("downloaddir") + remoteTorrent.name);
                        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                        //FileUtils.copyURLToFile(, new File(PropertiesHelper.getProperty("downloaddir") + remoteTorrent.name));
                    } else {
                        log.info("sorry I'm not yet smart enough to handle multi file torrent downloads");
                    }
                    isDownloadInProgress = false;
                } catch (IOException e) {
                    isDownloadInProgress = false;
                    e.printStackTrace();
                }
            }
        }
    }

    private void createDownloadFolderIfNotExists() {
        if (!new File("./downloads/").exists()) {
            new File("./downloads/").mkdirs();
        }
    }

    private boolean checkIfTorrentCanBeDownloaded(Torrent remoteTorrent) {
        boolean remoteStatusIsFinished = remoteTorrent.status.contains("finished");
        boolean isAlreadyDownloaded = new File("./downloads/" + remoteTorrent.name).exists();
        return remoteStatusIsFinished && !isAlreadyDownloaded;
    }
}
