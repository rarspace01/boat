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

public class MagnetDL extends HttpUser implements TorrentSearchEngine {

    MagnetDL(HttpHelper httpHelper) {
        super(httpHelper);
    }

    @Override
    public List<Torrent> searchTorrents(String searchName) {

        CopyOnWriteArrayList<Torrent> torrentList = new CopyOnWriteArrayList<>();

        String resultString = httpHelper.getPage(buildSearchUrl(searchName));

        torrentList.addAll(parseTorrentsOnResultPage(resultString, searchName));
        torrentList.sort(TorrentHelper.torrentSorter);
        return torrentList;
    }

    private String buildSearchUrl(String searchName) {
        final String encodedSearch = URLEncoder.encode(searchName, StandardCharsets.UTF_8);
        return String.format("%s/%s/%s/se/desc/", getBaseUrl(), encodedSearch.toLowerCase().charAt(0), encodedSearch);
    }

    @Override
    public String getBaseUrl() {
        return "https://www.magnetdl.com";
    }

    private List<Torrent> parseTorrentsOnResultPage(String pageContent, String searchName) {
        ArrayList<Torrent> torrentList = new ArrayList<>();

        Document doc = Jsoup.parse(pageContent);
        Elements torrentListOnPage = doc.select("tr:has(.m)");

        if (torrentListOnPage == null) {
            return torrentList;
        }

        torrentListOnPage.forEach(torrentElement -> {
            Torrent tempTorrent = new Torrent(toString());

            tempTorrent.name = torrentElement.getElementsByClass("n").first().getElementsByAttribute("title")
                .attr("title");
            tempTorrent.magnetUri = torrentElement.getElementsByClass("m").first().getElementsByAttribute("href")
                .first().attr("href");
            tempTorrent.seeder = Integer.parseInt(torrentElement.getElementsByClass("s").first().text());
            tempTorrent.leecher = Integer.parseInt(torrentElement.getElementsByClass("l").first().text());
            tempTorrent.lsize = TorrentHelper.extractTorrentSizeFromString(torrentElement.child(5).text());
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
