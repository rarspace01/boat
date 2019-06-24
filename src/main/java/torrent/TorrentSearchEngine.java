package torrent;

import java.util.List;

public interface TorrentSearchEngine {

    List<Torrent> searchTorrents(String torrentname);

    Torrent suggestATorrent(List<Torrent> inputList);
}
