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
public class Torrentz extends HttpUser implements TorrentSearchEngine {

    Torrentz(HttpHelper httpHelper) {
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
        return String.format("%s/data.php?q=%s", getBaseUrl(), encodedSearch);
    }

    @Override
    public String getBaseUrl() {
        return "https://torrentzeu.org/";
    }

    private List<Torrent> parseTorrentsOnResultPage(String pageContent, String searchName) {
        ArrayList<Torrent> torrentList = new ArrayList<>();

        Document doc = Jsoup.parse(pageContent);
        Elements torrentListOnPage = doc.select("tr:has(td)");

        if (torrentListOnPage == null) {
            return torrentList;
        }

        torrentListOnPage.forEach(torrentElement -> {
            Torrent tempTorrent = new Torrent(toString());

            tempTorrent.name = torrentElement.getElementsByAttributeValue("data-title", "Name").first().text();
            tempTorrent.magnetUri = torrentElement.getElementsByTag("a").first().attr("href");
            try {
                tempTorrent.seeder = Integer
                    .parseInt(torrentElement.getElementsByAttributeValue("data-title", "Last Updated").stream()
                        .filter(element -> !element.hasAttr("class")).findFirst().get().text());
                tempTorrent.leecher = Integer
                    .parseInt(torrentElement.getElementsByAttributeValue("data-title", "Leeches").first().text());
                tempTorrent.lsize = TorrentHelper.extractTorrentSizeFromString(
                    torrentElement.getElementsByAttributeValue("data-title", "Size").first().text()
                        .replaceAll(".*Size\\s|,.*", ""));
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
