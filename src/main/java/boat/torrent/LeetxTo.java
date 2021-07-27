package boat.torrent;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import boat.utilities.HttpHelper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import static org.springframework.util.CollectionUtils.isEmpty;

public class LeetxTo extends HttpUser implements TorrentSearchEngine {

    LeetxTo(HttpHelper httpHelper) {
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
        return String.format("%s/sort-search/%s/seeders/desc/1/", getBaseUrl(),
            URLEncoder.encode(searchName, StandardCharsets.UTF_8));
    }

    @Override
    public String getBaseUrl() {
        return "https://1337x.to";
    }

    private List<Torrent> parseTorrentsOnResultPage(String pageContent, String searchName) {
        ArrayList<Torrent> torrentList = new ArrayList<>();

        Document doc = Jsoup.parse(pageContent);

        Elements torrentListOnPage = doc.select(".table-list > tbody > tr");

        if (torrentListOnPage != null) {
            for (Element torrent : torrentListOnPage) {
                Torrent tempTorrent = new Torrent(toString());
                if (torrent.childNodeSize() > 0) {
                    torrent.children().forEach(element -> {

                        if (element.attr("class").contains("name")) {
                            //extract name
                            tempTorrent.name = element.getElementsByAttributeValueContaining("class", "name").get(0).getElementsByAttributeValueContaining("href", "torrent").get(0).html();
                            tempTorrent.isVerified = tempTorrent.name.contains("⭐");
                            //save remote url for later
                            tempTorrent.remoteUrl = getBaseUrl() + element.getElementsByAttributeValueContaining("class", "name").get(0).getElementsByAttributeValueContaining("href", "torrent").get(0).attr("href").trim();
                        }
                        if (element.attr("class").contains("size")) {
                            tempTorrent.size = TorrentHelper.cleanNumberString(element.getElementsByAttributeValueContaining("class", "size").get(0).textNodes().get(0).text().trim());
                            tempTorrent.lsize = TorrentHelper.extractTorrentSizeFromString(tempTorrent);
                        }

                        if (element.attr("class").contains("seeds")) {
                            tempTorrent.seeder = Integer.parseInt(TorrentHelper.cleanNumberString(element.getElementsByAttributeValueContaining("class", "seeds").get(0).textNodes().get(0).text().trim()));
                        }

                        if (element.attr("class").contains("leeches")) {
                            tempTorrent.leecher = Integer.parseInt(TorrentHelper.cleanNumberString(element.getElementsByAttributeValueContaining("class", "leeches").get(0).textNodes().get(0).text().trim()));
                        }

                    });
                }

                // evaluate result
                TorrentHelper.evaluateRating(tempTorrent, searchName);
                if (TorrentHelper.isValidTorrent(tempTorrent, false)) {
                    torrentList.add(tempTorrent);
                }
            }
        }
        //retrieve magneturis for torrents
        torrentList.parallelStream().forEach(torrent -> {
            torrent.magnetUri = retrieveMagnetUri(torrent);
            torrent.remoteUrl = "";
        });

        // remove torrents without magneturi
        return torrentList.stream().filter(TorrentHelper::isValidTorrent).collect(Collectors.toList());
    }

    private String retrieveMagnetUri(Torrent torrent) {
        String pageContent = httpHelper.getPage(torrent.remoteUrl);
        Document doc = Jsoup.parse(pageContent);
        if (!isEmpty(doc.select("* > li > a[href*=magnet]"))) {
            return doc.select("* > li > a[href*=magnet]").get(0).attr("href").trim();
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

}
