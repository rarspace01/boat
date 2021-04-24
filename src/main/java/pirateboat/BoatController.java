package pirateboat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import pirateboat.info.CloudService;
import pirateboat.info.TheFilmDataBaseService;
import pirateboat.info.TorrentMetaService;
import pirateboat.multifileHoster.MultifileHosterService;
import pirateboat.torrent.Torrent;
import pirateboat.torrent.TorrentHelper;
import pirateboat.torrent.TorrentSearchEngine;
import pirateboat.torrent.TorrentSearchEngineService;
import pirateboat.utilities.HttpHelper;
import pirateboat.utilities.PropertiesHelper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RestController
public final class BoatController {
    private final String switchToProgress = "<a href=\"../debug\">Show Progress</a> ";
    private final HttpHelper httpHelper;
    private final TorrentSearchEngineService torrentSearchEngineService;
    private final CloudService cloudService;
    private final TorrentMetaService torrentMetaService;
    private final TheFilmDataBaseService theFilmDataBaseService;
    private final MultifileHosterService multifileHosterService;

    @Autowired
    public BoatController(
            HttpHelper httpHelper,
            TorrentSearchEngineService torrentSearchEngineService,
            CloudService cloudService,
            TorrentMetaService torrentMetaService,
            TheFilmDataBaseService theFilmDataBaseService,
            MultifileHosterService multifileHosterService) {
        this.httpHelper = httpHelper;
        this.torrentSearchEngineService = torrentSearchEngineService;
        this.cloudService = cloudService;
        this.torrentMetaService = torrentMetaService;
        this.theFilmDataBaseService = theFilmDataBaseService;
        this.multifileHosterService = multifileHosterService;
    }

    @GetMapping({"/"})
    @NonNull
    public final String index() {
        return "Greetings from Spring Boot!";
    }

    @ResponseBody
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public String handleHttpMediaTypeNotAcceptableException() {
        return "acceptable MIME type:" + MediaType.TEXT_HTML;
    }

    @GetMapping({"/search"})
    @NonNull
    public final String search() {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<body style=\"font-size: 2em;\">\n" +
                "\n" +
                "<h2>Here to serve you</h2>\n" +
                "\n" +
                "<form action=\"../boat\" target=\"_blank\" method=\"GET\">\n" +
                "  Title:<br>\n" +
                "  <input type=\"text\" name=\"qq\" value=\"\" style=\"font-size: 2em; \">\n" +
                "  <br>\n" +
                "  <input type=\"reset\" value=\"Reset\" style=\"font-size: 2em; \">\n" +
                "  <input type=\"submit\" value=\"Search\" style=\"font-size: 2em; \">\n" +
                "</form>\n" +
                "  <br>\n" +
                "  <br>\n" +
                "<form action=\"../boat/download/\" target=\"_blank\" method=\"POST\">\n" +
                "  Direct download URL (multiple seperate by comma):<br>\n" +
                "  <input type=\"text\" name=\"dd\" value=\"\" style=\"font-size: 2em; \">\n" +
                "  <br>\n" +
                "  <input type=\"reset\" value=\"Reset\" style=\"font-size: 2em; \">\n" +
                "  <input type=\"submit\" value=\"Download\" style=\"font-size: 2em; \">\n" +
                "</form>\n" +
                "<br/>\n" +
                switchToProgress.replace("..", "../boat") +
                "</body>\n" +
                "</html>\n";
    }

    @GetMapping({"/boat"})
    @NonNull
    public final String searchTorrents(@RequestParam(value = "q", required = false) String searchString, @RequestParam(value = "qq", required = false) String localSearchString, @RequestParam(value = "qqq", required = false) String luckySearchUrl) {
        long startTime = System.currentTimeMillis();
        if(Strings.isNotEmpty(localSearchString)) {
            final List<String> existingFiles = cloudService.findExistingFiles(localSearchString);
            if(!existingFiles.isEmpty()) {
                return "We already found some files:<br/>" + String.join("<br/>",existingFiles)+"<br/>Still want to search? <a href=\"?q="+localSearchString+"\">Yes</a>";
            } else {
                searchString = localSearchString;
            }
        }
        if(Strings.isNotEmpty(localSearchString) || Strings.isNotEmpty(searchString)) {
            List<Torrent> torrentList = searchTorrents(searchString);
            log.info("Took: [{}]ms for [{}] found [{}]", (System.currentTimeMillis() - startTime), searchString, torrentList.size());
            return "G: " + torrentList.stream().limit(25).collect(Collectors.toList());
        } else if(Strings.isNotEmpty(luckySearchUrl)) {
            StringBuilder response = new StringBuilder();
            final String pageWithEntries = httpHelper.getPage(luckySearchUrl);
            if(Strings.isNotEmpty(pageWithEntries)) {
                final String[] titles = pageWithEntries.split("\n");
                Arrays.stream(titles).forEach(title ->
                        searchTorrents(title).stream().findFirst().ifPresentOrElse(torrent -> {
                            log.info("Download {} with {}", title,torrent.magnetUri);
                            downloadTorrentToMultifileHoster(null, torrent.magnetUri);
                            response.append(title).append("✅ <br/>");
                        },() -> {
                            log.warn("Couldn't Download {}", title);
                            response.append(title).append("❌ <br/>");
                        })
                );
                return response.toString();
            } else {
                return "Error: nothing in remote url";
            }
        } else {
            return "Error: nothing to search";
        }
    }

    public List<Torrent> cleanDuplicates(List<Torrent> combineResults) {
        ArrayList<Torrent> cleanedTorrents = new ArrayList<>();
        combineResults.forEach(result -> {
            if (!cleanedTorrents.contains(result)) {
                cleanedTorrents.add(result);
            } else {
                final int existingTorrentIndex = cleanedTorrents.indexOf(result);
                final Torrent existingTorrent = cleanedTorrents.get(existingTorrentIndex);
                if(existingTorrent.searchRating<result.searchRating) {
                    cleanedTorrents.remove(existingTorrent);
                    cleanedTorrents.add(result);
                }
            }
        });
        return cleanedTorrents;
    }

    @RequestMapping({"/boat/download"})
    @NonNull
    public final String downloadTorrentToMultifileHoster(@RequestParam(value = "d", required = false) String downloadUri, @RequestParam(value = "dd", required = false) String directDownloadUri) {
        List<Torrent> torrentsToBeDownloaded = new ArrayList<>();
        String decodedUri;
        if (Strings.isNotEmpty(downloadUri)) {
            byte[] magnetUri = Base64.getUrlDecoder().decode(downloadUri);
            decodedUri = new String(magnetUri, StandardCharsets.UTF_8);
            addUriToQueue(torrentsToBeDownloaded, decodedUri);
        } else if (Strings.isNotEmpty(directDownloadUri)) {
            decodedUri = directDownloadUri;
            if (!decodedUri.contains(",")) {
                addUriToQueue(torrentsToBeDownloaded, decodedUri);
            } else {
                String[] uris = decodedUri.split(",");
                Stream.of(uris).forEach(uri -> addUriToQueue(torrentsToBeDownloaded, uri));
            }
        }
        if(torrentsToBeDownloaded.size()==1) {
        return switchToProgress + multifileHosterService.addTorrentToQueue(torrentsToBeDownloaded.get(0));
        } else {
            torrentsToBeDownloaded.forEach(multifileHosterService::addTorrentToQueue);
            return switchToProgress;
        }
    }

    private void addUriToQueue(List<Torrent> torrentsToBeDownloaded, String decodedUri) {
        Torrent torrentToBeDownloaded = new Torrent("BoatController");
        torrentToBeDownloaded.magnetUri = decodedUri;
        torrentsToBeDownloaded.add(torrentToBeDownloaded);
    }

    @RequestMapping({"/boat/tfdb"})
    @NonNull
    public final String searchTfdb(@RequestParam(value = "q") String query) {
        return theFilmDataBaseService.search(query).toString();
    }

    @GetMapping({"/boat/debug"})
    @NonNull
    public final String getDebugInfo() {
        torrentMetaService.refreshTorrents();
        List<Torrent> remoteTorrents = torrentMetaService.getActiveTorrents();
        return "v:" + PropertiesHelper.getVersion()
                + "<br/>ActiveSearchEngines: " + torrentSearchEngineService.getActiveSearchEngines()
                + "<br/>InActiveSearchEngines: " + torrentSearchEngineService.getInActiveSearchEngines()
                + "<br/>D: " + remoteTorrents
                ;
    }

    @GetMapping({"/boat/shutdown"})
    @NonNull
    public final void shutdownServer() {
        System.exit(0);
    }

    @NotNull
    private List<Torrent> searchTorrents(String searchString) {
        List<Torrent> combineResults = new ArrayList<>();
        final List<TorrentSearchEngine> activeSearchEngines = new ArrayList<>(torrentSearchEngineService.getActiveSearchEngines());
        activeSearchEngines.parallelStream()
                .forEach(torrentSearchEngine -> combineResults.addAll(torrentSearchEngine.searchTorrents(searchString)));
        List<Torrent> returnResults = new ArrayList<>(cleanDuplicates(combineResults));
        // checkAllForCache
        List<Torrent> cacheStateOfTorrents = multifileHosterService.getCachedStateOfTorrents(returnResults);
        List<Torrent> torrentList = cacheStateOfTorrents.stream().map(torrent -> TorrentHelper.evaluateRating(torrent, searchString)).sorted(TorrentHelper.torrentSorter).collect(Collectors.toList());
        return torrentList;
    }
}
