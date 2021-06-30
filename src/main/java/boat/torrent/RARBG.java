package boat.torrent;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import boat.utilities.HttpHelper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

@Slf4j
public class RARBG extends HttpUser implements TorrentSearchEngine {

    public RARBG(HttpHelper httpHelper) {
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

    @Override
    public String getBaseUrl() {
        return "http://rarbg.to";
    }

    private String buildSearchUrl(String searchName) {
        return String.format("%s/torrents.php?search=%s&order=seeders&by=DESC", getBaseUrl(),
            URLEncoder.encode(searchName, StandardCharsets.UTF_8));
    }

    private List<Torrent> parseTorrentsOnResultPage(String pageContent, String searchName) {

        Document doc = Jsoup.parse(pageContent);

        Elements torrentsOnPage = doc.select(".lista2");
        return torrentsOnPage.stream()
            .map(Node::toString)
            .map(this::extractSubUrl)
            .filter(Objects::nonNull)
            .map(url -> parseTorrentsOnSubPage(httpHelper.getPage(url), searchName))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private Torrent parseTorrentsOnSubPage(String page, String searchName) {
        Document doc = Jsoup.parse(page);
        final Torrent torrent = new Torrent(toString());
        torrent.name = doc.title().replaceAll(" Torrent download", "");
        torrent.magnetUri = doc.select("a[href*=magnet]")
            .stream()
            .map(element -> element.attributes().get("href"))
            .filter(Objects::nonNull)
            .findFirst().orElse(null);
        final String text = doc.text();

        torrent.size = TorrentHelper.cleanNumberString(getValueBetweenStrings(text, "Size: ", "Show Files").trim());
        torrent.lsize = TorrentHelper.extractTorrentSizeFromString(torrent);

        try {
            torrent.seeder = Integer.parseInt(getValueBetweenStrings(text, "Seeders : ", " ,").trim());
            torrent.leecher = Integer.parseInt(getValueBetweenStrings(text, "Leechers : ", " ,").trim());
        } catch (Exception exception) {
            log.error("parsing exception", exception);
        }
        TorrentHelper.evaluateRating(torrent, searchName);
        if (TorrentHelper.isValidTorrent(torrent)) {
            return torrent;
        } else {
            return null;
        }
    }

    private String extractSubUrl(String elementString) {
        Pattern subUrlPattern = Pattern.compile("href=\"(\\/torrent\\/[A-Za-z0-9]+)\"");
        Matcher matcher = subUrlPattern.matcher(elementString);
        while (matcher.find()) {
            return getBaseUrl() + matcher.group(1);
        }
        return null;
    }

    private String getValueBetweenStrings(String input, String firstString, String secondString) {
        Pattern betweenPattern = Pattern.compile(firstString + "(.*)" + secondString);
        Matcher matcher = betweenPattern.matcher(input);
        while (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }


}
