package torrent;

import java.util.List;

/**
 * Created by deha on 02/10/2016.
 */
public interface TorrentSearchEngine {

    List<Torrent> searchTorrents(String torrentname);

}
