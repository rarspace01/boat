package torrent;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import utilities.HttpHelper;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

public class PirateBay implements TorrentSearchEngine {

    private static final int MAX_PAGES = 2;

    @Override
    public List<Torrent> searchTorrents(String torrentname) {

        CopyOnWriteArrayList<Torrent> torrentList = new CopyOnWriteArrayList<>();

//        Stream<BigInteger> pageStream = Stream.iterate(BigInteger.ZERO, n -> n.add(BigInteger.ONE)).limit(MAX_PAGES);
//
//        pageStream.parallel().forEach(bigInteger -> {
//            int pageIndex = bigInteger.getLowestSetBit();
//            String localString = HttpHelper.getPage("https://thepiratebay.org/search/" + torrentname + "/" + pageIndex + "/99/200", null, "lw=s");
//            torrentList.addAll(parseTorrentsOnResultPage(localString, torrentname));
//        });
//
//        CopyOnWriteArrayList<Torrent> torrentList = new CopyOnWriteArrayList<>();

        String resultString = null;
        try {
            resultString = HttpHelper.getPage(String.format("https://thepiratebay.org/search/%s/%d/99/200", URLEncoder.encode(torrentname, "UTF-8"), 0),null,"lw=s");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        torrentList.addAll(parseTorrentsOnResultPage(resultString, torrentname));

        // sort the findings
        torrentList.sort(TorrentHelper.torrentSorter);

        return torrentList;
    }

    @Override
    public Torrent suggestATorrent(List<Torrent> inputList) {
        Torrent returnTorrent = null;

        // sort the findings
        inputList.sort(TorrentHelper.torrentSorter);

        if (inputList != null && inputList.size() > 0) {

            double localMax = 0;
            double maxSize = 0;
            int maxSizeIndex = -1;
            int index = 0;

            // get the potentials
            for (Torrent torrent : inputList) {
                if ((torrent.searchRating > localMax) && (torrent.lsize > maxSize)) {
                    localMax = torrent.searchRating;
                    maxSize = torrent.lsize;
                    maxSizeIndex = index;
                } else {
                    break;
                }
                index++;
            }

            if (maxSizeIndex > -1) {
                returnTorrent = inputList.get(maxSizeIndex);
            }
        }
        return returnTorrent;
    }

    private List<Torrent> parseTorrentsOnResultPage(String pageContent, String torrentname) {

        ArrayList<Torrent> torrentList = new ArrayList<>();

        Document doc = Jsoup.parse(pageContent);

        Elements torrentListOnPage = doc.select("tr:not(.header)");

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
            tempTorrent.size = torrent.select("td").get(4).text().replace("\u00a0", " ");

            tempTorrent.lsize = TorrentHelper.extractTorrentSizeFromString(tempTorrent);


            // extract seeder
            tempTorrent.seeder = Integer.parseInt(torrent.select("td").get(5).text());

            // extract leecher
            tempTorrent.leecher = Integer.parseInt(torrent.select("td").get(6).text());

            // evaluate result
            TorrentHelper.evaluateRating(tempTorrent, torrentname);

            // filter torrents without any seeders
            if (tempTorrent.seeder > 0) {
                torrentList.add(tempTorrent);
            }
        }

        return torrentList;

    }

    private void printResults(List<Torrent> torrents) {
        for (Torrent t : torrents) {
            System.out.println(t);
        }
    }

}
