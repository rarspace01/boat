package boat.torrent.searchEngines

import boat.torrent.HttpUser
import boat.torrent.Torrent
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

class EZTV internal constructor(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {

    companion object {
        private val logger by LoggerDelegate()
        private const val COOKIES =
            "sort_no=100; q_filter=all; q_filter_web=on; q_filter_reality=on; q_filter_x265=on; layout=def_wlinks"
    }

    override fun searchTorrents(searchName: String): List<Torrent> {
        val torrentList = CopyOnWriteArrayList<Torrent>()
        val resultString = httpHelper.getPageWithCookies(buildSearchUrl(searchName), COOKIES)
        torrentList.addAll(parseTorrentsOnResultPage(resultString, searchName))
        return torrentList
    }

    private fun buildSearchUrl(searchName: String): String {
        // the site search doesn't support season tags without episode and expects dashes instead of spaces
        val cleanedSearchName = searchName
            .replace("\\bS\\d{2,3}\\b".toRegex(RegexOption.IGNORE_CASE), "")
            .trim { it <= ' ' }
            .replace("-", "")
            .replace("&", "")
            .replace("\\s+".toRegex(), "-")
        return String.format("%s/search/%s", baseUrl, URLEncoder.encode(cleanedSearchName, StandardCharsets.UTF_8))
    }

    override fun getBaseUrl(): String {
        return "https://eztvx.to"
    }

    private fun parseTorrentsOnResultPage(pageContent: String, searchName: String): List<Torrent> {
        val torrentList = ArrayList<Torrent>()
        try {
            val doc = Jsoup.parse(pageContent)
            val torrentListOnPage = doc.select("table.forum_header_border tr[name=\"hover\"].forum_header_border:has(a.magnet)")
            torrentListOnPage.forEach(Consumer { torrentElement: Element ->
                val tempTorrent = Torrent(toString())
                tempTorrent.name = (torrentElement.selectFirst("td:nth-child(2) a")?.attr("title") ?: "")
                    .replace("[eztv]", "")
                    .replace("\\(.*\\)$".toRegex(), "")
                    .trim { it <= ' ' }
                tempTorrent.magnetUri = torrentElement.selectFirst("a.magnet")?.attr("href")?.trim { it <= ' ' } ?: ""
                tempTorrent.size = torrentElement.selectFirst("td:nth-child(4)")?.text()?.replace('\u00a0', ' ') ?: ""
                tempTorrent.sizeInMB = TorrentHelper.extractTorrentSizeFromString(tempTorrent)
                tempTorrent.seeder = TorrentHelper.cleanNumberString(
                    torrentElement.selectFirst("td:nth-child(6)")?.text() ?: ""
                ).toIntOrNull() ?: 0
                tempTorrent.leecher = 0
                torrentList.add(tempTorrent)
            })
        } catch (exception: Exception) {
            logger.error("[{}] couldn't parse search results", this, exception)
        }
        return torrentList
    }

    override fun toString(): String {
        return "E"
    }
}
