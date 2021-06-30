package boat.torrent;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import boat.utilities.HttpHelper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

@Slf4j
public class LimeTorrents extends HttpUser implements TorrentSearchEngine {

    LimeTorrents(HttpHelper httpHelper) {
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
        final String encodedSearch = URLEncoder.encode(searchName, StandardCharsets.UTF_8);
        return String.format("%s/search/all/%s/seeds/1/", getBaseUrl(), encodedSearch);
    }

    @Override
    public String getBaseUrl() {
        return "https://www.limetorrents.info/";
    }

    private List<Torrent> parseTorrentsOnResultPage(String pageContent, String searchName) {
        ArrayList<Torrent> torrentList = new ArrayList<>();

        Document doc = Jsoup.parse(pageContent);
        Elements torrentListOnPage = doc.select(".table2 tr:has(td)");

        if (torrentListOnPage == null) {
            return torrentList;
        }

        torrentListOnPage.forEach(torrentElement -> {
            Torrent tempTorrent = new Torrent(toString());

            tempTorrent.name = torrentElement.getElementsByClass("tt-name").first().text();
            final String torrentHash = torrentElement.getElementsByClass("tt-name").first()
                .getElementsByAttributeValueContaining("href", "itorrents").first().attr("href")
                .replaceAll("http://itorrents.org/torrent/", "").replaceAll("\\.torrent.*", "");
            tempTorrent.magnetUri = TorrentHelper.buildMagnetUriFromHash(torrentHash, tempTorrent.name);
            try {
                tempTorrent.seeder = Integer
                    .parseInt(torrentElement.getElementsByClass("tdseed").first().text().replaceAll("[,.]", ""));
                tempTorrent.leecher = Integer
                    .parseInt(torrentElement.getElementsByClass("tdleech").first().text().replaceAll("[,.]", ""));
                tempTorrent.lsize = TorrentHelper
                    .extractTorrentSizeFromString(torrentElement.getElementsByClass("tdnormal").get(1).text());
                tempTorrent.size = TorrentHelper
                    .humanReadableByteCountBinary((long) (tempTorrent.lsize * 1024.0 * 1024.0));
            } catch (Exception exception) {
                log.error("parsing exception", exception);
            }

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
