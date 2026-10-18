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
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

class TorrentDownload internal constructor(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {

    companion object {
        private val logger by LoggerDelegate()
        private val hashInUrlRegex = "/([A-Fa-f0-9]{40})(/|$)".toRegex()
    }

    override fun searchTorrents(searchName: String): List<Torrent> {
        val torrentList = CopyOnWriteArrayList<Torrent>()
        val resultString = httpHelper.getPage(buildSearchUrl(searchName))
        torrentList.addAll(parseTorrentsOnResultPage(resultString, searchName))
        torrentList.sortWith(TorrentComparator)
        return torrentList
    }

    private fun buildSearchUrl(searchName: String): String {
        // "search" is sorted by seeders, "searchd" by date
        return String.format("%s/search?q=%s", baseUrl, URLEncoder.encode(searchName, StandardCharsets.UTF_8))
    }

    override fun getBaseUrl(): String {
        return "https://www.torrentdownload.info"
    }

    private fun parseTorrentsOnResultPage(pageContent: String, searchName: String): List<Torrent> {
        val torrentList = ArrayList<Torrent>()
        try {
            val doc = Jsoup.parse(pageContent)
            val torrentListOnPage = doc.select("table.table2 > tbody > tr:has(span.smallish)")
            torrentListOnPage.forEach(Consumer { torrentElement: Element ->
                val tempTorrent = Torrent(toString())
                val linkElement = torrentElement.selectFirst("div.tt-name > a[href^=\"/\"]")
                tempTorrent.name = linkElement?.text() ?: ""
                tempTorrent.remoteUrl = linkElement?.attr("href")?.trim { it <= ' ' } ?: ""
                tempTorrent.size = torrentElement.selectFirst("td:nth-child(3)")?.text()?.replace('\u00a0', ' ') ?: ""
                tempTorrent.sizeInMB = TorrentHelper.extractTorrentSizeFromString(tempTorrent)
                tempTorrent.seeder = TorrentHelper.cleanNumberString(
                    torrentElement.selectFirst("td.tdseed")?.text() ?: ""
                ).toIntOrNull() ?: 0
                tempTorrent.leecher = TorrentHelper.cleanNumberString(
                    torrentElement.selectFirst("td.tdleech")?.text() ?: ""
                ).toIntOrNull() ?: 0
                TorrentHelper.evaluateRating(tempTorrent, searchName)
                torrentList.add(tempTorrent)
            })
        } catch (exception: Exception) {
            logger.error("[{}] couldn't parse search results", this, exception)
        }
        // the details url contains the info hash, only fall back to the details page if it doesn't
        torrentList.parallelStream().forEach { torrent: Torrent ->
            torrent.magnetUri = retrieveMagnetUri(torrent)
            torrent.remoteUrl = ""
        }
        return torrentList.filter { torrent: Torrent -> TorrentHelper.isValidTorrent(torrent) }
    }

    private fun retrieveMagnetUri(torrent: Torrent): String {
        if (torrent.remoteUrl.isBlank()) {
            return ""
        }
        hashInUrlRegex.find(torrent.remoteUrl)?.let {
            return TorrentHelper.buildMagnetUriFromHash(it.groupValues[1].lowercase(Locale.getDefault()), torrent.name)
        }
        if (torrent.sizeInMB <= 0) {
            return ""
        }
        val pageContent = httpHelper.getPage(baseUrl + torrent.remoteUrl)
        return Jsoup.parse(pageContent).selectFirst("a[href^=\"magnet:?xt=\"]")?.attr("href")?.trim { it <= ' ' } ?: ""
    }

    override fun toString(): String {
        return "TD"
    }
}
