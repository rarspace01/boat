package hello;

import org.apache.commons.io.Charsets;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import torrent.*;

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
        TorrentSearchEngine solidTorrents = new SolidTorrents();
        TorrentSearchEngine piratebay = new PirateBay();

        List resultListSolid = solidTorrents.searchTorrents(searchString);
        List resultListPiratebay = piratebay.searchTorrents(searchString);

        List combineResults = new ArrayList<Torrent>();
        combineResults.addAll(resultListSolid);
        combineResults.addAll(resultListPiratebay);
        combineResults.sort(TorrentHelper.torrentSorter);
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
}
