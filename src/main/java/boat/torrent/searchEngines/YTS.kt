package boat.torrent.searchEngines

import boat.torrent.HttpUser
import boat.torrent.Torrent
import boat.torrent.TorrentComparator
import boat.torrent.TorrentHelper
import boat.torrent.TorrentSearchEngine
import boat.utilities.HttpHelper
import boat.utilities.LoggerDelegate
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

class YTS internal constructor(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {

    companion object {
        private val logger by LoggerDelegate()
    }

    override fun searchTorrents(searchName: String): List<Torrent> {
        val torrentList = CopyOnWriteArrayList<Torrent>()
        val resultString = httpHelper.getPage(buildSearchUrl(searchName))
        torrentList.addAll(parseTorrentsOnResultPage(resultString, searchName))
        torrentList.sortWith(TorrentComparator)
        return torrentList
    }

    private fun buildSearchUrl(searchName: String): String {
        return String.format(
                "%s/api/v2/list_movies.json?limit=50&query_term=%s&sort_by=seeds",
                baseUrl,
                URLEncoder.encode(searchName, StandardCharsets.UTF_8)
        )
    }

    override fun getBaseUrl(): String {
        return "https://yts.mx"
    }

    private fun parseTorrentsOnResultPage(pageContent: String, searchName: String): List<Torrent> {
        val torrentList = ArrayList<Torrent>()
        try {
            val jsonRoot = JsonParser.parseString(pageContent)
            if (jsonRoot == null || !jsonRoot.isJsonObject) {
                return torrentList
            }
            val data = jsonRoot.asJsonObject["data"] ?: return torrentList
            val results = data.asJsonObject["movies"] ?: return torrentList
            val jsonArray = results.asJsonArray
            jsonArray.forEach(Consumer { jsonTorrentElement: JsonElement ->
                val tempTorrent = Torrent(toString())
                val jsonTorrent = jsonTorrentElement.asJsonObject
                tempTorrent.name = jsonTorrent["title"].asString + " " + jsonTorrent["year"].asInt
                val bestTorrentSource = retrieveBestTorrent(jsonTorrent["torrents"].asJsonArray)
                tempTorrent.isVerified = true
                tempTorrent.magnetUri = TorrentHelper.buildMagnetUriFromHash(bestTorrentSource!!["hash"].asString.lowercase(Locale.getDefault()), tempTorrent.name)
                tempTorrent.seeder = bestTorrentSource["seeds"].asInt
                tempTorrent.leecher = bestTorrentSource["peers"].asInt
                tempTorrent.size = bestTorrentSource["size"].asString
                tempTorrent.sizeInMB = (bestTorrentSource["size_bytes"].asLong / 1024.0f / 1024.0f).toDouble()
                tempTorrent.date = Date(bestTorrentSource["date_uploaded_unix"].asLong * 1000)
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

    private fun retrieveBestTorrent(torrentElements: JsonArray): JsonObject? {
        val bestTorrent = AtomicReference<JsonObject?>()
        torrentElements.forEach(Consumer { torrentElement: JsonElement ->
            if (bestTorrent.get() == null || bestTorrent.get()!!["size_bytes"].asLong < torrentElement
                            .asJsonObject["size_bytes"].asLong
            ) {
                bestTorrent.set(torrentElement.asJsonObject)
            }
        })
        return bestTorrent.get()
    }

    override fun toString(): String {
        return this.javaClass.simpleName
    }
}