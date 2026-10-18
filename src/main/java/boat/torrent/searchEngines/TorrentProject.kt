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

class TorrentProject internal constructor(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {

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
            "%s/?t=%s&orderby=seeders", baseUrl,
            URLEncoder.encode(searchName, StandardCharsets.UTF_8)
        )
    }

    override fun getBaseUrl(): String {
        return "https://torrentproject.cc"
    }

    private fun parseTorrentsOnResultPage(pageContent: String, searchName: String): List<Torrent> {
        val torrentList = ArrayList<Torrent>()
        try {
            val doc = Jsoup.parse(pageContent)
            val torrentListOnPage = doc.select("#similarfiles div:has(> span > a[href^=\"/t\"])")
            torrentListOnPage.forEach(Consumer { torrentElement: Element ->
                val tempTorrent = Torrent(toString())
                // columns: name, seeders, leechers, date, size
                val columns = torrentElement.children().filter { element: Element -> element.tagName() == "span" }
                val linkElement = columns.getOrNull(0)?.selectFirst("a")
                tempTorrent.name = linkElement?.text() ?: ""
                tempTorrent.remoteUrl = linkElement?.attr("href")?.trim { it <= ' ' } ?: ""
                tempTorrent.size = columns.getOrNull(4)?.text()?.replace('\u00a0', ' ') ?: ""
                tempTorrent.sizeInMB = TorrentHelper.extractTorrentSizeFromString(tempTorrent)
                tempTorrent.seeder = TorrentHelper.cleanNumberString(
                    columns.getOrNull(1)?.text() ?: ""
                ).toIntOrNull() ?: 0
                tempTorrent.leecher = TorrentHelper.cleanNumberString(
                    columns.getOrNull(2)?.text() ?: ""
                ).toIntOrNull() ?: 0
                TorrentHelper.evaluateRating(tempTorrent, searchName)
                torrentList.add(tempTorrent)
            })
        } catch (exception: Exception) {
            logger.error("[{}] couldn't parse search results", this, exception)
        }
        //retrieve magneturis for torrents worth looking at
        torrentList.parallelStream()
            .filter { torrent: Torrent -> torrent.seeder > 0 && torrent.sizeInMB > 0 && torrent.remoteUrl.isNotBlank() }
            .forEach { torrent: Torrent ->
                torrent.magnetUri = retrieveMagnetUri(torrent)
                torrent.remoteUrl = ""
            }
        // remove torrents without magneturi
        return torrentList.filter { torrent: Torrent -> TorrentHelper.isValidTorrent(torrent) }
    }

    private fun retrieveMagnetUri(torrent: Torrent): String {
        val pageContent = httpHelper.getPage(baseUrl + torrent.remoteUrl)
        // the magnet link is wrapped in a redirect like https://mylink.cx/?url=magnet%3A...
        return Jsoup.parse(pageContent).select("#download a[href]")
            .map { element: Element -> TorrentHelper.urlDecode(element.attr("href").substringAfter("?url=")).trim { it <= ' ' } }
            .firstOrNull { href: String -> href.startsWith("magnet:") } ?: ""
    }

    override fun toString(): String {
        return "TP"
    }
}
