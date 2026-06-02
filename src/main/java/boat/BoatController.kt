package boat

import boat.info.*
import boat.model.ByteRange
import boat.model.LimitedInputStream
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
import jakarta.servlet.http.HttpServletRequest
import org.apache.commons.validator.routines.UrlValidator
import org.apache.logging.log4j.util.Strings
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.bind.annotation.*
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.lang.management.ManagementFactory
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.*
import java.util.stream.Collectors
import kotlin.concurrent.thread
import kotlin.system.exitProcess

@RestController
class BoatController @Autowired constructor(
    private val httpHelper: HttpHelper,
    private val torrentSearchEngineService: TorrentSearchEngineService,
    private val cloudService: CloudService,
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
        private const val DAV_ALLOW_HEADER = "OPTIONS, GET, HEAD, PROPFIND"
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
        return htmlHeader + ("v:" + PropertiesHelper.version + " started: " + startDate
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

    @RequestMapping("/PFDB/**")
    fun webdavPfdb(request: HttpServletRequest): ResponseEntity<Any> {
        val rootDir = File("/PFDB")
        if (!rootDir.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }

        val encodedRequestPath = request.requestURI.substringAfter("/PFDB", "")
        val requestPath = URLDecoder.decode(encodedRequestPath, StandardCharsets.UTF_8)
        val targetFile = File(rootDir, requestPath)

        if (!targetFile.exists()) {
            return davNotFound()
        }

        val rootCanonical = try {
            rootDir.canonicalFile
        } catch (_: IOException) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }

        val targetCanonical = try {
            targetFile.canonicalFile
        } catch (_: IOException) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }

        if (!targetCanonical.path.startsWith(rootCanonical.path)) {
            return davNotFound()
        }

        val normalizedRequestUri = normalizeCollectionUri(request.requestURI, targetCanonical.isDirectory)
        val normalizedHref = buildHref(request, normalizedRequestUri, targetCanonical.isDirectory)

        return when (request.method.uppercase(Locale.getDefault())) {
            "OPTIONS" -> ResponseEntity.ok()
                .header(HttpHeaders.ALLOW, DAV_ALLOW_HEADER)
                .header("DAV", "1")
                .header("MS-Author-Via", "DAV")
                .build()

            "PROPFIND" -> handlePropfind(request, rootCanonical, targetCanonical, normalizedRequestUri)

            "GET", "HEAD" -> {
                if (targetCanonical.isDirectory) {
                    if (!request.requestURI.endsWith("/")) {
                        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                            .header(HttpHeaders.LOCATION, normalizedHref)
                            .header(HttpHeaders.CONTENT_LOCATION, normalizedHref)
                            .header(HttpHeaders.ALLOW, DAV_ALLOW_HEADER)
                            .header("DAV", "1")
                            .build()
                    }

                    val files = targetCanonical.listFiles()?.sortedBy { it.name.lowercase(Locale.getDefault()) } ?: emptyList()
                    val html = StringBuilder("<html><body><h1>Files in ${escapeXml(targetCanonical.absolutePath)}</h1><ul>")
                    if (targetCanonical != rootCanonical) {
                        html.append("<li><a href=\"..\">..</a></li>")
                    }

                    files.forEach {
                        val name = if (it.isDirectory) "${it.name}/" else it.name
                        val href = encodePath(it.name) + if (it.isDirectory) "/" else ""
                        html.append("<li><a href=\"$href\">${escapeXml(name)}</a></li>")
                    }
                    html.append("</ul></body></html>")

                    val bytes = html.toString().toByteArray(StandardCharsets.UTF_8)
                    val resource = object : ByteArrayResource(bytes) {
                        override fun getFilename(): String = "index.html"
                    }

                    if (request.method.equals("HEAD", ignoreCase = true)) {
                        ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_LOCATION, normalizedHref)
                            .header(HttpHeaders.ALLOW, DAV_ALLOW_HEADER)
                            .header("DAV", "1")
                            .contentType(MediaType.TEXT_HTML)
                            .contentLength(bytes.size.toLong())
                            .build()
                    } else {
                        ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_LOCATION, normalizedHref)
                            .header(HttpHeaders.ALLOW, DAV_ALLOW_HEADER)
                            .header("DAV", "1")
                            .contentType(MediaType.TEXT_HTML)
                            .contentLength(bytes.size.toLong())
                            .body(resource)
                    }
                } else {
                    try {
                        val contentType = Files.probeContentType(targetCanonical.toPath()) ?: "application/octet-stream"
                        val encodedFilename = URLEncoder.encode(targetCanonical.name, StandardCharsets.UTF_8).replace("+", "%20")
                        val contentDisposition =
                            "attachment; filename=\"${targetCanonical.name.replace("\"", "\\\"")}\"; filename*=UTF-8''$encodedFilename"
                        val fileLength = targetCanonical.length()
                        val byteRange = parseSingleByteRange(request.getHeader(HttpHeaders.RANGE), fileLength)

                        if (request.getHeader(HttpHeaders.RANGE) != null && byteRange == null) {
                            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                                .header(HttpHeaders.CONTENT_RANGE, "bytes */$fileLength")
                                .header(HttpHeaders.ALLOW, DAV_ALLOW_HEADER)
                                .header("DAV", "1")
                                .build()
                        }

                        if (byteRange != null) {
                            val inputStream = FileInputStream(targetCanonical)
                            inputStream.skipNBytes(byteRange.start)
                            val resource = object : InputStreamResource(
                                LimitedInputStream(inputStream, byteRange.length)
                            ) {
                                override fun getFilename(): String = targetCanonical.name
                            }

                            if (request.method.equals("HEAD", ignoreCase = true)) {
                                ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                                    .header(HttpHeaders.CONTENT_RANGE, "bytes ${byteRange.start}-${byteRange.endInclusive}/$fileLength")
                                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                                    .header(HttpHeaders.CONTENT_LOCATION, normalizedHref)
                                    .header(HttpHeaders.ALLOW, DAV_ALLOW_HEADER)
                                    .header("DAV", "1")
                                    .contentType(MediaType.parseMediaType(contentType))
                                    .contentLength(byteRange.length)
                                    .build()
                            } else {
                                ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                                    .header(HttpHeaders.CONTENT_RANGE, "bytes ${byteRange.start}-${byteRange.endInclusive}/$fileLength")
                                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                                    .header(HttpHeaders.CONTENT_LOCATION, normalizedHref)
                                    .header(HttpHeaders.ALLOW, DAV_ALLOW_HEADER)
                                    .header("DAV", "1")
                                    .contentType(MediaType.parseMediaType(contentType))
                                    .contentLength(byteRange.length)
                                    .body(resource)
                            }
                        } else {
                            val resource = FileSystemResource(targetCanonical)
                            if (request.method.equals("HEAD", ignoreCase = true)) {
                                ResponseEntity.ok()
                                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                                    .header(HttpHeaders.CONTENT_LOCATION, normalizedHref)
                                    .header(HttpHeaders.ALLOW, DAV_ALLOW_HEADER)
                                    .header("DAV", "1")
                                    .contentType(MediaType.parseMediaType(contentType))
                                    .contentLength(fileLength)
                                    .build()
                            } else {
                                ResponseEntity.ok()
                                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                                    .header(HttpHeaders.CONTENT_LOCATION, normalizedHref)
                                    .header(HttpHeaders.ALLOW, DAV_ALLOW_HEADER)
                                    .header("DAV", "1")
                                    .contentType(MediaType.parseMediaType(contentType))
                                    .contentLength(fileLength)
                                    .body(resource)
                            }
                        }
                    } catch (_: IOException) {
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
                    }
                }
            }

            else -> ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header(HttpHeaders.ALLOW, DAV_ALLOW_HEADER)
                .header("DAV", "1")
                .build()
        }
    }

    private fun handlePropfind(
        request: HttpServletRequest,
        rootCanonical: File,
        targetCanonical: File,
        normalizedRequestUri: String,
    ): ResponseEntity<Any> {
        if (targetCanonical.isDirectory && !request.requestURI.endsWith("/")) {
            val normalizedHref = buildHref(request, normalizedRequestUri, true)
            return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, normalizedHref)
                .header(HttpHeaders.CONTENT_LOCATION, normalizedHref)
                .header(HttpHeaders.ALLOW, DAV_ALLOW_HEADER)
                .header("DAV", "1")
                .build()
        }

        val depth = (request.getHeader("Depth") ?: "infinity").trim()
        val effectiveDepth = when (depth.lowercase(Locale.getDefault())) {
            "0", "1" -> depth
            else -> "1"
        }

        val body = try {
            request.inputStream.readAllBytes().toString(StandardCharsets.UTF_8)
        } catch (_: IOException) {
            ""
        }

        val propertyMode = when {
            body.contains("<D:propname", ignoreCase = true) || body.contains(":propname", ignoreCase = true) -> PropfindMode.PROPNAME
            body.contains("<D:prop", ignoreCase = true) || body.contains(":prop>", ignoreCase = true) -> PropfindMode.PROP
            else -> PropfindMode.ALLPROP
        }

        val xml = StringBuilder()
        xml.append("""<?xml version="1.0" encoding="utf-8"?>""")
        xml.append("""<D:multistatus xmlns:D="DAV:">""")

        appendPropfindResponse(
            xml = xml,
            request = request,
            file = targetCanonical,
            href = buildHref(request, normalizedRequestUri, targetCanonical.isDirectory),
            propertyMode = propertyMode,
            rootCanonical = rootCanonical
        )

        if (targetCanonical.isDirectory && effectiveDepth != "0") {
            targetCanonical.listFiles()
                ?.sortedBy { it.name.lowercase(Locale.getDefault()) }
                ?.forEach { child ->
                    val childUri = normalizedRequestUri.trimEnd('/') + "/" + encodePath(child.name) + if (child.isDirectory) "/" else ""
                    appendPropfindResponse(
                        xml = xml,
                        request = request,
                        file = child,
                        href = buildHref(request, childUri, child.isDirectory),
                        propertyMode = propertyMode,
                        rootCanonical = rootCanonical
                    )
                }
        }

        xml.append("</D:multistatus>")

        return ResponseEntity.status(207)
            .header(HttpHeaders.CONTENT_TYPE, "application/xml; charset=utf-8")
            .header(HttpHeaders.CONTENT_LOCATION, buildHref(request, normalizedRequestUri, targetCanonical.isDirectory))
            .header(HttpHeaders.ALLOW, DAV_ALLOW_HEADER)
            .header("DAV", "1")
            .body(xml.toString())
    }

    private fun appendPropfindResponse(
        xml: StringBuilder,
        request: HttpServletRequest,
        file: File,
        href: String,
        propertyMode: PropfindMode,
        rootCanonical: File,
    ) {
        val lastModified = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }.format(Date(file.lastModified()))

        val creationDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }.format(Date(file.lastModified()))

        val etag = "\"${file.name}-${file.lastModified()}-${file.length()}\""
        val displayName = if (file == rootCanonical) "PFDB" else file.name

        xml.append("<D:response>")
        xml.append("<D:href>${escapeXml(href)}</D:href>")

        when (propertyMode) {
            PropfindMode.PROPNAME -> {
                xml.append("<D:propstat><D:prop>")
                xml.append("<D:displayname/>")
                xml.append("<D:creationdate/>")
                xml.append("<D:getlastmodified/>")
                xml.append("<D:resourcetype/>")
                xml.append("<D:supportedlock/>")
                if (!file.isDirectory) {
                    xml.append("<D:getcontentlength/>")
                    xml.append("<D:getcontenttype/>")
                    xml.append("<D:getetag/>")
                }
                xml.append("</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>")
            }

            PropfindMode.ALLPROP,
            PropfindMode.PROP -> {
                xml.append("<D:propstat><D:prop>")
                xml.append("<D:displayname>${escapeXml(displayName)}</D:displayname>")
                xml.append("<D:creationdate>$creationDate</D:creationdate>")
                xml.append("<D:getlastmodified>$lastModified</D:getlastmodified>")

                if (file.isDirectory) {
                    xml.append("<D:resourcetype><D:collection/></D:resourcetype>")
                } else {
                    xml.append("<D:resourcetype/>")
                    xml.append("<D:getcontentlength>${file.length()}</D:getcontentlength>")
                    val contentType = try {
                        Files.probeContentType(file.toPath())
                    } catch (_: Exception) {
                        null
                    } ?: "application/octet-stream"
                    xml.append("<D:getcontenttype>${escapeXml(contentType)}</D:getcontenttype>")
                    xml.append("<D:getetag>${escapeXml(etag)}</D:getetag>")
                }

                xml.append("<D:supportedlock/>")
                xml.append("</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>")
            }
        }

        xml.append("</D:response>")
    }

    private fun davNotFound(): ResponseEntity<Any> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .header(HttpHeaders.ALLOW, DAV_ALLOW_HEADER)
            .header("DAV", "1")
            .build()
    }

    private fun normalizeCollectionUri(requestUri: String, isDirectory: Boolean): String {
        return if (isDirectory && !requestUri.endsWith("/")) "$requestUri/" else requestUri
    }

    private enum class PropfindMode {
        ALLPROP,
        PROPNAME,
        PROP,
    }

    private fun isNotAlreadyDownloaded(mediaItem: MediaItem): Boolean {
        val existingFiles = cloudService.findExistingFiles(getSearchNameFrom(mediaItem))
        return existingFiles.isEmpty()
    }

    internal fun encodePath(path: String): String {
        return path.split("/")
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20")
            }
    }

    internal fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun parseSingleByteRange(rangeHeader: String?, fileLength: Long): ByteRange? {
        if (rangeHeader.isNullOrBlank() || !rangeHeader.startsWith("bytes=")) {
            return null
        }

        val rangeSpec = rangeHeader.removePrefix("bytes=").trim()
        if (rangeSpec.contains(",")) {
            return null
        }

        val parts = rangeSpec.split("-", limit = 2)
        if (parts.size != 2) {
            return null
        }

        val startPart = parts[0].trim()
        val endPart = parts[1].trim()

        val range = when {
            startPart.isEmpty() -> {
                val suffixLength = endPart.toLongOrNull() ?: return null
                if (suffixLength <= 0) {
                    return null
                }

                val start = maxOf(fileLength - suffixLength, 0)
                ByteRange(start, fileLength - 1)
            }

            else -> {
                val start = startPart.toLongOrNull() ?: return null
                val end = if (endPart.isEmpty()) {
                    fileLength - 1
                } else {
                    endPart.toLongOrNull() ?: return null
                }

                ByteRange(start, minOf(end, fileLength - 1))
            }
        }

        if (fileLength <= 0 || range.start < 0 || range.start >= fileLength || range.endInclusive < range.start) {
            return null
        }

        return range
    }

    fun buildHref(request: HttpServletRequest, path: String, isDirectory: Boolean): String {
        val forwardedProto = request.getHeader("X-Forwarded-Proto")
        val forwardedHost = request.getHeader("X-Forwarded-Host")
        val forwardedPort = request.getHeader("X-Forwarded-Port")

        val scheme = forwardedProto ?: request.scheme
        val hostHeader = forwardedHost ?: request.serverName
        val port = forwardedPort?.toIntOrNull() ?: request.serverPort

        val base = if (
            (scheme == "http" && port == 80) ||
            (scheme == "https" && port == 443)
        ) {
            "$scheme://$hostHeader"
        } else {
            if (hostHeader.contains(":") && !hostHeader.contains("]") && hostHeader.count { it == ':' } > 1) {
                "$scheme://[$hostHeader]:$port"
            } else {
                "$scheme://$hostHeader:$port"
            }
        }

        val normalized = if (isDirectory && !path.endsWith("/")) "$path/" else path
        return base + encodePath(normalized)
    }
}