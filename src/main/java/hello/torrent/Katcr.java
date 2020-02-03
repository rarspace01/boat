package hello.torrent;

import hello.utilities.HttpHelper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Katcr extends HttpUser implements TorrentSearchEngine {

    Katcr(HttpHelper httpHelper) {
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
        return String.format(getBaseUrl() + "/katsearch/page/1/%s", URLEncoder.encode(searchName, StandardCharsets.UTF_8));
    }

    @Override
    public String getBaseUrl() {
        return "https://katcr.co";
    }

    @Override
    public String getSearchPage() {
        return buildSearchUrl("test");
    }

    private List<Torrent> parseTorrentsOnResultPage(String pageContent, String searchName) {
        ArrayList<Torrent> torrentList = new ArrayList<>();

        Document doc = Jsoup.parse(pageContent);

        Elements torrentListOnPage = doc.select(".table > tbody > tr");

        if (torrentListOnPage != null) {
            for (Element torrent : torrentListOnPage) {
                Torrent tempTorrent = new Torrent();
                if (torrent.childNodeSize() > 0) {
                    torrent.children().forEach(element -> {

                        if (element.getElementsByClass("torrents_table__torrent_title").size() > 0) {
                            //extract name
                            tempTorrent.name = element.getElementsByClass("torrents_table__torrent_title").get(0).text();
                        }
                        if (element.getElementsByAttributeValueMatching("href", "magnet:").size() > 0) {
                            //extract magneturi
                            tempTorrent.magnetUri = element.getElementsByAttributeValueMatching("href", "magnet:").attr("href").trim();
                        }
                        if (element.getElementsByAttributeValueMatching("data-title", "Size").size() > 0) {
                            tempTorrent.size = TorrentHelper.cleanNumberString(element.getElementsByAttributeValueMatching("data-title", "Size").text().trim());
                            tempTorrent.lsize = TorrentHelper.extractTorrentSizeFromString(tempTorrent);
                        }
                        if (element.getElementsByAttributeValueMatching("data-title", "Seed").size() > 0) {
                            tempTorrent.seeder = Integer.parseInt(TorrentHelper.cleanNumberString(element.getElementsByAttributeValueMatching("data-title", "Seed").text().trim()));
                        }
                        if (element.getElementsByAttributeValueMatching("data-title", "Leech").size() > 0) {
                            tempTorrent.leecher = Integer.parseInt(TorrentHelper.cleanNumberString(element.getElementsByAttributeValueMatching("data-title", "Leech").text().trim()));
                        }
                    });
                }

                // evaluate result
                TorrentHelper.evaluateRating(tempTorrent, searchName);
                if (TorrentHelper.isValidTorrent(tempTorrent)) {
                    torrentList.add(tempTorrent);
                }
            }
        }
        return torrentList;
    }

    @Override
    public Torrent suggestATorrent(List<Torrent> inputList) {
        return inputList.stream().max(TorrentHelper.torrentSorter).orElse(null);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

}
