package boat.torrent

import boat.utilities.HttpHelper
import boat.utilities.LoggerDelegate
import com.fasterxml.jackson.databind.ObjectMapper
import lombok.extern.slf4j.Slf4j
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

@Slf4j
class SolidTorrents internal constructor(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {

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
        return baseUrl + "search?q=${URLEncoder.encode(searchName, StandardCharsets.UTF_8)}&sort=seeders"
    }

    override fun getBaseUrl(): String {
        return "https://solidtorrents.to/"
    }

    private fun parseTorrentsOnResultPage(pageContent: String, searchName: String): List<Torrent> {
        val torrentList = ArrayList<Torrent>()

        // create ObjectMapper instance
        val objectMapper = ObjectMapper()

        // read JSON like DOM Parser
        try {
            val document = Jsoup.parse(pageContent)
            val torrentElements = document.getElementsByClass("card search-result my-2")
            return torrentElements.mapNotNull {
                val tempTorrent = Torrent(toString())
                val magnetUri = it.getElementsByClass("dl-magnet")[0].attr("href")
                val title = it.getElementsByClass("title w-100 truncate")[0]
                val stats = it.getElementsByClass("stats")
                // extract Size & S/L
                tempTorrent.name = title.text().replace("✅", "").trim()
                tempTorrent.sizeInMB = TorrentHelper.extractTorrentSizeFromString(stats[0].allElements[3].text())
                tempTorrent.size = TorrentHelper.humanReadableByteCountBinary((tempTorrent.sizeInMB * 1024 * 1024).toLong())
                tempTorrent.seeder = stats[0].allElements[7].text().trim().replace(Regex(".*(\\..*)?[kK]"),"999").toInt()
                tempTorrent.leecher = stats[0].allElements[10].text().trim().replace(Regex(".*(\\..*)?[kK]"),"999").toInt()
                tempTorrent.magnetUri = magnetUri
                tempTorrent.isVerified = title.text().matches(Regex(".*✅.*"))
                // evaluate result
                TorrentHelper.evaluateRating(tempTorrent, searchName)
                if (TorrentHelper.isValidTorrent(tempTorrent)) {
                    tempTorrent
                } else {
                    null
                }
            }
        } catch (exception: Exception) {
            logger.error("parsing exception", exception)
        }
        return torrentList
    }

    override fun toString(): String {
        return this.javaClass.simpleName
    }
}
