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

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

@RestController
public final class BoatController {
    @GetMapping({"/"})
    @NotNull
    public final String index() {
        return "Greetings from Spring Boot!";
    }

    @GetMapping({"/boat"})
    @NotNull
    public final String getTorrents(@RequestParam("q") @NotNull String searchString) {
        List<TorrentSearchEngine> torrentSearchEngines = new ArrayList<>();
        List combineResults = new ArrayList<Torrent>();
        List returnResults = new ArrayList<Torrent>();

        torrentSearchEngines.add(new PirateBay());
        torrentSearchEngines.add(new SolidTorrents());

        long currentTimeMillis = System.currentTimeMillis();

        torrentSearchEngines.parallelStream()
                .forEach(torrentSearchEngine -> combineResults.addAll(torrentSearchEngine.searchTorrents(searchString)));
        returnResults.addAll(cleanDuplicates(combineResults));
        returnResults.sort(TorrentHelper.torrentSorter);

        System.out.println("Took: [" + (System.currentTimeMillis() - currentTimeMillis) + "]ms");

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
        final String switchToProgress = "<a href=\"../boat/debug\">Show Progress</a> ";
        return switchToProgress + (new Premiumize()).addTorrentToQueue(torrentToBeDownloaded);
    }

    @GetMapping({"/boat/debug"})
    @NotNull
    public final String getDebugInfo() {
        InputStream stream = getClass().getResourceAsStream("/META-INF/MANIFEST.MF");
        String impBuildDate = "";

        if (stream == null) {
            System.out.println("Couldn't find manifest.");
            System.exit(0);
        }

        Manifest manifest = null;
        try {
            manifest = new Manifest(stream);
            Attributes attributes = manifest.getMainAttributes();

            String impTitle = attributes.getValue("Implementation-Title");
            String impVersion = attributes.getValue("Implementation-Version");
            impBuildDate = attributes.getValue("Built-Date");
            String impBuiltBy = attributes.getValue("Built-By");
        } catch (IOException e) {
            e.printStackTrace();
        }


        ArrayList<Torrent> remoteTorrents = new Premiumize().getRemoteTorrents();
        return "[" + impBuildDate + "] D: " + remoteTorrents;
    }

    @GetMapping({"/boat/shutdown"})
    @NotNull
    public final void shutdownServer() {
        System.exit(0);
    }
}
