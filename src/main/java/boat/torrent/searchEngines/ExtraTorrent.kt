package boat.torrent.searchEngines

import boat.torrent.HttpUser
import boat.torrent.Torrent
import boat.torrent.TorrentComparator
import boat.torrent.TorrentHelper
import boat.torrent.TorrentSearchEngine
import boat.utilities.HttpHelper
import boat.utilities.LoggerDelegate
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

class ExtraTorrent internal constructor(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {

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
        val cleanedSearchName = searchName.trim { it <= ' ' }.replace("\\s+".toRegex(), ".")
        return String.format(
            "%s/search/?srt=seeds&order=desc&search=%s&new=1&x=0&y=0", baseUrl,
            URLEncoder.encode(cleanedSearchName, StandardCharsets.UTF_8)
        )
    }

    override fun getBaseUrl(): String {
        return "https://extratorrent.st"
    }

    private fun parseTorrentsOnResultPage(pageContent: String, searchName: String): List<Torrent> {
        val torrentList = ArrayList<Torrent>()
        try {
            val doc = Jsoup.parse(pageContent)
            val torrentListOnPage = doc.select("tr[class^=\"tl\"]:has(a[href^=\"magnet:?xt=\"])")
            torrentListOnPage.forEach(Consumer { torrentElement: Element ->
                val tempTorrent = Torrent(toString())
                tempTorrent.name = torrentElement.selectFirst("a[href^=\"/torrent/\"]:not([href$=\"comments\"])")?.text() ?: ""
                tempTorrent.magnetUri = torrentElement.selectFirst("a[href^=\"magnet:?xt=\"]")?.attr("href")?.trim { it <= ' ' } ?: ""
                tempTorrent.size = torrentElement.selectFirst("td:nth-last-of-type(4)")?.text()?.replace('\u00a0', ' ') ?: ""
                tempTorrent.sizeInMB = TorrentHelper.extractTorrentSizeFromString(tempTorrent)
                tempTorrent.seeder = TorrentHelper.cleanNumberString(
                    torrentElement.selectFirst("td.sy, td.sn")?.text() ?: ""
                ).toIntOrNull() ?: 0
                tempTorrent.leecher = TorrentHelper.cleanNumberString(
                    torrentElement.selectFirst("td.ly, td.ln")?.text() ?: ""
                ).toIntOrNull() ?: 0
                TorrentHelper.evaluateRating(tempTorrent, searchName)
                if (TorrentHelper.isValidTorrent(tempTorrent)) {
                    torrentList.add(tempTorrent)
                }
            })
        } catch (exception: Exception) {
            logger.error("[{}] couldn't parse search results", this, exception)
        }
        return torrentList
    }

    override fun toString(): String {
        return "ET"
    }
}
