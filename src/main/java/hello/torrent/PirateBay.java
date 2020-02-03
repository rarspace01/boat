package hello.torrent;

import hello.utilities.HttpHelper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PirateBay extends HttpUser implements TorrentSearchEngine {

    PirateBay(HttpHelper httpHelper) {
        super(httpHelper);
    }

    @Override
    public List<Torrent> searchTorrents(String searchName) {

        CopyOnWriteArrayList<Torrent> torrentList = new CopyOnWriteArrayList<>();

        String resultString = null;
        resultString = httpHelper.getPage(String.format(getBaseUrl() + "/search/%s/%d/99/200", URLEncoder.encode(searchName, StandardCharsets.UTF_8), 0), null, "lw=s");

        torrentList.addAll(parseTorrentsOnResultPage(resultString, searchName));

        // sort the findings
        torrentList.sort(TorrentHelper.torrentSorter);

        return torrentList;
    }

    @Override
    public String getBaseUrl() {
        return "https://thepiratebay.org";
    }

    @Override
    public Torrent suggestATorrent(List<Torrent> inputList) {
        return inputList.stream().max(TorrentHelper.torrentSorter).orElse(null);
    }

    private List<Torrent> parseTorrentsOnResultPage(String pageContent, String searchName) {

        ArrayList<Torrent> torrentList = new ArrayList<>();

        Document doc = Jsoup.parse(pageContent);

        Elements torrentListOnPage = doc.select("tr:not(.header)");

        if (torrentListOnPage != null) {
            for (Element torrent : torrentListOnPage) {

                Torrent tempTorrent = new Torrent();

                // extract ahref for title
                Elements nameElements = torrent.select("a[title~=Details for]");
                if (nameElements.size() > 0) {
                    tempTorrent.name = nameElements.get(0).text();
                }

                // extract uri for magnetlink
                Elements uriElements = torrent.select("a[title~=using magnet]");
                if (uriElements.size() > 0) {
                    tempTorrent.magnetUri = uriElements.get(0).attributes().get("href");
                }

                // extract date

                String inputDateString = torrent.select("td").get(2).text().replace("\u00a0", " ");
                SimpleDateFormat formatter = new SimpleDateFormat("MM-dd yyyy");
                if (inputDateString.matches("Today.*")) {
                    tempTorrent.date = new Date();
                } else if (inputDateString.matches("Y-day.*")) {
                    Calendar constructedDate = Calendar.getInstance();
                    constructedDate.add(Calendar.DAY_OF_MONTH, -1);
                    tempTorrent.date = constructedDate.getTime();
                } else {
                    try {
                        tempTorrent.date = formatter.parse(inputDateString);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }

                // extract size
                tempTorrent.size = TorrentHelper.cleanNumberString(torrent.select("td").get(4).text().replace("\u00a0", " "));

                tempTorrent.lsize = TorrentHelper.extractTorrentSizeFromString(tempTorrent);


                // extract seeder
                tempTorrent.seeder = Integer.parseInt(TorrentHelper.cleanNumberString(torrent.select("td").get(5).text()));

                // extract leecher
                tempTorrent.leecher = Integer.parseInt(TorrentHelper.cleanNumberString(torrent.select("td").get(6).text()));

                // evaluate result
                TorrentHelper.evaluateRating(tempTorrent, searchName);

                // filter torrents without any seeders
                if (TorrentHelper.isValidTorrent(tempTorrent)) {
                    torrentList.add(tempTorrent);
                }
            }
        }

        return torrentList;

    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }
}
