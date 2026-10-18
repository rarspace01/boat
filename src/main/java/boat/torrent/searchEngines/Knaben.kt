package boat.torrent.searchEngines

import boat.torrent.HttpUser
import boat.torrent.Torrent
import boat.torrent.TorrentComparator
import boat.torrent.TorrentHelper
import boat.torrent.TorrentSearchEngine
import boat.utilities.HttpHelper
import boat.utilities.LoggerDelegate
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

class Knaben internal constructor(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {

    companion object {
        private val logger by LoggerDelegate()
    }

    override fun searchTorrents(searchName: String): List<Torrent> {
        val torrentList = CopyOnWriteArrayList<Torrent>()
        val resultString = httpHelper.getPage(buildSearchUrl(), body = buildSearchBody(searchName))
        torrentList.addAll(parseTorrentsOnResultPage(resultString, searchName))
        torrentList.sortWith(TorrentComparator)
        return torrentList
    }

    private fun buildSearchUrl(): String {
        return "$baseUrl/v1"
    }

    private fun buildSearchBody(searchName: String): String {
        val body = JsonObject()
        body.addProperty("search_type", "100%")
        body.addProperty("search_field", "title")
        body.addProperty("query", searchName.trim { it <= ' ' })
        body.addProperty("order_by", "seeders")
        body.addProperty("order_direction", "desc")
        body.addProperty("from", 0)
        body.addProperty("size", 100)
        body.addProperty("hide_unsafe", true)
        body.addProperty("hide_xxx", true)
        return body.toString()
    }

    override fun getBaseUrl(): String {
        return "https://api.knaben.org"
    }

    private fun parseTorrentsOnResultPage(pageContent: String, searchName: String): List<Torrent> {
        val torrentList = ArrayList<Torrent>()
        try {
            val jsonRoot = JsonParser.parseString(pageContent)
            if (jsonRoot == null || !jsonRoot.isJsonObject) {
                return torrentList
            }
            val hits = jsonRoot.asJsonObject["hits"] ?: return torrentList
            hits.asJsonArray.forEach(Consumer { jsonTorrentElement: JsonElement ->
                val tempTorrent = Torrent(toString())
                val jsonTorrent = jsonTorrentElement.asJsonObject
                tempTorrent.name = jsonTorrent["title"].asString
                val magnetUrl = jsonTorrent["magnetUrl"]
                tempTorrent.magnetUri = if (magnetUrl != null && !magnetUrl.isJsonNull && magnetUrl.asString.isNotBlank()) {
                    magnetUrl.asString
                } else if (jsonTorrent["hash"] != null && !jsonTorrent["hash"].isJsonNull) {
                    TorrentHelper.buildMagnetUriFromHash(
                        jsonTorrent["hash"].asString.lowercase(Locale.getDefault()),
                        tempTorrent.name
                    )
                } else {
                    ""
                }
                tempTorrent.seeder = jsonTorrent["seeders"].asInt
                tempTorrent.leecher = jsonTorrent["peers"].asInt
                val sizeInBytes = jsonTorrent["bytes"].asLong
                tempTorrent.size = TorrentHelper.humanReadableByteCountBinary(sizeInBytes)
                tempTorrent.sizeInMB = (sizeInBytes / 1024.0f / 1024.0f).toDouble()
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
        return "Kn"
    }
}
