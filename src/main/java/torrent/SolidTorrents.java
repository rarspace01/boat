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
    public static void main(String[] args) {
        new SolidTorrents().searchTorrents("Iron Man 3");
    }

    @Override
    public List<Torrent> searchTorrents(String torrentname) {

        CopyOnWriteArrayList<Torrent> torrentList = new CopyOnWriteArrayList<>();

        String resultString = null;
        try {
            resultString = HttpHelper.getPage("https://solidtorrents.net/search?q=" + URLEncoder.encode(torrentname, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        torrentList.addAll(parseTorrentsOnResultPage(resultString, torrentname));
        torrentList.sort(TorrentHelper.torrentSorter);
        return torrentList;
    }

    private List<Torrent> parseTorrentsOnResultPage(String pageContent, String torrentname) {
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
                        tempTorrent.size = element.children().get(2).childNodes().get(0).toString().trim();
                        tempTorrent.lsize = TorrentHelper.extractTorrentSizeFromString(tempTorrent);
                        //tempTorrent.seeder = Integer.parseInt(element.children().get(2).childNodes().get(1).childNodes().get(1).toString());
                        //tempTorrent.leecher = Integer.parseInt(element.children().get(2).childNodes().get(3).childNodes().get(1).toString());
                    } else if (element.children().get(0).children().get(0).toString().contains("Magnet Link")) {
                        tempTorrent.magnetUri = element.childNodes().get(0).childNodes().get(1).childNodes().get(0).attributes().get("href");
                    }
                });
            }
            // evaluate result
            TorrentHelper.evaluateRating(tempTorrent, torrentname);
            if (tempTorrent.magnetUri != null && tempTorrent.seeder > 0) {
                torrentList.add(tempTorrent);
            }
        }
        return torrentList;
    }

    @Override
    public Torrent suggestATorrent(List<Torrent> inputList) {
        return null;
    }
}
