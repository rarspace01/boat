package pirateboat.multifileHoster;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pirateboat.torrent.HttpUser;
import pirateboat.torrent.Torrent;
import pirateboat.torrent.TorrentFile;
import pirateboat.torrent.TorrentHelper;
import pirateboat.utilities.HttpHelper;
import pirateboat.utilities.PropertiesHelper;

import java.time.Duration;
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
    public List<Torrent> getRemoteTorrents() {
        final List<Torrent> torrents = new ArrayList<>();
        String requestUrl = "https://api.alldebrid.com/v4/magnet/status?agent=pirateboat&apikey=" + PropertiesHelper.getProperty("alldebrid_apikey");
        String pageContent = httpHelper.getPage(requestUrl);
        JsonElement jsonRoot = JsonParser.parseString(pageContent);
        final JsonArray jsonMagnets = jsonRoot.getAsJsonObject().get("data").getAsJsonObject().get("magnets").getAsJsonArray();
        jsonMagnets.forEach(jsonElement -> {
            final Torrent torrent = new Torrent(getName());
            final JsonObject jsonTorrent = jsonElement.getAsJsonObject();
            torrent.remoteId = jsonTorrent.get("id").getAsString();
            torrent.name = jsonTorrent.get("filename").getAsString();
            torrent.size = (jsonTorrent.get("size").getAsLong() / 1024 / 1024) + "MB";
            torrent.lsize = TorrentHelper.extractTorrentSizeFromString(torrent);
            final double downloaded = jsonTorrent.get("downloaded").getAsLong();
            final double size = jsonTorrent.get("size").getAsLong();
            final double downloadSpeed = (jsonTorrent.get("downloadSpeed").getAsLong());
            final long remainingSeconds = (long) ((size - downloaded) / downloadSpeed);
            final Duration duration = Duration.ofSeconds(remainingSeconds);
            torrent.progress = String.format("%f", (downloaded / size));
            torrent.eta = String.format("ETA: %s",duration.toString());
            torrent.status = jsonTorrent.get("status").getAsString();
            torrents.add(torrent);
        });
        return torrents;
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
        String requestUrl = "https://api.alldebrid.com/v4/magnet/delete?agent=pirateboat&apikey=" + PropertiesHelper.getProperty("alldebrid_apikey") + "&id=" + remoteTorrent.remoteId;
        httpHelper.getPage(requestUrl);
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
