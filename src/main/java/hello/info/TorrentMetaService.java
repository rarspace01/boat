package hello.info;

import hello.torrent.Premiumize;
import hello.torrent.Torrent;
import hello.utilities.HttpHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TorrentMetaService {

    private final Premiumize premiumize;

    private static final Logger log = LoggerFactory.getLogger(TorrentMetaService.class);


    public List<Torrent> getActiveTorrents() {
        return activeTorrents;
    }

    private List<Torrent> activeTorrents = null;

    @Autowired
    public TorrentMetaService(HttpHelper httpHelper, TheFilmDataBaseService theFilmDataBaseService) {
        this.premiumize = new Premiumize(httpHelper, theFilmDataBaseService);
    }

    public void refreshTorrents() {
        ArrayList<Torrent> remoteTorrents = premiumize.getRemoteTorrents();
        List<Torrent> newTorrentList = remoteTorrents.stream().peek(remoteTorrent -> activeTorrents.forEach(cachedTorrent -> {
            if(cachedTorrent.getTorrentId().equals(remoteTorrent.getTorrentId())) {
                if (remoteTorrent.status.equals("finished")) {
                    remoteTorrent.status = cachedTorrent.status;
                }
            } else {
                if (remoteTorrent.status.equals("finished")) {
                    remoteTorrent.status = "ready to upload";
                }
            }
        })).collect(Collectors.toList());
        activeTorrents = new ArrayList<>();
        activeTorrents.addAll(newTorrentList);
    }

    public void updateTorrent(Torrent torrentUpdate) {
        if (torrentUpdate != null) {
            activeTorrents.forEach(torrent -> {
                if (torrentUpdate.getTorrentId().equals(torrent.getTorrentId())) {
                    torrent.status = torrentUpdate.status;
                }
            });
        }
    }

}
