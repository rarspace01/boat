package torrent;

import java.util.List;

/**
 * Created by denis on 02/10/2016.
 */
public interface TorrentSearchEngine {

    List<Torrent> searchTorrents(String torrentname);

    Torrent suggestATorrent(List<Torrent> inputList);
}
