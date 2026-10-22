package boat.multifileHoster

import org.springframework.util.StringUtils
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * File names for the files uploaded to the drive, built from the name of the torrent and the url of the file.
 */
object DownloadFileNames {
    fun extractFileNameFromUrl(fileURLFromTorrent: String): String {
        val fileString = URLDecoder.decode(fileURLFromTorrent, StandardCharsets.UTF_8)
        val pattern = Pattern.compile("[^\\/]+\$")
        var foundMatch: String? = null
        val matcher = pattern.matcher(fileString)
        while (matcher.find()) {
            foundMatch = matcher.group()
        }
        return foundMatch?.replace("\\s".toRegex(), ".") ?: ""
    }

    fun buildFilename(name: String?, fileURLFromTorrent: String?): String {
        val fileEndingFromUrl: String? = extractFileEndingFromUrl(fileURLFromTorrent)
        var returnName = if (StringUtils.hasText(name)) name ?: fileURLFromTorrent!! else fileURLFromTorrent!!
        returnName = returnName.replace("\"".toRegex(), "")
        returnName = returnName.replace("\\.torrent".toRegex(), "")
        returnName = returnName.replace("[wW][wW][wW]\\.[A-Za-z0-9-]*\\.[a-zA-Z]+[\\s-]*".toRegex(), "").trim { it <= ' ' }
        returnName = returnName.replace("\\s".toRegex(), ".")
        return if (fileEndingFromUrl?.let { !returnName.contains(it) } ?: true) {
            "$returnName.$fileEndingFromUrl"
        } else {
            returnName
        }
    }

    private fun extractFileEndingFromUrl(fileURLFromTorrent: String?): String? {
        val pattern = Pattern.compile("[A-Za-z0-9]+$")
        var foundMatch: String? = null
        val matcher = fileURLFromTorrent?.let { pattern.matcher(it) }
        matcher?.let {
            while (matcher.find()) {
                foundMatch = matcher.group()
            }
        }
        // remove quotes && .torrent
        return foundMatch?.replace("\"".toRegex(), "")?.replace(".torrent".toRegex(), "") ?: fileURLFromTorrent
    }
}
