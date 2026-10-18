package boat.torrent.searchEngines

import boat.torrent.HttpUser
import boat.torrent.Torrent
import boat.torrent.TorrentComparator
import boat.torrent.TorrentHelper
import boat.torrent.TorrentSearchEngine
import boat.utilities.HttpHelper
import boat.utilities.LoggerDelegate
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

class TorrentsCSV internal constructor(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {

    companion object {
        private val logger by LoggerDelegate()
    }

    override fun searchTorrents(searchName: String): List<Torrent> {
        // the search needs at least 3 characters
        if (searchName.trim { it <= ' ' }.length < 3) {
            return emptyList()
        }
        val torrentList = CopyOnWriteArrayList<Torrent>()
        val resultString = httpHelper.getPage(buildSearchUrl(searchName))
        torrentList.addAll(parseTorrentsOnResultPage(resultString, searchName))
        torrentList.sortWith(TorrentComparator)
        return torrentList
    }

    private fun buildSearchUrl(searchName: String): String {
        return String.format(
            "%s/service/search?size=100&q=%s", baseUrl,
            URLEncoder.encode(searchName.trim { it <= ' ' }, StandardCharsets.UTF_8)
        )
    }

    override fun getBaseUrl(): String {
        return "https://torrents-csv.com"
    }

    private fun parseTorrentsOnResultPage(pageContent: String, searchName: String): List<Torrent> {
        val torrentList = ArrayList<Torrent>()
        try {
            val jsonRoot = JsonParser.parseString(pageContent)
            if (jsonRoot == null || !jsonRoot.isJsonObject) {
                return torrentList
            }
            val results = jsonRoot.asJsonObject["torrents"] ?: return torrentList
            results.asJsonArray.forEach(Consumer { jsonTorrentElement: JsonElement ->
                val tempTorrent = Torrent(toString())
                val jsonTorrent = jsonTorrentElement.asJsonObject
                tempTorrent.name = jsonTorrent["name"].asString
                tempTorrent.magnetUri = TorrentHelper.buildMagnetUriFromHash(
                    jsonTorrent["infohash"].asString.lowercase(Locale.getDefault()),
                    tempTorrent.name
                )
                tempTorrent.seeder = jsonTorrent["seeders"]?.takeIf { !it.isJsonNull }?.asInt ?: 0
                tempTorrent.leecher = jsonTorrent["leechers"]?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val sizeInBytes = jsonTorrent["size_bytes"].asLong
                tempTorrent.size = TorrentHelper.humanReadableByteCountBinary(sizeInBytes)
                tempTorrent.sizeInMB = (sizeInBytes / 1024.0f / 1024.0f).toDouble()
                tempTorrent.date = Date(jsonTorrent["created_unix"].asLong * 1000)
                TorrentHelper.evaluateRating(tempTorrent, searchName)
                if (TorrentHelper.isValidTorrent(tempTorrent)) {
                    torrentList.add(tempTorrent)
                }
            })
        } catch (exception: Exception) {
            logger.error("failed to parse string:\n${pageContent}", exception)
        }
        return torrentList
    }

    override fun toString(): String {
        return "TC"
    }
}
