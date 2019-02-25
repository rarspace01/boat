package hello;

import org.apache.commons.io.Charsets;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import torrent.PirateBay;
import torrent.Premiumize;
import torrent.Torrent;

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
        //Intrinsics.checkParameterIsNotNull(searchString, "searchString");
        PirateBay pirateBay = new PirateBay();
        List resultList = pirateBay.searchTorrents(searchString);
        return "G: " + resultList.subList(0, Math.min(resultList.size(), 10));
    }

    @GetMapping({"/boat/download"})
    @NotNull
    public final String downloadTorrentToPremiumize(@RequestParam("d") @NotNull String downloadUri) {
        //Intrinsics.checkParameterIsNotNull(downloadUri, "downloadUri");
        byte[] var10000 = Base64.getUrlDecoder().decode(downloadUri);
        //Intrinsics.checkExpressionValueIsNotNull(var10000, "Base64.getUrlDecoder().decode(downloadUri)");
        byte[] var3 = var10000;
        String decodedUri = new String(var3, Charsets.UTF_8);
        Torrent torrentToBeDownloaded = new Torrent();
        torrentToBeDownloaded.magnetUri = decodedUri.toString();
        String var4 = (new Premiumize()).addTorrentToQueue(torrentToBeDownloaded);
        //Intrinsics.checkExpressionValueIsNotNull(var4, "Premiumize().addTorrentT…ue(torrentToBeDownloaded)");
        return var4;
    }

    @GetMapping({"/boat/debug"})
    @NotNull
    public final String getDebugInfo() {
        ArrayList<Torrent> remoteTorrents = new Premiumize().getRemoteTorrents();
        return "D: " + remoteTorrents;
    }
}
