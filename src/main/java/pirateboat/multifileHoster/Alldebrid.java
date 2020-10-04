package pirateboat.multifileHoster;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pirateboat.torrent.HttpUser;
import pirateboat.torrent.Torrent;
import pirateboat.torrent.TorrentFile;
import pirateboat.torrent.TorrentHelper;
import pirateboat.utilities.HttpHelper;
import pirateboat.utilities.PropertiesHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Alldebrid extends HttpUser implements MultifileHoster {
    private static final Logger log = LoggerFactory.getLogger(Alldebrid.class);

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
    public void enrichCacheStateOfTorrents(List<Torrent> torrents) {
        String requestUrl = "https://api.alldebrid.com/v4/magnet/instant?agent=pirateboat&apikey=" + PropertiesHelper.getProperty("alldebrid_apikey") + "%s";
        String urlEncodedBrackets = TorrentHelper.urlEncode("[]");
        String collected = torrents.stream().map(Torrent::getTorrentId).collect(Collectors.joining("&magnets" + urlEncodedBrackets + "=", "&magnets" + urlEncodedBrackets + "=", ""));
        String checkUrl = String.format(requestUrl, collected);
        String pageContent = httpHelper.getPage(checkUrl);
        JsonElement jsonRoot = JsonParser.parseString(pageContent);
        if (jsonRoot == null || !jsonRoot.isJsonObject()) {
            log.error("couldn't retrieve cache for:" + checkUrl);
            log.error(pageContent);
        } else {
            JsonElement reponse = jsonRoot.getAsJsonObject().get("data").getAsJsonObject().get("magnets");
            JsonArray reponseArray = reponse.getAsJsonArray();
            AtomicInteger index = new AtomicInteger();
            if (reponseArray.size() == torrents.size()) {
                reponseArray.forEach(jsonElement -> {
                    if (jsonElement.getAsJsonObject().get("instant").getAsBoolean()) {
                        torrents.get(index.get()).cached.add(this.getClass().getSimpleName());
                    }
                    index.getAndIncrement();
                });
            }
        }
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

    @Override
    public int getPrio() {
        return 0;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }
}
