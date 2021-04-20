package pirateboat.torrent;

import com.google.gson.JsonElement;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import pirateboat.utilities.HttpHelper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Zooqle extends HttpUser implements TorrentSearchEngine {

    Zooqle(HttpHelper httpHelper) {
        super(httpHelper);
    }

    @Override
    public List<Torrent> searchTorrents(String searchName) {

        CopyOnWriteArrayList<Torrent> torrentList = new CopyOnWriteArrayList<>();

        String resultString = null;
        resultString = httpHelper.getPage(buildSearchUrl(searchName));

        torrentList.addAll(parseTorrentsOnResultPage(resultString, searchName));
        torrentList.sort(TorrentHelper.torrentSorter);
        return torrentList;
    }

    private String buildSearchUrl(String searchName) {
        return String.format("%s/search?q=%s&fmt=rss", getBaseUrl(), URLEncoder.encode(searchName, StandardCharsets.UTF_8));
    }

    @Override
    public String getBaseUrl() {
        return "https://zooqle.com";
    }

    private List<Torrent> parseTorrentsOnResultPage(String pageContent, String searchName) {
        ArrayList<Torrent> torrentList = new ArrayList<>();

        Document doc = Jsoup.parse(pageContent);
        Elements torrentListOnPage = doc.select("item");

        if (torrentListOnPage == null) {
            return torrentList;
        }
        JsonElement results = null;
        if (results == null) {
            return torrentList;
        }


        torrentListOnPage.forEach(torrentElement -> {
            Torrent tempTorrent = new Torrent(toString());
            /*tempTorrent.name = jsonTorrent.get("title").getAsString() + " " + jsonTorrent.get("year").getAsInt();
            JsonObject bestTorrentSource = retrieveBestTorrent(jsonTorrent.get("torrents").getAsJsonArray());

            //tempTorrent.magnetUri = bestTorrentSource.get("url").getAsString();
            tempTorrent.magnetUri = TorrentHelper.buildMagnetUriFromHash(bestTorrentSource.get("hash").getAsString().toLowerCase(), tempTorrent.name);
            tempTorrent.seeder = bestTorrentSource.get("seeds").getAsInt();
            tempTorrent.leecher = bestTorrentSource.get("peers").getAsInt();
            tempTorrent.size = bestTorrentSource.get("size").getAsString();
            tempTorrent.lsize = bestTorrentSource.get("size_bytes").getAsLong() / 1024.0f / 1024.0f;
            tempTorrent.date = new Date(bestTorrentSource.get("date_uploaded_unix").getAsLong() * 1000);*/

            TorrentHelper.evaluateRating(tempTorrent, searchName);
            if (TorrentHelper.isValidTorrent(tempTorrent)) {
                torrentList.add(tempTorrent);
            }
        });

        return torrentList;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

}
