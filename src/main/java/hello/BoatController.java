package hello;

import org.apache.commons.io.Charsets;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

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

        torrentSearchEngines.add(new PirateBay());
        torrentSearchEngines.add(new SolidTorrents());

        long currentTimeMillis = System.currentTimeMillis();

        torrentSearchEngines.parallelStream()
                .forEach(torrentSearchEngine -> combineResults.addAll(torrentSearchEngine.searchTorrents(searchString)));
        combineResults.sort(TorrentHelper.torrentSorter);

        System.out.println("Took: [" + (System.currentTimeMillis() - currentTimeMillis) + "]ms");

        return "G: " + combineResults.subList(0, Math.min(combineResults.size(), 10));
    }

    @GetMapping({"/boat/download"})
    @NotNull
    public final String downloadTorrentToPremiumize(@RequestParam("d") @NotNull String downloadUri) {
        byte[] magnetUri = Base64.getUrlDecoder().decode(downloadUri);
        String decodedUri = new String(magnetUri, Charsets.UTF_8);
        Torrent torrentToBeDownloaded = new Torrent();
        torrentToBeDownloaded.magnetUri = decodedUri;
        return (new Premiumize()).addTorrentToQueue(torrentToBeDownloaded);
    }

    @GetMapping({"/boat/debug"})
    @NotNull
    public final String getDebugInfo() {
        ArrayList<Torrent> remoteTorrents = new Premiumize().getRemoteTorrents();
        return "D: " + remoteTorrents;
    }

    @GetMapping({"/boat/shutdown"})
    @NotNull
    public final void shutdownServer() {
        System.exit(0);
    }
}
