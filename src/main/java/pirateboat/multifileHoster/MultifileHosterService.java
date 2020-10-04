package pirateboat.multifileHoster;

import org.springframework.stereotype.Service;
import pirateboat.torrent.HttpUser;
import pirateboat.torrent.Torrent;
import pirateboat.utilities.HttpHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class MultifileHosterService extends HttpUser {

    private final List<MultifileHoster> multifileHosterList = new ArrayList<>();

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
}
