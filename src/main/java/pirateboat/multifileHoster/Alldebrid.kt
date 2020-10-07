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

import static java.lang.String.format;

public class Alldebrid extends HttpUser implements MultifileHoster {
    private static final Logger log = LoggerFactory.getLogger(Alldebrid.class);

    public Alldebrid(HttpHelper httpHelper) {
        super(httpHelper);
    }

    @Override
    public String addTorrentToQueue(Torrent toBeAddedTorrent) {
        String requestUrl = "https://api.alldebrid.com/v4/magnet/upload?agent=pirateboat&apikey=" + PropertiesHelper.getProperty("alldebrid_apikey") + "%s";
        String urlEncodedBrackets = TorrentHelper.urlEncode("[]");
        final String collected = "&magnets" + urlEncodedBrackets + "=" + TorrentHelper.urlEncode(toBeAddedTorrent.magnetUri);
        String checkUrl = format(requestUrl, collected);
        String pageContent = httpHelper.getPage(checkUrl);
        JsonElement jsonRoot = JsonParser.parseString(pageContent);
        final JsonElement status = jsonRoot.getAsJsonObject().get("status");
        return status != null ? status.getAsString() : "error";
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
            torrent.progress = format("%f", (downloaded / size));
            torrent.eta = format("ETA: %s", duration.toString());
            torrent.status = jsonTorrent.get("status").getAsString();
            torrents.add(torrent);
        });
        return torrents;
    }

    private Torrent getRemoteTorrentById(String remoteId) {
        String remoteIdString = remoteId == null ? "" : "&id=" + remoteId;
        String requestUrl = "https://api.alldebrid.com/v4/magnet/status?agent=pirateboat&apikey=" + PropertiesHelper.getProperty("alldebrid_apikey") + remoteIdString;
        String pageContent = httpHelper.getPage(requestUrl);
        Torrent torrent = null;
        JsonElement jsonRoot = JsonParser.parseString(pageContent);
        final JsonElement data = jsonRoot.getAsJsonObject().get("data");
        if (data != null) {
            torrent = new Torrent(getName());
            final JsonObject jsonTorrent = data.getAsJsonObject().get("magnets").getAsJsonObject();
            torrent.remoteId = jsonTorrent.get("id").getAsString();
            torrent.name = jsonTorrent.get("filename").getAsString();
            torrent.size = (jsonTorrent.get("size").getAsLong() / 1024 / 1024) + "MB";
            torrent.lsize = TorrentHelper.extractTorrentSizeFromString(torrent);
            final double downloaded = jsonTorrent.get("downloaded").getAsLong();
            final double size = jsonTorrent.get("size").getAsLong();
            final double downloadSpeed = (jsonTorrent.get("downloadSpeed").getAsLong());
            final long remainingSeconds = (long) ((size - downloaded) / downloadSpeed);
            final Duration duration = Duration.ofSeconds(remainingSeconds);
            torrent.progress = format("%f", (downloaded / size));
            torrent.eta = format("ETA: %s", duration.toString());
            torrent.status = jsonTorrent.get("status").getAsString();
            final JsonArray links = jsonTorrent.get("links").getAsJsonArray();
            if (remoteId != null) {
                torrent.fileList.clear();
                torrent.fileList.addAll(extractFiles(links));
            }
        }
        return torrent;
    }

    private List<TorrentFile> extractFiles(JsonArray links) {
        final ArrayList<TorrentFile> torrentFiles = new ArrayList<>();
        if (links != null) {
            links.forEach(jsonElement -> {
                final TorrentFile torrentFile = new TorrentFile();
                torrentFile.filesize = jsonElement.getAsJsonObject().get("size").getAsLong();
                torrentFile.name = jsonElement.getAsJsonObject().get("filename").getAsString();
                torrentFile.url = jsonElement.getAsJsonObject().get("link").getAsString();
                torrentFiles.add(torrentFile);
            });

        }
        torrentFiles.forEach(this::resolveDirectLink);
        return torrentFiles;
    }

    private void resolveDirectLink(final TorrentFile torrentFile) {
        final String baseUrl = "https://api.alldebrid.com/v4/link/unlock?agent=pirateboat&apikey=" + PropertiesHelper.getProperty("alldebrid_apikey") + "&link=%s";
        String requestUrl = String.format(baseUrl, TorrentHelper.urlEncode(torrentFile.url));
        String pageContent = httpHelper.getPage(requestUrl);
        JsonElement jsonRoot = JsonParser.parseString(pageContent);
        final JsonElement data = jsonRoot.getAsJsonObject().get("data");
        if (data != null) {
            torrentFile.url = data.getAsJsonObject().get("link").getAsString();
        }
    }

    @Override
    public void enrichCacheStateOfTorrents(List<Torrent> torrents) {
        String requestUrl = "https://api.alldebrid.com/v4/magnet/instant?agent=pirateboat&apikey=" + PropertiesHelper.getProperty("alldebrid_apikey") + "%s";
        String urlEncodedBrackets = TorrentHelper.urlEncode("[]");
        String collected = torrents.stream().map(Torrent::getTorrentId).collect(Collectors.joining("&magnets" + urlEncodedBrackets + "=", "&magnets" + urlEncodedBrackets + "=", ""));
        String checkUrl = format(requestUrl, collected);
        String pageContent = httpHelper.getPage(checkUrl);
        JsonElement jsonRoot = JsonParser.parseString(pageContent);
        if (jsonRoot == null || !jsonRoot.isJsonObject()) {
            log.error("couldn't retrieve cache for:" + checkUrl);
        } else {
            JsonElement response = jsonRoot.getAsJsonObject().get("data").getAsJsonObject().get("magnets");
            JsonArray responseArray = response.getAsJsonArray();
            AtomicInteger index = new AtomicInteger();
            if (responseArray.size() == torrents.size()) {
                responseArray.forEach(jsonElement -> {
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
        final Torrent remoteTorrent = getRemoteTorrentById(torrent.remoteId);
        return remoteTorrent.fileList;
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
