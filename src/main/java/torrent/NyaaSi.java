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

public class NyaaSi implements TorrentSearchEngine {
    public static void main(String[] args) {
        new NyaaSi().searchTorrents("search");
    }

    @Override
    public List<Torrent> searchTorrents(String torrentname) {

        CopyOnWriteArrayList<Torrent> torrentList = new CopyOnWriteArrayList<>();

        String resultString = null;
        try {
            resultString = HttpHelper.getPage(String.format("https://nyaa.si/?f=0&c=0_0&q=%s&s=seeders&o=desc", URLEncoder.encode(torrentname, "UTF-8")));
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

        Elements torrentListOnPage = doc.select(".torrent-list > tbody > tr");

        for (Element torrent : torrentListOnPage) {
            Torrent tempTorrent = new Torrent();
            if (torrent.childNodeSize() > 0) {
                torrent.children().forEach(element -> {

                    if (element.getElementsByTag("a").size() == 2
                            && element.getElementsByTag("a").get(1).attributes().get("title").length() > 0) {
                        //extract Size & S/L
                        tempTorrent.name = element.getElementsByTag("a").get(1).attributes().get("title");
                    }
                    if (element.getElementsByTag("a").size() == 2
                            && element.getElementsByTag("a").get(1).attributes().get("href").contains("magnet")) {
                        //extract Size & S/L
                        tempTorrent.magnetUri = element.getElementsByTag("a").get(1).attributes().get("href");
                    }
                    if (element.text().contains("MiB") || element.text().contains("GiB")) {
                        tempTorrent.size = element.text().trim();
                        tempTorrent.lsize = TorrentHelper.extractTorrentSizeFromString(tempTorrent);
                    }
//                        tempTorrent.seeder = Integer.parseInt(element.children().get(2).childNodes().get(4).childNodes().get(1).toString().trim());
//                        tempTorrent.leecher = Integer.parseInt(element.children().get(2).childNodes().get(6).childNodes().get(1).toString().trim());

                });
            }

            int index = torrent.children().size() - 3;
            if(index>0) {
                tempTorrent.seeder = Integer.parseInt(torrent.children().get(index).text());
                tempTorrent.leecher = Integer.parseInt(torrent.children().get(index+1).text());
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
        return inputList.stream().min(TorrentHelper.torrentSorter).orElse(null);
    }
}
