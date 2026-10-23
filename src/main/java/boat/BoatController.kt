package boat

import boat.info.MediaProxyService
import boat.info.CloudFileService
import boat.info.CloudService
import boat.info.MediaItem
import boat.info.QueueService
import boat.model.Transfer
import boat.model.TransferStatus
import boat.multifileHoster.MultifileHosterService
import boat.services.ConfigurationService
import boat.services.TransferService
import boat.torrent.Torrent
import boat.torrent.TorrentHelper.humanReadableByteCountBinary
import boat.torrent.TorrentHelper.removeSpecialCharacters
import boat.torrent.TorrentSearchEngineService
import boat.utilities.HttpHelper
import boat.utilities.LoggerDelegate
import boat.utilities.PropertiesHelper
import org.apache.commons.validator.routines.UrlValidator
import org.apache.logging.log4j.util.Strings
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.HtmlUtils
import java.lang.management.ManagementFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlin.system.exitProcess

@RestController
class BoatController @Autowired constructor(
    private val httpHelper: HttpHelper,
    private val torrentSearchEngineService: TorrentSearchEngineService,
    private val cloudService: CloudService,
    private val mediaProxyService: MediaProxyService,
    private val multifileHosterService: MultifileHosterService,
    private val queueService: QueueService,
    private val cloudFileService: CloudFileService,
    private val transferService: TransferService,
    private val configurationService: ConfigurationService,
) {

    companion object {
        /** The titles come from TMDB/TVDB or a pasted list. */
        internal fun queueHtml(queue: List<MediaItem>): String =
            String.format("<br/>Queue [%d]: %s", queue.size, HtmlUtils.htmlEscape(queue.toString()))

        private val logger by LoggerDelegate()
        const val BREAK_LINK_HTML = "  <br>\n"
        private val trailingYearPattern = "^(.+?)[\\s.(\\[]+((?:19|20)[0-9]{2})[)\\]]?$".toRegex()

        /**
         * Splits a line of the search list into title and year, a trailing year ("Dune 1984", "Dune (2021)") is used as
         * year. Null for a blank line.
         */
        internal fun splitSearchListEntry(line: String): Pair<String, Int?>? {
            val entry = line.trim()
            if (entry.isEmpty()) return null
            val match = trailingYearPattern.find(entry)
            val year = match?.groupValues?.get(2)?.toInt()?.takeIf { it <= Calendar.getInstance().get(Calendar.YEAR) + 1 }
            return (if (year != null) match.groupValues[1].trim() else entry) to year
        }

        /** The page of a title at TMDB or TVDB, from its id like `{tmdb-27205}`. */
        internal fun mediaDatabaseUrl(mediaItem: MediaItem): String? {
            val match = Regex("\\{(tmdb|tvdb)-(\\d+)}", RegexOption.IGNORE_CASE).find(mediaItem.id ?: return null) ?: return null
            val (provider, id) = match.destructured
            val isSeries = mediaItem.type == boat.info.MediaType.Series
            return when (provider.lowercase()) {
                "tmdb" -> "https://www.themoviedb.org/${if (isSeries) "tv" else "movie"}/$id"
                else -> "https://thetvdb.com/dereferrer/${if (isSeries) "series" else "movie"}/$id"
            }
        }

        private fun parseMediaType(mediaType: String?): boat.info.MediaType? {
            return boat.info.MediaType.entries.firstOrNull { it.name.equals(mediaType, ignoreCase = true) }
        }

        /**
         * Restart and delete buttons of a transfer on the info page, not while it is uploading to the drive.
         */
        internal fun transferControls(transfer: Transfer): String {
            if (transfer.transferStatus == TransferStatus.UPLOADING_TO_DRIVE) return ""
            val id = HtmlUtils.htmlEscape(transfer.id)
            return """ <form method="post" action="./transferRestart" class="transfer-control">""" +
                    """<input type="hidden" name="id" value="$id"><button type="submit" title="restart">↻</button></form>""" +
                    """<form method="post" action="./transferDelete" class="transfer-control" """ +
                    """onsubmit="return confirm('Delete this transfer and its files on the multifilehoster?')">""" +
                    """<input type="hidden" name="id" value="$id"><button type="submit" title="delete">🗑</button></form>"""
        }
    }

    private val switchToProgress = "<a href=\"./info\">Show Progress</a> "
    private val switchToSearchList = "<a href=\"./searchList\">Search a List</a> "
    private val switchToSearch = "<a href=\"./search\">Search a single Title</a> "
    private val switchToSearchOld = "<a href=\"./searchOld\">Old Search</a> "

    private val htmlHeader = """<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
    body { font-size: 1.5em; overflow-wrap: break-word; word-wrap: break-word; }
    input[type="text"], textarea { font-size: 1em; width: 95%; box-sizing: border-box; }
    form.transfer-control { display: inline; margin-left: 4px; }
    @media (min-width: 800px) {
        body { font-size: 1em; }
        input[type="text"], textarea { width: auto; }
    }
</style>
</head>
<body>
"""

    private val htmlFooter = """</body>
</html>
"""

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

    @GetMapping("/boat/searchOld")
    fun searchOld(): String {
        return htmlHeader + """
<h2>Here to serve you</h2>

<form action="./queryOld" target="_blank" method="GET">
  Title:<br>
  <input type="text" name="qq" value="">
$BREAK_LINK_HTML  <input type="reset" value="Reset">
  <input type="submit" value="Search">
</form>
$BREAK_LINK_HTML$BREAK_LINK_HTML<form action="./download" target="_blank" method="POST">
  Direct download URL (multiple seperate by comma):<br>
  <input type="text" name="dd" value="">
$BREAK_LINK_HTML  <input type="reset" value="Reset">
  <input type="submit" value="Download">
</form>
<br/>
$switchToSearch$switchToSearchList$switchToProgress""" + htmlFooter
    }

    @GetMapping("/boat/searchList")
    fun searchList(): String {
        return htmlHeader + """
<h2>Here to serve you</h2>
<form action="./queueList" target="_blank" method="POST">
Download multiple movies (one per line):<br>
<textarea id="qqq" name="qqq" rows="25" cols="25">
</textarea>
$BREAK_LINK_HTML  <input type="reset" value="Reset">
  <input type="submit" value="Download">
</form>
<br/>
$switchToSearch${switchToProgress}""" + htmlFooter
    }

    @RequestMapping("/boat/queryOld")
    fun searchTorrentsOld(
        @RequestParam(value = "q", required = false) searchString: String?,
        @RequestParam(value = "qq", required = false) localSearchString: String?,
    ): String {
        var searchString = searchString
        val startTime = System.currentTimeMillis()
        if (localSearchString != null && Strings.isNotEmpty(localSearchString)) {
            val existingFiles = cloudService.findExistingFiles(localSearchString)
            searchString = if (existingFiles.isNotEmpty()) {
                return htmlHeader + ("We already found some files:<br/>" + existingFiles.joinToString("<br/>") { HtmlUtils.htmlEscape(it) }
                        + "<br/>Still want to search? <a href=\"?q=" + urlEncode(localSearchString) + "\">Yes</a>") + htmlFooter
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
            htmlHeader + torrentList.joinToString(separator = "") + htmlFooter
        } else {
            htmlHeader + "Error: nothing to search" + htmlFooter
        }
    }

    /** Adds the entries of a list (one title per line, or the url of such a list) to the queue. */
    @PostMapping("/boat/queueList")
    fun queueList(@RequestParam(value = "qqq", required = false) luckySearchList: String?): String {
        if (luckySearchList.isNullOrEmpty()) return htmlHeader + "Error: nothing to search" + htmlFooter
        val urlValidator = UrlValidator(arrayOf("http", "https"))
        val pageWithEntries: String = if (urlValidator.isValid(luckySearchList)) {
            // the url is given by the user, so only public addresses are fetched
            httpHelper.getPublicPage(luckySearchList) ?: return htmlHeader + "Error: could not read the remote url" + htmlFooter
        } else {
            luckySearchList
        }
        if (pageWithEntries.isEmpty()) return htmlHeader + "Error: nothing in remote url" + htmlFooter
        val titles = pageWithEntries.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        // no lookups here, the queue job matches an entry and checks for existing files when it is its turn
        val listOfMediaItems = titles.mapNotNull { splitSearchListEntry(it) }
            .map { (title, year) -> MediaItem(title, title, year, boat.info.MediaType.Other) }
        queueService.addAll(listOfMediaItems)
        return htmlHeader + (switchToProgress + HtmlUtils.htmlEscape(listOfMediaItems.toString()).replace("\n", "<br/>")) + htmlFooter
    }

    /**
     * A title to select: the link of the title does the search for the whole series or the movie, then the link to the
     * page of the title at TMDB or TVDB (to check it is the one), and for a series the seasons to search for.
     */
    private fun mediaItemLine(mediaItem: MediaItem, seasonCount: Int?): String {
        val itemMidParam = (mediaItem.id?.let { "&mid=" + urlEncode(it) } ?: "") + (mediaItem.type?.let { "&mt=${it.name}" } ?: "")
        val itemYearParam = mediaItem.year?.takeIf { mediaItem.type == boat.info.MediaType.Movie }
            ?.let { "&y=$it" } ?: ""
        val searchLink = "?t=" + urlEncode(mediaItem.title) + itemYearParam + itemMidParam
        val line = StringBuilder("<a href=\"$searchLink\">" + HtmlUtils.htmlEscape(mediaItem.title) +
                (mediaItem.year?.let { " ($it)" } ?: "") +
                (mediaItem.type?.let { " [$it]" } ?: "") +
                (mediaItem.id?.let { " " + HtmlUtils.htmlEscape(it) } ?: "") + "</a>")
        mediaDatabaseUrl(mediaItem)?.let { line.append(" <a href=\"${HtmlUtils.htmlEscape(it)}\" target=\"_blank\" rel=\"noopener noreferrer\">${if (it.contains("themoviedb")) "TMDB" else "TVDB"}</a>") }
        if (seasonCount != null && seasonCount > 0) {
            line.append(" ")
            line.append((1..seasonCount).joinToString(" ") { season -> "<a href=\"$searchLink&s=$season\">S$season</a>" })
        }
        return line.append("<br/>\n").toString()
    }

    @GetMapping("/boat/search")
    fun search(): String {
        return htmlHeader + """
<h2>Here to serve you</h2>

<form action="./query" target="_blank" method="GET">
  Title:<br>
  <input type="text" name="qq" value="">
$BREAK_LINK_HTML  <input type="reset" value="Reset">
  <input type="submit" value="Search">
</form>
<br/>
$switchToSearchOld$switchToSearchList$switchToProgress""" + htmlFooter
    }

    /**
     * Media aware search flow:
     * 1. qq: look up the title via [MediaProxyService] and let the user pick a media item
     * 2. t (+ y) + mid + mt: check for already downloaded files of the selected media item
     * 3. q + mid + mt: search torrents, download links carry the remote media id (e.g. {tmdb-123}) and media type
     */
    @RequestMapping("/boat/query")
    fun searchTorrents(
        @RequestParam(value = "qq", required = false) mediaSearchString: String?,
        @RequestParam(value = "t", required = false) selectedTitle: String?,
        @RequestParam(value = "y", required = false) selectedYear: Int?,
        @RequestParam(value = "q", required = false) searchString: String?,
        @RequestParam(value = "mid", required = false) remoteMediaId: String?,
        @RequestParam(value = "mt", required = false) mediaTypeString: String?,
        @RequestParam(value = "s", required = false) selectedSeason: Int?,
    ): String {
        val startTime = System.currentTimeMillis()
        val mediaId = remoteMediaId?.ifBlank { null }
        val mediaType = parseMediaType(mediaTypeString)
        val season = selectedSeason?.takeIf { it > 0 }
        val midParam = (if (mediaId != null) "&mid=" + urlEncode(mediaId) else "") + (mediaType?.let { "&mt=${it.name}" } ?: "")
        return if (!searchString.isNullOrBlank()) {
            val torrentList = torrentSearchEngineService.searchTorrents(searchString)
            logger.info(
                "Took: [{}]ms for [{}]{} found [{}]", System.currentTimeMillis() - startTime, searchString,
                mediaId ?: "", torrentList.size
            )
            htmlHeader + torrentList.joinToString(separator = "") { it.toHtml(mediaId, mediaType) } + htmlFooter
        } else if (!selectedTitle.isNullOrBlank()) {
            val cleanedTitle = removeSpecialCharacters(selectedTitle)
            val torrentSearchString = cleanedTitle + (selectedYear?.let { " $it" } ?: "") + (season?.let { " S%02d".format(it) } ?: "")
            val existingFiles = (cloudService.findExistingFiles(cleanedTitle) +
                    (mediaId?.let { cloudService.findExistingFiles(it) } ?: emptyList())).distinct()
            if (existingFiles.isNotEmpty()) {
                htmlHeader + ("We already found some files:<br/>" + existingFiles.joinToString("<br/>") { HtmlUtils.htmlEscape(it) }
                        + "<br/>Still want to search? <a href=\"?q=" + urlEncode(torrentSearchString) + midParam + "\">Yes</a>") + htmlFooter
            } else {
                searchTorrents(null, null, null, torrentSearchString, mediaId, mediaType?.name, null)
            }
        } else if (!mediaSearchString.isNullOrBlank()) {
            val mediaItems = mediaProxyService.searchWithTrailingYear(mediaSearchString)
            logger.info(
                "Took: [{}]ms for media search [{}] found [{}]", System.currentTimeMillis() - startTime,
                mediaSearchString, mediaItems.size
            )
            // the searches don't tell the number of seasons of a series, its details do
            // in parallel on threads of their own, not on the common pool (cores - 1 threads, the requests only wait)
            val seasonCounts = Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                mediaItems.map { executor.submit<Int?> { mediaProxyService.seasonCountOf(it) } }.map { it.get() }
            }
            val mediaList = mediaItems.indices.joinToString(separator = "") { index -> mediaItemLine(mediaItems[index], seasonCounts[index]) }
            htmlHeader + (if (mediaItems.isEmpty()) "No media found.<br/>" else "Select a title:<br/>$mediaList") +
                    htmlFooter
        } else {
            htmlHeader + "Error: nothing to search" + htmlFooter
        }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    @RequestMapping("/boat/download")
    fun downloadTorrentToMultifileHoster(
        @RequestParam(value = "d", required = false) downloadUri: String?,
        @RequestParam(value = "dd", required = false) directDownloadUri: String?,
        @RequestParam(value = "mid", required = false) remoteMediaId: String?,
        @RequestParam(value = "mt", required = false) mediaTypeString: String?,
    ): ResponseEntity<String> {
        val uris = if (Strings.isNotEmpty(downloadUri)) {
            val decoded = runCatching { String(Base64.getUrlDecoder().decode(downloadUri!!.trim()), StandardCharsets.UTF_8) }.getOrNull()
                ?: return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("d is not Base64")
            listOf(decoded)
        } else {
            // a comma separated list of urls
            directDownloadUri.orEmpty().split(",").map { it.trim() }
        }
        val torrentsToBeDownloaded = uris.filter { it.isNotBlank() }.map { Torrent.of(magnetUri = it) }
        torrentsToBeDownloaded.forEach {
            multifileHosterService.addTorrentToTransfer(it, remoteMediaId?.ifBlank { null }, parseMediaType(mediaTypeString))
        }
        multifileHosterService.addTransfersToDownloadQueue()
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(switchToProgress)
    }

    @RequestMapping("/boat/tfdb")
    fun searchTfdb(@RequestParam(value = "q") query: String?): String {
        return HtmlUtils.htmlEscape(mediaProxyService.search(query).toString())
    }

    @GetMapping("/boat/info")
    fun info(@RequestParam(value = "message", required = false) message: String?): String {
        multifileHosterService.refreshTorrents()
        val remoteTorrents: List<Torrent> = multifileHosterService.activeTorrents
        val runtimeBean = ManagementFactory.getRuntimeMXBean()
        val startTime = runtimeBean.startTime
        val startDate = Date(startTime)
        val transfers = transferService.getAll()
        return htmlHeader + (message?.takeIf { it.isNotBlank() }?.let { "<b>" + HtmlUtils.htmlEscape(it) + "</b><br/>" } ?: "") +
                ("v:" + PropertiesHelper.version + " started: " + startDate
                + "<br/>MODE: " + configurationService.getServiceMode()
                + "<br/>target folder: " + (if (cloudService.isTargetFolderReady) "✅" else "❌")
                + "<br/>search Cache: " + (if (cloudFileService.isCacheFilled) "✅" else "❌")
                + "<br/>ActiveSearchEngines: " + torrentSearchEngineService.getActiveSearchEngines()
                + "<br/>InActiveSearchEngines: " + torrentSearchEngineService.inActiveSearchEngines
                + "<br/>Active MultifileHoster: " + multifileHosterService.getActiveMultifileHosters()
                + "<br/>Active DL MultifileHoster: " + multifileHosterService.getActiveMultifileHosterForDownloads()
                + "<br/>TrafficLeft: " + humanReadableByteCountBinary(multifileHosterService.getRemainingTrafficInMB().toLong() * 1024 * 1024)
                + String.format(
            "<br/>Transfers [%d]: %s",
            transfers.size,
            transfers.joinToString(separator = ", ", prefix = "[", postfix = "]") { it.toString() + transferControls(it) }
        ) + String.format("<br/><!-- D [%d]: %s -->", remoteTorrents.size, remoteTorrents) + queueHtml(queueService.getQueue())) + htmlFooter
    }

    @PostMapping("/boat/transferDelete")
    fun deleteTransfer(@RequestParam(value = "id") transferId: String): ResponseEntity<Void> {
        logger.info("delete transfer request received: {}", transferId)
        return redirectToInfo(multifileHosterService.deleteTransfer(transferId))
    }

    @PostMapping("/boat/transferRestart")
    fun restartTransfer(@RequestParam(value = "id") transferId: String): ResponseEntity<Void> {
        logger.info("restart transfer request received: {}", transferId)
        return redirectToInfo(multifileHosterService.restartTransfer(transferId))
    }

    // 303, so reloading the info page doesn't send the form again
    private fun redirectToInfo(message: String): ResponseEntity<Void> {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
            .header(HttpHeaders.LOCATION, "./info?message=" + urlEncode(message))
            .build()
    }

    /** The former name of the info page, for bookmarks and links. */
    @GetMapping("/boat/debug")
    fun debugRedirect(@RequestParam(value = "message", required = false) message: String?): ResponseEntity<Void> {
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
            .header(HttpHeaders.LOCATION, "./info" + (message?.let { "?message=" + urlEncode(it) } ?: ""))
            .build()
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

    @GetMapping("/boat/clearMediaCache")
    fun clearMediaCache(): String {
        logger.info("clear media cache request received")
        val removedEntries = mediaProxyService.clearCache()
        return "cleared media cache, removed $removedEntries entries"
    }
}
