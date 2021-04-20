package pirateboat.torrent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import pirateboat.utilities.HttpHelper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class PirateBay extends HttpUser implements TorrentSearchEngine {

    PirateBay(HttpHelper httpHelper) {
        super(httpHelper);
    }

    @Override
    public List<Torrent> searchTorrents(String searchName) {

        CopyOnWriteArrayList<Torrent> torrentList = new CopyOnWriteArrayList<>();

        String resultString = null;
        resultString = httpHelper.getPage(buildSearchUrl(searchName), null, "lw=s");

        torrentList.addAll(parseTorrentsOnResultPage(resultString, searchName));

        // sort the findings
        torrentList.sort(TorrentHelper.torrentSorter);

        return torrentList;
    }

    private String buildSearchUrl(String searchName) {
        return String.format(getBaseUrl() + "/q.php?q=%s&cat=", URLEncoder.encode(searchName, StandardCharsets.UTF_8));
    }

    @Override
    public String getBaseUrl() {
        return "https://apibay.org";
    }

    private List<Torrent> parseTorrentsOnResultPage(String pageContent, String searchName) {

        ArrayList<Torrent> torrentList = new ArrayList<>();

        try {
            JsonElement jsonRoot = JsonParser.parseString(pageContent);
            JsonArray listOfTorrents = jsonRoot.getAsJsonArray();

            listOfTorrents.forEach(jsonElement -> {
                Torrent tempTorrent = new Torrent(toString());
                final JsonObject jsonObject = jsonElement.getAsJsonObject();
                tempTorrent.name = jsonObject.get("name").getAsString();
                tempTorrent.magnetUri = TorrentHelper.buildMagnetUriFromHash(jsonObject.get("info_hash").getAsString(), tempTorrent.name);
                tempTorrent.seeder = jsonObject.get("seeders").getAsInt();
                tempTorrent.leecher = jsonObject.get("leechers").getAsInt();
                tempTorrent.lsize = jsonObject.get("size").getAsLong() / 1024.0f / 1024.0f;
                TorrentHelper.evaluateRating(tempTorrent, searchName);
                if (TorrentHelper.isValidTorrent(tempTorrent)) {
                    torrentList.add(tempTorrent);
                }
            });
        } catch (JsonSyntaxException e) {
            log.error("coudn't extract torrent ", e);
        }
        return torrentList;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }
}
