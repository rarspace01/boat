package torrent;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import utilities.HttpHelper;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SolidTorrents implements TorrentSearchEngine {

    @Override
    public List<Torrent> searchTorrents(String searchName) {

        CopyOnWriteArrayList<Torrent> torrentList = new CopyOnWriteArrayList<>();

        String resultString = null;
        try {
            resultString = HttpHelper.getPage(getBaseUrl() + "/search?q=" + URLEncoder.encode(searchName, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        torrentList.addAll(parseTorrentsOnResultPage(resultString, searchName));
        torrentList.sort(TorrentHelper.torrentSorter);
        return torrentList;
    }

    @Override
    public String getBaseUrl() {
        return "https://solidtorrents.net";
    }

    private List<Torrent> parseTorrentsOnResultPage(String pageContent, String searchName) {
        ArrayList<Torrent> torrentList = new ArrayList<>();

        Document doc = Jsoup.parse(pageContent);

        Elements torrentListOnPage = doc.select("div .v-card__text > div[role=list] > div > div[role=listitem]");

        for (Element torrent : torrentListOnPage) {
            Torrent tempTorrent = new Torrent();
            if (torrent.childNodeSize() > 0) {
                torrent.children().forEach(element -> {
                    if (element.childNodeSize() == 5 && element.children().get(0).childNodes().get(0).childNodes().get(0).attributes().hasKey("title")) {
                        //extract Size & S/L
                        tempTorrent.name = element.children().get(0).childNodes().get(0).childNodes().get(0).attributes().get("title");
                        String sizeString = element.children().get(2).childNodes().get(2).toString();
                        tempTorrent.size = TorrentHelper.cleanNumberString(Jsoup.parse(sizeString).text().trim());
                        tempTorrent.lsize = TorrentHelper.extractTorrentSizeFromString(tempTorrent);
                        tempTorrent.seeder = Integer.parseInt(TorrentHelper.cleanNumberString(element.children().get(2).childNodes().get(4).childNodes().get(1).toString().trim()));
                        tempTorrent.leecher = Integer.parseInt(TorrentHelper.cleanNumberString(element.children().get(2).childNodes().get(6).childNodes().get(1).toString().trim()));
                    } else if (element.children().get(0).children().get(0).toString().contains("Magnet Link")) {
                        tempTorrent.magnetUri = element.childNodes().get(0).childNodes().get(1).childNodes().get(0).attributes().get("href");
                    }
                });
            }
            // evaluate result
            TorrentHelper.evaluateRating(tempTorrent, searchName);
            if (TorrentHelper.isValidTorrent(tempTorrent)) {
                torrentList.add(tempTorrent);
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
        return this.getClass().getName();
    }
}
