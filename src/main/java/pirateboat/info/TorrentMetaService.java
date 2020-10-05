package pirateboat.info;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pirateboat.multifileHoster.MultifileHosterService;
import pirateboat.torrent.Torrent;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TorrentMetaService {

    private static final Logger log = LoggerFactory.getLogger(TorrentMetaService.class);
    private final MultifileHosterService multifileHosterService;


    public List<Torrent> getActiveTorrents() {
        return activeTorrents;
    }

    private List<Torrent> activeTorrents = new ArrayList<>();

    @Autowired
    public TorrentMetaService(MultifileHosterService multifileHosterService) {
        this.multifileHosterService = multifileHosterService;
    }

    public void refreshTorrents() {
        List<Torrent> remoteTorrents = multifileHosterService.getRemoteTorrents();
        List<Torrent> newTorrentList = remoteTorrents.stream().peek(remoteTorrent -> activeTorrents.forEach(cachedTorrent -> {
            if (cachedTorrent.getTorrentId().equals(remoteTorrent.getTorrentId())) {
                if (List.of("finished", "seeding", "Ready").stream().anyMatch(status -> remoteTorrent.status.contains(status))) {
                    remoteTorrent.status = cachedTorrent.status;
                }
            } else {
                if (List.of("finished", "seeding", "Ready").stream().anyMatch(status -> remoteTorrent.status.contains(status))) {
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
