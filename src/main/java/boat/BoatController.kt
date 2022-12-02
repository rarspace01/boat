package boat

import boat.info.CloudFileService
import boat.info.CloudService
import boat.info.MediaItem
import boat.info.QueueService
import boat.info.TheFilmDataBaseService
import boat.info.TorrentMetaService
import boat.multifileHoster.MultifileHosterService
import boat.services.TransferService
import boat.torrent.Torrent
import boat.torrent.TorrentHelper.getSearchNameFrom
import boat.torrent.TorrentHelper.humanReadableByteCountBinary
import boat.torrent.TorrentSearchEngineService
import boat.utilities.HttpHelper
import boat.utilities.LoggerDelegate
import boat.utilities.PropertiesHelper
import org.apache.commons.validator.routines.UrlValidator
import org.apache.logging.log4j.util.Strings
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.Base64
import java.util.Date
import java.util.stream.Collectors
import kotlin.concurrent.thread
import kotlin.system.exitProcess

@RestController
class BoatController @Autowired constructor(
    private val httpHelper: HttpHelper,
    private val torrentSearchEngineService: TorrentSearchEngineService,
    private val cloudService: CloudService,
    torrentMetaService: TorrentMetaService?,
    private val theFilmDataBaseService: TheFilmDataBaseService,
    private val multifileHosterService: MultifileHosterService,
    private val queueService: QueueService,
    private val cloudFileService: CloudFileService,
    private val transferService: TransferService
) {

    companion object {
        private val logger by LoggerDelegate()
        const val BREAK_LINK_HTML = "  <br>\n"
    }

    private val switchToProgress = "<a href=\"./debug\">Show Progress</a> "
    private val switchToSearchList = "<a href=\"./searchList\">Search a List</a> "
    private val switchToSearch = "<a href=\"./search\">Search a single Title</a> "

    @GetMapping("/")
    fun index(): String {
        return "Greetings from Spring Boot!"
    }

    @ResponseBody
    @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
    fun handleHttpMediaTypeNotAcceptableException(): String {
        return "acceptable MIME type:" + MediaType.TEXT_HTML
    }

    @GetMapping("/search")
    fun searchRedirect(): String {
        return """<html>
<head>
  <meta http-equiv="Refresh" content="0; url=./boat/search" />
</head>
<body>
  <p>Please follow <a href="./boat/search">this link</a>.</p>
</body>
</html>
"""
    }

    @GetMapping("/boat/search")
    fun search(): String {
        return """<!DOCTYPE html>
<html>
<body style="font-size: 2em;">

<h2>Here to serve you</h2>

<form action="../boat" target="_blank" method="GET">
  Title:<br>
  <input type="text" name="qq" value="" style="font-size: 2em; ">
$BREAK_LINK_HTML  <input type="reset" value="Reset" style="font-size: 2em; ">
  <input type="submit" value="Search" style="font-size: 2em; ">
</form>
$BREAK_LINK_HTML$BREAK_LINK_HTML<form action="./download" target="_blank" method="POST">
  Direct download URL (multiple seperate by comma):<br>
  <input type="text" name="dd" value="" style="font-size: 2em; ">
$BREAK_LINK_HTML  <input type="reset" value="Reset" style="font-size: 2em; ">
  <input type="submit" value="Download" style="font-size: 2em; ">
</form>
<br/>
$switchToSearchList${switchToProgress.replace("..", "../boat")}</body>
</html>
"""
    }

    @GetMapping("/boat/searchList")
    fun searchList(): String {
        return """<!DOCTYPE html>
<html>
<body style="font-size: 2em;">

<h2>Here to serve you</h2>
<form action="../boat" target="_blank" method="POST">
Download multiple movies (one per line):<br>
<textarea id="qqq" name="qqq" rows="25" cols="25" style="font-size: 2em; ">
</textarea>
$BREAK_LINK_HTML  <input type="reset" value="Reset" style="font-size: 2em; ">
  <input type="submit" value="Download" style="font-size: 2em; ">
</form>
<br/>
$switchToSearch${switchToProgress}")}</body>
</html>
"""
    }

    @RequestMapping("/boat")
    fun searchTorrents(
        @RequestParam(value = "q", required = false) searchString: String?,
        @RequestParam(value = "qq", required = false) localSearchString: String?,
        @RequestParam(value = "qqq", required = false) luckySearchList: String?,
    ): String {
        var searchString = searchString
        val startTime = System.currentTimeMillis()
        if (localSearchString != null && Strings.isNotEmpty(localSearchString)) {
            val existingFiles = cloudService.findExistingFiles(localSearchString)
            searchString = if (existingFiles.isNotEmpty()) {
                return ("We already found some files:<br/>" + java.lang.String.join("<br/>", existingFiles)
                        + "<br/>Still want to search? <a href=\"?q=" + localSearchString + "\">Yes</a>")
            } else {
                localSearchString
            }
        }
        return if (Strings.isNotEmpty(localSearchString) || Strings.isNotEmpty(searchString)) {
            val torrentList = torrentSearchEngineService.searchTorrents(searchString!!)
            logger.info(
                "Took: [{}]ms for [{}] found [{}]", System.currentTimeMillis() - startTime, searchString,
                torrentList.size
            )
            "G: " + torrentList.stream().limit(50).collect(Collectors.toList())
        } else if (luckySearchList != null && Strings.isNotEmpty(luckySearchList)) {
            val schemes = arrayOf("http", "https")
            val urlValidator = UrlValidator(schemes)
            val pageWithEntries: String = if (urlValidator.isValid(luckySearchList)) {
                httpHelper.getPage(luckySearchList)
            } else {
                luckySearchList
            }
            if (Strings.isNotEmpty(pageWithEntries)) {
                val titles = pageWithEntries.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val listOfMediaItems = Arrays.stream(titles).map { title: String? -> MediaItem(title!!, title, null, boat.info.MediaType.Other) }
                    .filter { mediaItem: MediaItem -> isNotAlreadyDownloaded(mediaItem) }
                    .collect(Collectors.toList())
                queueService.addAll(listOfMediaItems)
                queueService.saveQueue()
                switchToProgress.replace("..", "../boat") + listOfMediaItems
            } else {
                "Error: nothing in remote url"
            }
        } else {
            "Error: nothing to search"
        }
    }

    @RequestMapping("/boat/download")
    fun downloadTorrentToMultifileHoster(
        @RequestParam(value = "d", required = false) downloadUri: String?,
        @RequestParam(value = "dd", required = false) directDownloadUri: String?,
    ): String {
        val torrentsToBeDownloaded: MutableList<Torrent> = ArrayList()
        val decodedUri: String
        if (Strings.isNotEmpty(downloadUri)) {
            val magnetUri = Base64.getUrlDecoder().decode(downloadUri)
            decodedUri = String(magnetUri, StandardCharsets.UTF_8)
            torrentsToBeDownloaded.add(
                Torrent.of(
                    magnetUri = decodedUri,
                )
            )
        } else if (Strings.isNotEmpty(directDownloadUri)) {
            decodedUri = directDownloadUri ?: ""
            if (!decodedUri.contains(",")) {
                torrentsToBeDownloaded.add(
                    Torrent.of(
                        magnetUri = decodedUri,
                    )
                )
            } else {
                val uris = decodedUri.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                uris.map {
                    torrentsToBeDownloaded.add(
                        Torrent.of(
                            magnetUri = it,
                        )
                    )
                }
            }
        }
        torrentsToBeDownloaded.map {
            multifileHosterService.addTorrentToTransfer(it)
        }
        multifileHosterService.addTransfersToDownloadQueue()
        return switchToProgress
    }

    @RequestMapping("/boat/tfdb")
    fun searchTfdb(@RequestParam(value = "q") query: String?): String {
        return theFilmDataBaseService.search(query).toString()
    }

    @get:GetMapping("/boat/debug")
    val debugInfo: String
        get() {
            multifileHosterService.refreshTorrents()
            val remoteTorrents: List<Torrent> = multifileHosterService.activeTorrents
            val runtimeBean = ManagementFactory.getRuntimeMXBean()
            val startTime = runtimeBean.startTime
            val startDate = Date(startTime)
            return ("v:" + PropertiesHelper.getVersion() + " started: " + startDate
                    + "<br/>remote host: " + httpHelper.externalHostname()
                    + "<br/>cloud token: " + (if (cloudService.isCloudTokenValid) "✅" else "❌")
                    + "<br/>search Cache: " + (if (cloudFileService.isCacheFilled) "✅" else "❌")
                    + "<br/>ActiveSearchEngines: " + torrentSearchEngineService.getActiveSearchEngines()
                    + "<br/>InActiveSearchEngines: " + torrentSearchEngineService.inActiveSearchEngines
                    + "<br/>Active MultifileHoster: " + multifileHosterService.getActiveMultifileHosters()
                    + "<br/>Active DL MultifileHoster: " + multifileHosterService.getActiveMultifileHosterForDownloads()
                    + "<br/>TrafficLeft: " + humanReadableByteCountBinary(multifileHosterService.getRemainingTrafficInMB().toLong() * 1024 * 1024)
                    + String.format(
                "<br/>Transfers [%d]: %s",
                transferService.getAll().size,
                transferService.getAll()
            ) + String.format("<br/><!-- D [%d]: %s -->", remoteTorrents.size, remoteTorrents) + String.format(
                "<br/>Queue [%d]: %s",
                queueService.getQueue().size,
                queueService.getQueue()
            ))
        }

    @GetMapping("/boat/shutdown")
    fun shutdownServer(): String {
        logger.info("shutdown request received")
        thread(start = true) {
            Thread.sleep(1000)
            exitProcess(0)
        }
        return "shutting down/restarting"
    }

    private fun isNotAlreadyDownloaded(mediaItem: MediaItem): Boolean {
        val existingFiles = cloudService
            .findExistingFiles(getSearchNameFrom(mediaItem))
        return existingFiles.isEmpty()
    }
}