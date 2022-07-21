package boat;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import boat.info.CloudFileService;
import boat.info.CloudService;
import boat.info.MediaItem;
import boat.info.QueueService;
import boat.info.TheFilmDataBaseService;
import boat.info.TorrentMetaService;
import boat.multifileHoster.MultifileHosterService;
import boat.services.TransferService;
import boat.torrent.Torrent;
import boat.torrent.TorrentHelper;
import boat.torrent.TorrentSearchEngineService;
import boat.utilities.HttpHelper;
import boat.utilities.PropertiesHelper;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.logging.log4j.util.Strings;

@Slf4j
@RestController
public final class BoatController {

    public static final String BREAK_LINK_HTML = "  <br>\n";
    private final String switchToProgress = "<a href=\"../debug\">Show Progress</a> ";
    private final String switchToSearchList = "<a href=\"../searchList\">Search a List</a> ";
    private final String switchToSearch = "<a href=\"../search\">Search a single Title</a> ";
    private final HttpHelper httpHelper;
    private final TorrentSearchEngineService torrentSearchEngineService;
    private final CloudService cloudService;
    private final TheFilmDataBaseService theFilmDataBaseService;
    private final MultifileHosterService multifileHosterService;
    private final QueueService queueService;
    private final CloudFileService cloudFileService;
    private final TransferService transferService;

    @Autowired
    public BoatController(
        HttpHelper httpHelper,
        TorrentSearchEngineService torrentSearchEngineService,
        CloudService cloudService,
        TorrentMetaService torrentMetaService,
        TheFilmDataBaseService theFilmDataBaseService,
        MultifileHosterService multifileHosterService,
        QueueService queueService,
        CloudFileService cloudFileService,
        TransferService transferService) {
        this.httpHelper = httpHelper;
        this.torrentSearchEngineService = torrentSearchEngineService;
        this.cloudService = cloudService;
        this.theFilmDataBaseService = theFilmDataBaseService;
        this.multifileHosterService = multifileHosterService;
        this.queueService = queueService;
        this.cloudFileService = cloudFileService;
        this.transferService = transferService;
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
            BREAK_LINK_HTML +
            "  <input type=\"reset\" value=\"Reset\" style=\"font-size: 2em; \">\n" +
            "  <input type=\"submit\" value=\"Search\" style=\"font-size: 2em; \">\n" +
            "</form>\n" +
            BREAK_LINK_HTML +
            BREAK_LINK_HTML +
            "<form action=\"../boat/download\" target=\"_blank\" method=\"POST\">\n" +
            "  Direct download URL (multiple seperate by comma):<br>\n" +
            "  <input type=\"text\" name=\"dd\" value=\"\" style=\"font-size: 2em; \">\n" +
            BREAK_LINK_HTML +
            "  <input type=\"reset\" value=\"Reset\" style=\"font-size: 2em; \">\n" +
            "  <input type=\"submit\" value=\"Download\" style=\"font-size: 2em; \">\n" +
            "</form>\n" +
            "<br/>\n" +
            switchToSearchList +
            switchToProgress.replace("..", "../boat") +
            "</body>\n" +
            "</html>\n";
    }

    @GetMapping({"/searchList"})
    @NonNull
    public final String searchList() {
        return "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<body style=\"font-size: 2em;\">\n" +
            "\n" +
            "<h2>Here to serve you</h2>\n" +
            "<form action=\"../boat\" target=\"_blank\" method=\"POST\">\n" +
            "Download multiple movies (one per line):<br>\n" +
            "<textarea id=\"qqq\" name=\"qqq\" rows=\"25\" cols=\"25\" style=\"font-size: 2em; \">\n"
            + "</textarea>\n" +
            BREAK_LINK_HTML +
            "  <input type=\"reset\" value=\"Reset\" style=\"font-size: 2em; \">\n" +
            "  <input type=\"submit\" value=\"Download\" style=\"font-size: 2em; \">\n" +
            "</form>\n" +
            "<br/>\n" +
            switchToSearch +
            switchToProgress.replace("..", "../boat") +
            "</body>\n" +
            "</html>\n";
    }

    @RequestMapping({"/boat"})
    @NonNull
    public final String searchTorrents(@RequestParam(value = "q", required = false) String searchString,
                                       @RequestParam(value = "qq", required = false) String localSearchString,
                                       @RequestParam(value = "qqq", required = false) String luckySearchList) {
        long startTime = System.currentTimeMillis();
        if (Strings.isNotEmpty(localSearchString)) {
            final List<String> existingFiles = cloudService.findExistingFiles(localSearchString);
            if (!existingFiles.isEmpty()) {
                return "We already found some files:<br/>" + String.join("<br/>", existingFiles)
                    + "<br/>Still want to search? <a href=\"?q=" + localSearchString + "\">Yes</a>";
            } else {
                searchString = localSearchString;
            }
        }
        if (Strings.isNotEmpty(localSearchString) || Strings.isNotEmpty(searchString)) {
            List<Torrent> torrentList = torrentSearchEngineService.searchTorrents(searchString);
            log.info("Took: [{}]ms for [{}] found [{}]", (System.currentTimeMillis() - startTime), searchString,
                torrentList.size());
            return "G: " + torrentList.stream().limit(25).collect(Collectors.toList());
        } else if (Strings.isNotEmpty(luckySearchList)) {
            String[] schemes = {"http", "https"};
            UrlValidator urlValidator = new UrlValidator(schemes);
            final String pageWithEntries;
            if (urlValidator.isValid(luckySearchList)) {
                pageWithEntries = httpHelper.getPage(luckySearchList);
            } else {
                pageWithEntries = luckySearchList;
            }
            if (Strings.isNotEmpty(pageWithEntries)) {
                final String[] titles = pageWithEntries.split("\n");
                final List<MediaItem> listOfMediaItems = Arrays.stream(titles).map(title ->
                        new MediaItem(title, title, null, boat.info.MediaType.Other)
                    )
                    .filter(this::isNotAlreadyDownloaded)
                    .collect(Collectors.toList());
                queueService.addAll(listOfMediaItems);
                queueService.saveQueue();
                return switchToProgress.replace("..", "../boat") + listOfMediaItems;
            } else {
                return "Error: nothing in remote url";
            }
        } else {
            return "Error: nothing to search";
        }
    }


    @RequestMapping({"/boat/download"})
    @NonNull
    public final String downloadTorrentToMultifileHoster(
        @RequestParam(value = "d", required = false) String downloadUri,
        @RequestParam(value = "dd", required = false) String directDownloadUri) {
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
        torrentsToBeDownloaded.forEach(multifileHosterService::addTorrentToTransfer);
        multifileHosterService.addTransfersToDownloadQueue();
        multifileHosterService.updateTransferStatus();
        return switchToProgress;
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
        multifileHosterService.refreshTorrents();
        List<Torrent> remoteTorrents = multifileHosterService.getActiveTorrents();
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        long startTime = runtimeBean.getStartTime();
        Date startDate = new Date(startTime);
        return "v:" + PropertiesHelper.getVersion() + " started: " + startDate
            + "<br/>remote host: " + httpHelper.getExternalHostname()
            + "<br/>cloud token: " + (cloudService.isCloudTokenValid() ? "✅" : "❌")
            + "<br/>search Cache: " + (cloudFileService.isCacheFilled() ? "✅" : "❌")
            + "<br/>ActiveSearchEngines: " + torrentSearchEngineService.getActiveSearchEngines()
            + "<br/>InActiveSearchEngines: " + torrentSearchEngineService.getInActiveSearchEngines()
            + "<br/>Active MultifileHoster: " + multifileHosterService.getActiveMultifileHosters()
            + "<br/>Active DL MultifileHoster: " + multifileHosterService.getActiveMultifileHosterForDownloads()
            + "<br/>TrafficLeft: " + TorrentHelper
            .humanReadableByteCountBinary((long) multifileHosterService.getRemainingTrafficInMB() * 1024 * 1024)
            + String.format("<br/>Transfers [%d]: %s", transferService.getAll().size(), transferService.getAll())
            + String.format("<br/><!-- D [%d]: %s -->", remoteTorrents.size(), remoteTorrents)
            + String.format("<br/>Queue [%d]: %s", queueService.getQueue().size(), queueService.getQueue())
            ;
    }

    @GetMapping({"/boat/shutdown"})
    @NonNull
    public final String shutdownServer() {
        log.info("shutdown request received");
        System.exit(0);
        return "off we go";
    }

    private boolean isNotAlreadyDownloaded(MediaItem mediaItem) {
        final List<String> existingFiles = cloudService
            .findExistingFiles(TorrentHelper.getSearchNameFrom(mediaItem));
        return existingFiles.isEmpty();
    }

}
