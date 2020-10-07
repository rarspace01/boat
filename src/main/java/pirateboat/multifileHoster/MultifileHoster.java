package pirateboat.multifileHoster;

import pirateboat.torrent.Torrent;
import pirateboat.torrent.TorrentFile;

import java.util.ArrayList;
import java.util.List;

public interface MultifileHoster {

    String addTorrentToQueue(Torrent toBeAddedTorrent);
    List<Torrent> getRemoteTorrents();
    void enrichCacheStateOfTorrents(List<Torrent> torrents);
    void delete(Torrent remoteTorrent);
    List<TorrentFile> getFilesFromTorrent(Torrent torrent);
    int getPrio();
    String getName();
}
