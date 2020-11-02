package pirateboat.torrent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import pirateboat.utilities.HttpHelper;
import org.jsoup.Jsoup;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SolidTorrents extends HttpUser implements TorrentSearchEngine {

    SolidTorrents(HttpHelper httpHelper) {
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
        return getBaseUrl() + "/api/v1/search?sort=seeders&q=" + URLEncoder.encode(searchName, StandardCharsets.UTF_8) + "&category=all&fuv=yes";
    }

    @Override
    public String getBaseUrl() {
        return "https://solidtorrents.net";
    }

    @Override
    public String getSearchPage() {
        return buildSearchUrl("test");
    }

    private List<Torrent> parseTorrentsOnResultPage(String pageContent, String searchName) {
        ArrayList<Torrent> torrentList = new ArrayList<>();

        //create ObjectMapper instance
        ObjectMapper objectMapper = new ObjectMapper();

//read JSON like DOM Parser
        try {
            JsonNode rootNode = objectMapper.readTree(pageContent);
            JsonNode resultsNode = rootNode.get("results");

            if (resultsNode != null) {
                Iterator<JsonNode> elements = resultsNode.elements();
                while (elements.hasNext()) {
                    JsonNode jsonTorrent = elements.next();
                    Torrent tempTorrent = new Torrent(toString());
                    //extract Size & S/L
                    tempTorrent.name = jsonTorrent.get("title").asText();
                    String sizeString = jsonTorrent.get("size").asLong() / 1024 / 1024 + "MB";
                    tempTorrent.size = TorrentHelper.cleanNumberString(Jsoup.parse(sizeString).text().trim());
                    tempTorrent.lsize = TorrentHelper.extractTorrentSizeFromString(tempTorrent);
                    tempTorrent.seeder = jsonTorrent.get("swarm").get("seeders").asInt();
                    tempTorrent.leecher = jsonTorrent.get("swarm").get("leechers").asInt();
                    tempTorrent.magnetUri = jsonTorrent.get("magnet").asText();
                    // evaluate result
                    TorrentHelper.evaluateRating(tempTorrent, searchName);
                    if (TorrentHelper.isValidTorrent(tempTorrent)) {
                        torrentList.add(tempTorrent);
                    }
                }
            }

        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return torrentList;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }
}
