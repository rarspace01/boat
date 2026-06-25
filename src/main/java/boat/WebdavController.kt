package boat

import boat.model.ByteRange
import boat.model.LimitedInputStream
import boat.utilities.LoggerDelegate
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@RestController
class WebdavController {

    companion object {
        private val logger by LoggerDelegate()
        private const val DAV_ALLOW_HEADER =
            "OPTIONS, GET, HEAD, PROPFIND, PROPPATCH, MKCOL, COPY, MOVE, LOCK, UNLOCK"

        // Cached, thread-safe date formatters to prevent GC overhead and slowdowns in tight loops
        private val LAST_MODIFIED_FORMATTER = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .withZone(ZoneId.of("GMT"))

        private val CREATION_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .withZone(ZoneId.of("GMT"))
    }

    internal var rootDir = File("/PFDB")

    @RequestMapping("/PFDB", "/PFDB/", "/PFDB/**")
    fun webdavPfdb(request: HttpServletRequest): ResponseEntity<Any> {
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

        val serverRelativePath = if (requestPath.isEmpty()) "/PFDB" else "/PFDB/${requestPath.trimStart('/')}"
        val normalizedRequestUri = normalizeCollectionUri(serverRelativePath, targetCanonical.isDirectory)
        val normalizedHref = buildHref(request, normalizedRequestUri, targetCanonical.isDirectory)

        return when (request.method.uppercase(Locale.getDefault())) {
            "OPTIONS" -> ResponseEntity.ok()
                .header(HttpHeaders.ALLOW, DAV_ALLOW_HEADER)
                .header("DAV", "1, 2")
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
                            .header("DAV", "1, 2")
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

                    val responseBuilder = ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_LOCATION, normalizedHref)
                        .header(HttpHeaders.ALLOW, DAV_ALLOW_HEADER)
                        .header("DAV", "1, 2")
                        .contentType(MediaType.TEXT_HTML)
                        .contentLength(bytes.size.toLong())

                    if (request.method.equals("HEAD", ignoreCase = true)) {
                        responseBuilder.build()
                    } else {
                        responseBuilder.body(resource)
                    }
                } else {
                    try {
                        val contentType = getFastContentType(targetCanonical.name)
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
                                .header("DAV", "1, 2")
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

                            val responseBuilder = ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                                .header(HttpHeaders.CONTENT_RANGE, "bytes ${byteRange.start}-${byteRange.endInclusive}/$fileLength")
                                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                                .header(HttpHeaders.CONTENT_LOCATION, normalizedHref)
                                .header(HttpHeaders.ALLOW, DAV_ALLOW_HEADER)
                                .header("DAV", "1, 2")
                                .contentType(MediaType.parseMediaType(contentType))
                                .contentLength(byteRange.length)

                            if (request.method.equals("HEAD", ignoreCase = true)) {
                                responseBuilder.build()
                            } else {
                                responseBuilder.body(resource)
                            }
                        } else {
                            val resource = FileSystemResource(targetCanonical)
                            val responseBuilder = ResponseEntity.ok()
                                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                                .header(HttpHeaders.CONTENT_LOCATION, normalizedHref)
                                .header(HttpHeaders.ALLOW, DAV_ALLOW_HEADER)
                                .header("DAV", "1, 2")
                                .contentType(MediaType.parseMediaType(contentType))
                                .contentLength(fileLength)

                            if (request.method.equals("HEAD", ignoreCase = true)) {
                                responseBuilder.build()
                            } else {
                                responseBuilder.body(resource)
                            }
                        }
                    } catch (_: IOException) {
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
                    }
                }
            }

            else -> ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header(HttpHeaders.ALLOW, DAV_ALLOW_HEADER)
                .header("DAV", "1, 2")
                .build()
        }
    }

    private fun handlePropfind(
        request: HttpServletRequest,
        rootCanonical: File,
        targetCanonical: File,
        normalizedRequestUri: String,
    ): ResponseEntity<Any> {
        val startTime = System.currentTimeMillis()
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
            file = targetCanonical,
            href = buildHref(request, normalizedRequestUri, targetCanonical.isDirectory),
            propertyMode = propertyMode,
            rootCanonical = rootCanonical
        )

        var itemsProcessed = 1
        if (targetCanonical.isDirectory && effectiveDepth != "0") {
            val children = targetCanonical.listFiles()?.sortedBy { it.name.lowercase(Locale.getDefault()) }
            if (children != null) {
                itemsProcessed += children.size
                children.forEach { child ->
                    val childUri = normalizedRequestUri.trimEnd('/') + "/" + child.name + if (child.isDirectory) "/" else ""
                    appendPropfindResponse(
                        xml = xml,
                        file = child,
                        href = buildHref(request, childUri, child.isDirectory),
                        propertyMode = propertyMode,
                        rootCanonical = rootCanonical
                    )
                }
            }
        }

        xml.append("</D:multistatus>")
        val duration = System.currentTimeMillis() - startTime

        logger.info("PROPFIND path='${targetCanonical.absolutePath}' items=$itemsProcessed timeTaken=${duration}ms")

        return ResponseEntity.status(207)
            .header(HttpHeaders.CONTENT_TYPE, "application/xml; charset=utf-8")
            .header(HttpHeaders.CONTENT_LOCATION, buildHref(request, normalizedRequestUri, targetCanonical.isDirectory))
            .header(HttpHeaders.ALLOW, DAV_ALLOW_HEADER)
            .header("DAV", "1, 2")
            .body(xml.toString())
    }

    private fun appendPropfindResponse(
        xml: StringBuilder,
        file: File,
        href: String,
        propertyMode: PropfindMode,
        rootCanonical: File,
    ) {
        val instant = Instant.ofEpochMilli(file.lastModified())
        val lastModified = LAST_MODIFIED_FORMATTER.format(instant)
        val creationDate = CREATION_DATE_FORMATTER.format(instant)

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
                    val contentType = getFastContentType(file.name)
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
            .header("DAV", "1, 2")
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

    internal fun encodePath(path: String): String {
        return path.split("/")
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20")
            }
    }

    internal fun escapeXml(value: String): String {
        // Drop XML 1.0 forbidden control characters that disrupt parsers like Kodi's TinyXML
        val cleaned = value.replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
        return cleaned
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun getFastContentType(filename: String): String {
        val ext = filename.substringAfterLast('.', "").lowercase(Locale.getDefault())
        return when (ext) {
            // --- Video ---
            "mkv" -> "video/x-matroska"
            "mp4", "m4v" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mov", "qt" -> "video/quicktime"
            "wmv" -> "video/x-ms-wmv"
            "flv" -> "video/x-flv"
            "webm" -> "video/webm"
            "mpg", "mpeg", "m2v", "mp2" -> "video/mpeg"
            "3gp", "3g2" -> "video/3gpp"
            "ts", "m2ts", "mts" -> "video/mp2t"
            "vob" -> "video/dvd"
            "ogv" -> "video/ogg"
            "rm", "rmvb" -> "application/vnd.rn-realmedia"
            "asf" -> "video/x-ms-asf"
            "mxf" -> "application/mxf"

            // --- Audio ---
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav" -> "audio/x-wav"
            "aac" -> "audio/aac"
            "ogg", "oga" -> "audio/ogg"
            "m4a", "m4b" -> "audio/mp4"
            "wma" -> "audio/x-ms-wma"
            "alac" -> "audio/alac"
            "ape" -> "audio/x-monkeys-audio"
            "opus" -> "audio/opus"
            "dts" -> "audio/vnd.dts"
            "dts-hd" -> "audio/vnd.dts.hd"
            "ac3" -> "audio/ac3"
            "eac3" -> "audio/eac3"
            "dsf", "dff" -> "audio/dsd"

            // --- Subtitles ---
            "srt" -> "application/x-subrip"
            "vtt" -> "text/vtt"
            "ass", "ssa" -> "text/x-ssa"
            "smi", "sami" -> "application/smil+xml"
            "sub", "txt" -> "text/plain" // Text-based subs or VobSub pairs

            // --- Images (Posters, Fanart, Banners) ---
            "jpg", "jpeg", "jpe" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "tiff", "tif" -> "image/tiff"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "tbn" -> "image/jpeg" // Kodi-specific thumbnail extension

            // --- Kodi Metadata ---
            "nfo" -> "text/plain"
            "xml" -> "application/xml"

            // --- Default / Fallback ---
            else -> "application/octet-stream"
        }
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