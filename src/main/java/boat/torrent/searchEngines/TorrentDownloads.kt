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

class TorrentDownloads internal constructor(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {

    companion object {
        private val logger by LoggerDelegate()
    }

    override fun searchTorrents(searchName: String): List<Torrent> {
        val torrentList = CopyOnWriteArrayList<Torrent>()
        val resultString = httpHelper.getPage(buildSearchUrl(searchName))
        torrentList.addAll(parseTorrentsOnResultPage(resultString, searchName))
        return torrentList
    }

    private fun buildSearchUrl(searchName: String): String {
        val cleanedSearchName = searchName.replace("\\W+".toRegex(), " ").trim { it <= ' ' }
        return String.format(
            "%s/search/?search=%s", baseUrl,
            URLEncoder.encode(cleanedSearchName, StandardCharsets.UTF_8)
        )
    }

    override fun getBaseUrl(): String {
        return "https://www.torrentdownloads.pro"
    }

    private fun parseTorrentsOnResultPage(pageContent: String, searchName: String): List<Torrent> {
        val torrentList = ArrayList<Torrent>()
        try {
            val doc = Jsoup.parse(pageContent)
            val torrentListOnPage = doc.select(
                "div.inner_container > div:has(p:has(a[href^=\"/torrent/\"])):not(:has(span.__cf_email__))"
            )
            torrentListOnPage.forEach(Consumer { torrentElement: Element ->
                val tempTorrent = Torrent(toString())
                val linkElement = torrentElement.selectFirst("p:nth-child(1) > a[href^=\"/torrent/\"]")
                tempTorrent.name = (linkElement?.attr("title") ?: "").replace("View torrent info : ", "").trim { it <= ' ' }
                tempTorrent.remoteUrl = linkElement?.attr("href")?.trim { it <= ' ' } ?: ""
                tempTorrent.size = torrentElement.selectFirst("span:nth-child(5)")?.text()?.replace('\u00a0', ' ') ?: ""
                tempTorrent.sizeInMB = TorrentHelper.extractTorrentSizeFromString(tempTorrent)
                tempTorrent.seeder = TorrentHelper.cleanNumberString(
                    torrentElement.selectFirst("span:nth-child(4)")?.text() ?: ""
                ).toIntOrNull() ?: 0
                tempTorrent.leecher = TorrentHelper.cleanNumberString(
                    torrentElement.selectFirst("span:nth-child(3)")?.text() ?: ""
                ).toIntOrNull() ?: 0
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
        return torrentList
    }

    private fun retrieveMagnetUri(torrent: Torrent): String {
        val pageContent = httpHelper.getPage(baseUrl + torrent.remoteUrl)
        return Jsoup.parse(pageContent).selectFirst("a[href^=\"magnet:\"]")?.attr("href")?.trim { it <= ' ' } ?: ""
    }

    override fun toString(): String {
        return "TDs"
    }
}
