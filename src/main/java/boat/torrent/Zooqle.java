package boat.torrent;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import boat.utilities.HttpHelper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

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

        torrentListOnPage.forEach(torrentElement -> {
            Torrent tempTorrent = new Torrent(toString());

            tempTorrent.name = torrentElement.getElementsByTag("title").text();
            tempTorrent.magnetUri = TorrentHelper.buildMagnetUriFromHash(torrentElement.getElementsByTag("torrent").first().getElementsByTag("infohash").text(), tempTorrent.name);
            tempTorrent.seeder = Integer.parseInt(torrentElement.getElementsByTag("torrent:seeds").text());
            tempTorrent.leecher = Integer.parseInt(torrentElement.getElementsByTag("torrent:peers").text());
            tempTorrent.lsize =
                Long.parseLong(torrentElement.getElementsByTag("torrent:contentlength").text()) / 1024.0f / 1024.0f;
            tempTorrent.size = TorrentHelper.humanReadableByteCountBinary((long) (tempTorrent.lsize * 1024.0 * 1024.0));

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
