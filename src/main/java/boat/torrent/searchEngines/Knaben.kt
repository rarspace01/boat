package boat.torrent.searchEngines

import boat.torrent.HttpUser
import boat.torrent.Torrent
import boat.torrent.TorrentHelper
import boat.torrent.TorrentSearchEngine
import boat.utilities.HttpHelper
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.*

class Knaben internal constructor(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {

    companion object {
        private const val PAGE_SIZE = 300
    }

    override fun searchTorrents(searchName: String): List<Torrent> {
        // the API gives at most PAGE_SIZE hits per request and sorts them by seeders
        return SearchPaging.collect { page ->
            parseTorrentsOnResultPage(httpHelper.getPage(buildSearchUrl(), body = buildSearchBody(searchName, (page - 1) * PAGE_SIZE)), searchName)
        }
    }

    private fun buildSearchUrl(): String {
        return "$baseUrl/v1"
    }

    private fun buildSearchBody(searchName: String, offset: Int): String {
        val body = JsonObject()
        body.addProperty("search_type", "100%")
        body.addProperty("search_field", "title")
        body.addProperty("query", searchName.trim { it <= ' ' })
        body.addProperty("order_by", "seeders")
        body.addProperty("order_direction", "desc")
        body.addProperty("from", offset)
        body.addProperty("size", PAGE_SIZE)
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
            torrentList += SearchHits.parseEach(this, hits.asJsonArray) { jsonTorrentElement: JsonElement ->
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
                tempTorrent
            }
        } catch (exception: Exception) {
            SearchHits.logUnreadablePage(this, pageContent, exception)
        }
        return torrentList
    }

    override fun toString(): String {
        return "Kn"
    }
}
