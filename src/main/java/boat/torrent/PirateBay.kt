package boat.torrent

import boat.utilities.HttpHelper
import boat.utilities.LoggerDelegate
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import lombok.extern.slf4j.Slf4j
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

class PirateBay internal constructor(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {

    companion object {
        private val logger by LoggerDelegate()
    }

    override fun searchTorrents(searchName: String): List<Torrent> {
        val resultString = httpHelper.getPageWithShortTimeout(buildSearchUrl(searchName), null, "lw=s")
        val torrentList = CopyOnWriteArrayList(
            parseTorrentsOnResultPage(resultString, searchName)
        )

        // sort the findings
        torrentList.sortWith(TorrentComparator)
        return torrentList
    }

    private fun buildSearchUrl(searchName: String): String {
        return String.format("%s/q.php?q=%s&cat=", baseUrl, URLEncoder.encode(searchName, StandardCharsets.UTF_8))
    }

    override fun getBaseUrl(): String {
        return "https://apibay.org"
    }

    private fun parseTorrentsOnResultPage(pageContent: String, searchName: String): List<Torrent> {
        val torrentList = ArrayList<Torrent>()
        try {
            val jsonRoot = JsonParser.parseString(pageContent)
            if (jsonRoot.isJsonArray) {
                val listOfTorrents = jsonRoot.asJsonArray
                listOfTorrents.forEach(Consumer { jsonElement: JsonElement ->
                    val tempTorrent = Torrent(toString())
                    val jsonObject = jsonElement.asJsonObject
                    tempTorrent.name = jsonObject["name"].asString
                    tempTorrent.magnetUri = TorrentHelper
                        .buildMagnetUriFromHash(jsonObject["info_hash"].asString, tempTorrent.name)
                    tempTorrent.seeder = jsonObject["seeders"].asInt
                    tempTorrent.leecher = jsonObject["leechers"].asInt
                    tempTorrent.isVerified = "vip" == jsonObject["status"].asString
                    val size = jsonObject["size"].asLong
                    tempTorrent.lsize = (size / 1024.0f / 1024.0f).toDouble()
                    tempTorrent.size = String.format("%s", TorrentHelper.humanReadableByteCountBinary(size))
                    TorrentHelper.evaluateRating(tempTorrent, searchName)
                    if (TorrentHelper.isValidTorrent(tempTorrent)) {
                        torrentList.add(tempTorrent)
                    }
                })
            }
        } catch (e: JsonSyntaxException) {
            logger.error("[{}] couldn't extract torrent: {} ", this, e.stackTrace)
        } catch (e: IllegalStateException) {
            logger.error("[{}] couldn't extract torrent: {} ", this, e.stackTrace)
        }
        return torrentList
    }

    override fun toString(): String {
        return this.javaClass.simpleName
    }
}