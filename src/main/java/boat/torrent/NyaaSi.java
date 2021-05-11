package boat.torrent;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import boat.utilities.HttpHelper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class NyaaSi extends HttpUser implements TorrentSearchEngine {

    NyaaSi(HttpHelper httpHelper) {
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
        return String.format(getBaseUrl() + "/?f=0&c=0_0&q=%s&s=seeders&o=desc", URLEncoder.encode(searchName, StandardCharsets.UTF_8));
    }

    @Override
    public String getBaseUrl() {
        return "https://nyaa.si";
    }

    private List<Torrent> parseTorrentsOnResultPage(String pageContent, String searchName) {
        ArrayList<Torrent> torrentList = new ArrayList<>();

        Document doc = Jsoup.parse(pageContent);

        Elements torrentListOnPage = doc.select(".torrent-list > tbody > tr");

        if (torrentListOnPage != null) {
            for (Element torrent : torrentListOnPage) {
                Torrent tempTorrent = new Torrent(toString());
                final Attributes attributesOnTableRecord = torrent.attributes();
                if (torrent.childNodeSize() > 0) {
                    torrent.children().forEach(element -> {

                        if (element.getElementsByTag("a").size() > 0
                                && getTorrentTitle(element).length() > 0) {
                            //extract name
                            tempTorrent.name = getTorrentTitle(element);
                        }
                        if (attributesOnTableRecord.size() > 0 && "success".equals(attributesOnTableRecord.get("class"))) {
                            tempTorrent.isVerified = true;
                        }
                        if (elementContainsMagnetUri(element)) {
                            //extract magneturi
                            tempTorrent.magnetUri = getMagnetUri(element);
                        }
                        if (element.text().contains("MiB") || element.text().contains("GiB")) {
                            tempTorrent.size = TorrentHelper.cleanNumberString(element.text().trim());
                            tempTorrent.lsize = TorrentHelper.extractTorrentSizeFromString(tempTorrent);
                        }
                    });
                }

                int index = torrent.children().size() - 3;
                if (index > 0) {
                    tempTorrent.seeder = Integer.parseInt(TorrentHelper.cleanNumberString(torrent.children().get(index).text()));
                    tempTorrent.leecher = Integer.parseInt(TorrentHelper.cleanNumberString(torrent.children().get(index + 1).text()));
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

    private String getMagnetUri(Element metaElement) {
        return metaElement.getElementsByTag("a").stream()
                .filter(element -> element.attributes().get("href").contains("magnet"))
                .map(element -> element.attributes().get("href").trim()).collect(Collectors.joining(""));
    }

    private boolean elementContainsMagnetUri(Element metaElement) {
        return metaElement.getElementsByTag("a").stream().anyMatch(element -> element.attributes().get("href").contains("magnet"));
    }

    private String getTorrentTitle(Element metaElement) {
        final Elements elementsByTag = metaElement.getElementsByTag("a");

        return elementsByTag.stream()
                .filter(element -> !element.attributes().get("href").contains("magnet"))
                .filter(element -> !element.attributes().get("href").contains("comment"))
                .filter(element -> element.attributes().get("href").contains("/view/"))
                .filter(element -> element.text().trim().length() > 0)
                .map(element -> element.text().trim()).collect(Collectors.joining());
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

}
