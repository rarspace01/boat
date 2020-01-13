package hello.torrent;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import hello.utilities.HttpHelper;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class LeetxTo extends HttpUser implements TorrentSearchEngine {

    LeetxTo(HttpHelper httpHelper) {
        super(httpHelper);
    }

    @Override
    public List<Torrent> searchTorrents(String searchName) {

        CopyOnWriteArrayList<Torrent> torrentList = new CopyOnWriteArrayList<>();

        String resultString = null;
        try {
            resultString = httpHelper.getPage(String.format(getBaseUrl() + "/sort-search/%s/seeders/desc/1/", URLEncoder.encode(searchName, "UTF-8")));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        torrentList.addAll(parseTorrentsOnResultPage(resultString, searchName));
        torrentList.sort(TorrentHelper.torrentSorter);
        return torrentList;
    }

    @Override
    public String getBaseUrl() {
        return "https://1337x.to";
    }

    private List<Torrent> parseTorrentsOnResultPage(String pageContent, String searchName) {
        ArrayList<Torrent> torrentList = new ArrayList<>();

        Document doc = Jsoup.parse(pageContent);

        Elements torrentListOnPage = doc.select(".table-list > tbody > tr");

        for (Element torrent : torrentListOnPage) {
            Torrent tempTorrent = new Torrent();
            if (torrent.childNodeSize() > 0) {
                torrent.children().forEach(element -> {

                    if (element.attr("class").contains("name")) {
                        //extract name
                        tempTorrent.name = element.getElementsByAttributeValueContaining("class", "name").get(0).getElementsByAttributeValueContaining("href", "torrent").get(0).html();
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
            if (TorrentHelper.isValidMetaTorrent(tempTorrent)) {
                torrentList.add(tempTorrent);
            }
        }
        //retrieve magneturis for torrents
        torrentList.parallelStream().forEach(torrent -> torrent.magnetUri = retrieveMagnetUri(torrent));

        // remove torrents without magneturi
        return torrentList.stream().filter(TorrentHelper::isValidTorrent).collect(Collectors.toList());
    }

    private String retrieveMagnetUri(Torrent torrent) {
        String pageContent = httpHelper.getPage(torrent.remoteUrl);
        Document doc = Jsoup.parse(pageContent);
        if (doc.select("* > li > a[href*=magnet]").size() > 0) {
            return doc.select("* > li > a[href*=magnet]").get(0).attr("href").trim();
        } else {
            return null;
        }
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
