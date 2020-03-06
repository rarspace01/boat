package hello.info;

import hello.torrent.Premiumize;
import hello.torrent.Torrent;
import hello.utilities.HttpHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TorrentMetaService {

    private final Premiumize premiumize;

    public List<Torrent> getActiveTorrents() {
        return activeTorrents;
    }

    private List<Torrent> activeTorrents = new ArrayList<>();

    @Autowired
    public TorrentMetaService(HttpHelper httpHelper, TheFilmDataBaseService theFilmDataBaseService) {
        this.premiumize = new Premiumize(httpHelper, theFilmDataBaseService);
    }

    public void refreshTorrents() {
        ArrayList<Torrent> remoteTorrents = premiumize.getRemoteTorrents();
        // deleteNonexisting remoteTorrent
        activeTorrents.retainAll(remoteTorrents);
        List<Torrent> newTorrentList = remoteTorrents.stream().map(remoteTorrent -> {
            int indexOfRemoteTorrent = activeTorrents.indexOf(remoteTorrent);
            if (indexOfRemoteTorrent == -1) {
                return remoteTorrent;
            }
            Torrent activeTorrent = activeTorrents.get(indexOfRemoteTorrent);
            if (remoteTorrent.status.equals("finished")) {
                remoteTorrent.status = activeTorrent.status;
            }
            return remoteTorrent;
        }).collect(Collectors.toList());
        activeTorrents.clear();
        activeTorrents.addAll(newTorrentList);
    }

    public void updateTorrent(Torrent torrentUpdate) {
        if (torrentUpdate != null) {
            int indexOf = activeTorrents.indexOf(torrentUpdate);
            if(indexOf != -1) {
                activeTorrents.set(indexOf,torrentUpdate);
            }
        }
    }

}
