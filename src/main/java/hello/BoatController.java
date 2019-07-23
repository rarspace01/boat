package hello;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import torrent.PirateBay;
import torrent.Premiumize;
import torrent.SolidTorrents;
import torrent.Torrent;
import torrent.TorrentHelper;
import torrent.TorrentSearchEngine;
import utilities.PropertiesHelper;

import javax.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public final class BoatController {
    final String switchToProgress = "<a href=\"../debug\">Show Progress</a> ";

    @GetMapping({"/"})
    @NotNull
    public final String index() {
        return "Greetings from Spring Boot!";
    }

    @GetMapping({"/search"})
    @NotNull
    public final String search() {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<body>\n" +
                "\n" +
                "<h2>Here to serve you</h2>\n" +
                "\n" +
                "<form action=\"../boat\" target=\"_blank\" method=\"GET\">\n" +
                "  Title:<br>\n" +
                "  <input type=\"text\" name=\"q\" value=\"\">\n" +
                "  <br>\n" +
                "  <input type=\"submit\" value=\"Submit\">\n" +
                "</form>\n" +
                "<br/>\n" +
                switchToProgress.replace("..", "../boat") +
                "</body>\n" +
                "</html>\n";
    }

    @GetMapping({"/boat"})
    @NotNull
    public final String getTorrents(@RequestParam("q") @NotNull String searchString) {
        List<TorrentSearchEngine> torrentSearchEngines = new ArrayList<>();
        List<Torrent> combineResults = new ArrayList<>();

        torrentSearchEngines.add(new PirateBay());
        torrentSearchEngines.add(new SolidTorrents());

        long currentTimeMillis = System.currentTimeMillis();

        torrentSearchEngines.parallelStream()
                .forEach(torrentSearchEngine -> combineResults.addAll(torrentSearchEngine.searchTorrents(searchString)));
        List<Torrent> returnResults = new ArrayList<>(cleanDuplicates(combineResults));
        returnResults.sort(TorrentHelper.torrentSorter);

        System.out.println(String.format("Took: [%s]ms for [%s]", (System.currentTimeMillis() - currentTimeMillis), searchString));

        return "G: " + returnResults.stream().limit(10).collect(Collectors.toList());
    }

    private List<Torrent> cleanDuplicates(List<Torrent> combineResults) {
        ArrayList<Torrent> cleanedTorrents = new ArrayList<>();
        combineResults.forEach(result -> {
            if (!cleanedTorrents.contains(result)) {
                cleanedTorrents.add(result);
            }
        });
        return cleanedTorrents;
    }

    @GetMapping({"/boat/download"})
    @NotNull
    public final String downloadTorrentToPremiumize(@RequestParam("d") @NotNull String downloadUri) {
        byte[] magnetUri = Base64.getUrlDecoder().decode(downloadUri);
        String decodedUri = new String(magnetUri, StandardCharsets.UTF_8);
        Torrent torrentToBeDownloaded = new Torrent();
        torrentToBeDownloaded.magnetUri = decodedUri;
        return switchToProgress + (new Premiumize()).addTorrentToQueue(torrentToBeDownloaded);
    }

    @GetMapping({"/boat/debug"})
    @NotNull
    public final String getDebugInfo() {

        ArrayList<Torrent> remoteTorrents = new Premiumize().getRemoteTorrents();
        return "v:" + PropertiesHelper.getVersion() + " D: " + remoteTorrents;
    }

    @GetMapping({"/boat/shutdown"})
    @NotNull
    public final void shutdownServer() {
        System.exit(0);
    }
}
