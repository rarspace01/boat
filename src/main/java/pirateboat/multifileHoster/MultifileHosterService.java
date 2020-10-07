package pirateboat.multifileHoster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pirateboat.torrent.HttpUser;
import pirateboat.torrent.Torrent;
import pirateboat.torrent.TorrentFile;
import pirateboat.utilities.HttpHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MultifileHosterService extends HttpUser {
    private static final Logger log = LoggerFactory.getLogger(MultifileHosterService.class);

    private final List<MultifileHoster> multifileHosterList = new ArrayList<>();

    @Autowired
    public MultifileHosterService(HttpHelper httpHelper) {
        super(httpHelper);
        multifileHosterList.add(new Premiumize(httpHelper));
        multifileHosterList.add(new Alldebrid(httpHelper));
    }

    public List<Torrent> getCachedStateOfTorrents(List<Torrent> returnResults) {
        multifileHosterList.forEach(multifileHoster -> multifileHoster.enrichCacheStateOfTorrents(returnResults));
        return returnResults;
    }

    public String addTorrentToQueue(Torrent torrent) {
        Torrent potentialCachedTorrentToDownload = getCachedStateOfTorrents(Collections.singletonList(torrent)).stream().findFirst().orElse(torrent);
        return multifileHosterList.stream()
                .filter(multifileHoster -> multifileHoster.getName().equals(potentialCachedTorrentToDownload.cached.stream().findFirst().orElse("")))
                .min(Comparator.comparingInt(MultifileHoster::getPrio))
                .orElse(multifileHosterList.get(0))
                .addTorrentToQueue(torrent);
    }

    public List<Torrent> getRemoteTorrents() {
        return multifileHosterList.stream()
                .flatMap(multifileHoster -> multifileHoster.getRemoteTorrents().stream())
                .collect(Collectors.toList());
    }

    public boolean isSingleFileDownload(Torrent torrentToBeDownloaded) {
        List<TorrentFile> tfList = getFilesFromTorrent(torrentToBeDownloaded);
        long sumFileSize = 0L;
        long biggestFileYet = 0L;
        for (TorrentFile tf : tfList) {
            if (tf.filesize > biggestFileYet) {
                biggestFileYet = tf.filesize;
            }
            sumFileSize += tf.filesize;
        }
        // if maxfilesize >90% sumSize --> Singlefile
        return biggestFileYet > (0.9d * sumFileSize);
    }

    public List<TorrentFile> getFilesFromTorrent(Torrent torrentToBeDownloaded) {
        final Optional<MultifileHoster> hoster = multifileHosterList.stream().filter(multifileHoster -> multifileHoster.getName().equals(torrentToBeDownloaded.source)).findFirst();
        if (hoster.isPresent()) {
            return hoster.get().getFilesFromTorrent(torrentToBeDownloaded);
        } else {
            return new ArrayList<>();
        }
    }

    public String getMainFileURLFromTorrent(Torrent torrentToBeDownloaded) {
        List<TorrentFile> tfList = getFilesFromTorrent(torrentToBeDownloaded);
        String remoteURL = null;
        // iterate over and check for One File Torrent
        long biggestFileYet = 0;
        for (TorrentFile tf : tfList) {
            if (tf.filesize > biggestFileYet) {
                biggestFileYet = tf.filesize;
                remoteURL = tf.url;
            }
        }
        return remoteURL;
    }

    public void delete(Torrent torrent) {
        final Optional<MultifileHoster> hoster = multifileHosterList.stream().filter(multifileHoster -> multifileHoster.getName().equals(torrent.source)).findFirst();
        if (hoster.isPresent()) {
            hoster.get().delete(torrent);
        } else {
            log.error("Deletion of Torrent not possible: {}", torrent.toString());
        }
    }
}
