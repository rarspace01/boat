package boat

import boat.info.CloudFileService
import boat.info.CloudService
import boat.info.MediaItem
import boat.info.QueueService
import boat.info.TheFilmDataBaseService
import boat.info.TorrentMetaService
import boat.multifileHoster.MultifileHosterService
import boat.services.ConfigurationService
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
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Arrays
import java.util.Base64
import java.util.Date
import java.util.stream.Collectors
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMethod
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread
import kotlin.system.exitProcess

@RestController
class BoatController @Autowired constructor(
    private val httpHelper: HttpHelper,
    private val torrentSearchEngineService: TorrentSearchEngineService,
    private val cloudService: CloudService,
    private val torrentMetaService: TorrentMetaService?,
    private val theFilmDataBaseService: TheFilmDataBaseService,
    private val multifileHosterService: MultifileHosterService,
    private val queueService: QueueService,
    private val cloudFileService: CloudFileService,
    private val transferService: TransferService,
    private val configurationService: ConfigurationService,
) {

    companion object {
        private val logger by LoggerDelegate()
        const val BREAK_LINK_HTML = "  <br>\n"
    }

    private val switchToProgress = "<a href=\"./debug\">Show Progress</a> "
    private val switchToSearchList = "<a href=\"./searchList\">Search a List</a> "
    private val switchToSearch = "<a href=\"./search\">Search a single Title</a> "

    private val htmlHeader = """<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
    body { font-size: 1.5em; overflow-wrap: break-word; word-wrap: break-word; }
    input[type="text"], textarea { font-size: 1em; width: 95%; box-sizing: border-box; }
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
$BREAK_LINK_HTML$BREAK_LINK_HTML<form action="./download" target="_blank" method="POST">
  Direct download URL (multiple seperate by comma):<br>
  <input type="text" name="dd" value="">
$BREAK_LINK_HTML  <input type="reset" value="Reset">
  <input type="submit" value="Download">
</form>
<br/>
$switchToSearchList${switchToProgress.replace("..", "../boat")}""" + htmlFooter
    }

    @GetMapping("/boat/searchList")
    fun searchList(): String {
        return htmlHeader + """
<h2>Here to serve you</h2>
<form action="./query" target="_blank" method="POST">
Download multiple movies (one per line):<br>
<textarea id="qqq" name="qqq" rows="25" cols="25">
</textarea>
$BREAK_LINK_HTML  <input type="reset" value="Reset">
  <input type="submit" value="Download">
</form>
<br/>
$switchToSearch${switchToProgress}""" + htmlFooter
    }

    @RequestMapping("/boat/query")
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
                return htmlHeader + ("We already found some files:<br/>" + java.lang.String.join("<br/>", existingFiles)
                        + "<br/>Still want to search? <a href=\"?q=" + localSearchString + "\">Yes</a>") + htmlFooter
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
            htmlHeader + torrentList.stream().limit(50).collect(Collectors.toList())
                .joinToString(separator = "") + htmlFooter
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
                htmlHeader + (switchToProgress.replace("..", "../boat") + listOfMediaItems) + htmlFooter
            } else {
                htmlHeader + "Error: nothing in remote url" + htmlFooter
            }
        } else {
            htmlHeader + "Error: nothing to search" + htmlFooter
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

    @GetMapping("/boat/debug")
    fun debugInfo(): String {
        multifileHosterService.refreshTorrents()
        val remoteTorrents: List<Torrent> = multifileHosterService.activeTorrents
        val runtimeBean = ManagementFactory.getRuntimeMXBean()
        val startTime = runtimeBean.startTime
        val startDate = Date(startTime)
        return htmlHeader + ("v:" + PropertiesHelper.getVersion() + " started: " + startDate
                + "<br/>MODE: " + configurationService.getServiceMode()
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
        )) + htmlFooter
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

    @RequestMapping("/pfdb/**")
    fun webdavPfdb(request: HttpServletRequest): ResponseEntity<*> {
        val rootPath = "/PFDB"
        val rootDir = File(rootPath)
        if (!rootDir.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null)
        }

        val requestPath = request.requestURI.substringAfter("/pfdb", "")
        val targetFile = File(rootDir, requestPath)

        if (!targetFile.exists() || !targetFile.canonicalPath.startsWith(rootDir.canonicalPath)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null)
        }

        return when (request.method) {
            "OPTIONS" -> ResponseEntity.ok()
                .header("Allow", "GET, HEAD, POST, OPTIONS, PROPFIND")
                .header("DAV", "1, 2")
                .header("MS-Author-Via", "DAV")
                .build<Void>()
            "PROPFIND" -> {
                val depth = request.getHeader("Depth") ?: "1"
                val xml = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\" ?>")
                xml.append("<D:multistatus xmlns:D=\"DAV:\">")

                fun addFileToXml(file: File, path: String) {
                    val isDirectory = file.isDirectory
                    val name = file.name
                    val fullPath = if (path.endsWith("/")) "$path$name" else "$path/$name"
                    val displayPath = if (isDirectory) "$fullPath/" else fullPath
                    val lastModified = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("GMT")
                    }.format(Date(file.lastModified()))

                    xml.append("<D:response>")
                    xml.append("<D:href>$displayPath</D:href>")
                    xml.append("<D:propstat>")
                    xml.append("<D:prop>")
                    if (isDirectory) {
                        xml.append("<D:resourcetype><D:collection/></D:resourcetype>")
                    } else {
                        xml.append("<D:resourcetype/>")
                        xml.append("<D:getcontentlength>${file.length()}</D:getcontentlength>")
                        val contentType = try {
                            Files.probeContentType(file.toPath())
                        } catch (e: Exception) {
                            null
                        } ?: "application/octet-stream"
                        xml.append("<D:getcontenttype>$contentType</D:getcontenttype>")
                    }
                    xml.append("<D:getlastmodified>$lastModified</D:getlastmodified>")
                    xml.append("<D:displayname>${file.name}</D:displayname>")
                    xml.append("</D:prop>")
                    xml.append("<D:status>HTTP/1.1 200 OK</D:status>")
                    xml.append("</D:propstat>")
                    xml.append("</D:response>")
                }

                val baseRequestPath = request.requestURI.removeSuffix("/")
                addFileToXml(targetFile, baseRequestPath.substringBeforeLast("/", ""))

                if (targetFile.isDirectory && depth != "0") {
                    targetFile.listFiles()?.forEach { child ->
                        addFileToXml(child, baseRequestPath)
                    }
                }
                xml.append("</D:multistatus>")
                ResponseEntity.status(207)
                    .header("Content-Type", "application/xml; charset=utf-8")
                    .body(xml.toString())
            }
            "GET", "HEAD" -> {
                if (targetFile.isDirectory) {
                    val files = targetFile.listFiles()?.sortedBy { it.name } ?: emptyList()
                    val html = StringBuilder("<html><body><h1>Files in ${targetFile.absolutePath}</h1><ul>")
                    if (targetFile != rootDir) {
                        html.append("<li><a href=\"..\">..</a></li>")
                    }
                    files.forEach {
                        val name = if (it.isDirectory) "${it.name}/" else it.name
                        val currentPath = if (requestPath.isEmpty()) "/" else requestPath
                        val baseLink = if (currentPath.endsWith("/")) currentPath else "$currentPath/"
                        html.append("<li><a href=\"/pfdb${baseLink}${it.name}${if (it.isDirectory) "/" else ""}\">$name</a></li>")
                    }
                    html.append("</ul></body></html>")

                    val bytes = html.toString().toByteArray(StandardCharsets.UTF_8)
                    val resource = object : org.springframework.core.io.ByteArrayResource(bytes) {
                        override fun getFilename(): String = "index.html"
                    }

                    ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .contentLength(bytes.size.toLong())
                        .body(resource)
                } else {
                    try {
                        val resource = FileSystemResource(targetFile)
                        val contentType = Files.probeContentType(targetFile.toPath()) ?: "application/octet-stream"
                        ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${targetFile.name}\"")
                            .contentType(MediaType.parseMediaType(contentType))
                            .contentLength(targetFile.length())
                            .body(resource)
                    } catch (e: IOException) {
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
                    }
                }
            }
            else -> ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build<Any>()
        }
    }

    private fun isNotAlreadyDownloaded(mediaItem: MediaItem): Boolean {
        val existingFiles = cloudService
            .findExistingFiles(getSearchNameFrom(mediaItem))
        return existingFiles.isEmpty()
    }
}