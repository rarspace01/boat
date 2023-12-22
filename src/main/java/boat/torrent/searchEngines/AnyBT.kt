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
import org.jsoup.Jsoup
import java.lang.Double
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

class AnyBT internal constructor(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {

    companion object {
        private val logger by LoggerDelegate()
    }

    override fun searchTorrents(searchName: String): List<Torrent> {
        val torrentList = CopyOnWriteArrayList<Torrent>()
        val resultString = httpHelper.getPage(buildSearchUrl(searchName), body = "{\"sql\":\"select /*+ SET_VAR(full_text_option='{\\\"highlight\\\":{ \\\"style\\\":\\\"html\\\",\\\"fields\\\":[\\\"file_name\\\"]}}') */ file_name,filesize,total_count,_id,category,firstadd_utc_timestamp,_score from library.dht where query_string('file_name:\\\\\\\"${searchName}\\\\\\\"^1') order by total_count desc limit 0, 200\",\"arguments\":[]}")
        torrentList.addAll(parseTorrentsOnResultPage(resultString, searchName))
        torrentList.sortWith(TorrentComparator)
        return torrentList
    }

    private fun buildSearchUrl(searchName: String): String {
        return "$baseUrl/blockved/glitterchain/index/sql/simple_query"
    }

    override fun getBaseUrl(): String {
        return "https://gateway.magnode.ru"
    }

    private fun parseTorrentsOnResultPage(pageContent: String, searchName: String): List<Torrent> {
        val torrentList = ArrayList<Torrent>()
        try {
            val jsonRoot = JsonParser.parseString(pageContent)
            if (jsonRoot == null || !jsonRoot.isJsonObject) {
                return torrentList
            }
            val results = jsonRoot.asJsonObject["result"] ?: return torrentList
            val jsonArray = results.asJsonArray
            jsonArray.forEach(Consumer { jsonTorrentElement: JsonElement ->
                val tempTorrent = Torrent(toString())
                val jsonTorrent = jsonTorrentElement.asJsonObject.getAsJsonObject("row")
                tempTorrent.name = jsonTorrent["file_name"].asJsonObject.get("value").asString
                tempTorrent.isVerified = false
                tempTorrent.magnetUri = TorrentHelper.buildMagnetUriFromHash(jsonTorrent["_id"].asJsonObject.get("value").asString.lowercase(Locale.getDefault()), tempTorrent.name)
                tempTorrent.seeder = Double.parseDouble(jsonTorrent["total_count"].asJsonObject.get("value").asString).toInt()
                tempTorrent.leecher = 0
                tempTorrent.size = Double.parseDouble(jsonTorrent["filesize"].asJsonObject.get("value").asString).toLong().toString()
                tempTorrent.sizeInMB = (tempTorrent.size.toLong() / 1024.0f / 1024.0f).toDouble()
                tempTorrent.date = Date(Double.parseDouble(jsonTorrent["firstadd_utc_timestamp"].asJsonObject.get("value").asString).toLong() * 1000)
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