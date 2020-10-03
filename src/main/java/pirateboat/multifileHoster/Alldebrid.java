package pirateboat.multifileHoster;

import pirateboat.torrent.HttpUser;
import pirateboat.torrent.Torrent;
import pirateboat.torrent.TorrentFile;
import pirateboat.utilities.HttpHelper;

import java.util.ArrayList;
import java.util.List;

public class Alldebrid extends HttpUser implements MultifileHoster {

    public Alldebrid(HttpHelper httpHelper) {
        super(httpHelper);
    }

    @Override
    public String addTorrentToQueue(Torrent toBeAddedTorrent) {
        return null;
    }

    @Override
    public ArrayList<Torrent> getRemoteTorrents() {
        return null;
    }

    @Override
    public boolean isSingleFileDownload(Torrent remoteTorrent) {
        return false;
    }

    @Override
    public List<Torrent> getCacheStateOfTorrents(List<Torrent> torrents) {
        return null;
    }

    @Override
    public void delete(Torrent remoteTorrent) {

    }

    @Override
    public List<TorrentFile> getFilesFromTorrent(Torrent torrent) {
        return null;
    }

    @Override
    public String getMainFileURLFromTorrent(Torrent torrent) {
        return null;
    }
}
