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
import java.util.function.Consumer

class LimeTorrents internal constructor(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {

    companion object {
        private val hashInTorrentLinkPattern = Regex("/torrent/([A-Fa-f0-9]{40})")
        private val logger by LoggerDelegate()
    }

    override fun searchTorrents(searchName: String): List<Torrent> {
        return SearchPaging.collect { page -> parseTorrentsOnResultPage(httpHelper.getPage(buildSearchUrl(searchName, page)), searchName) }
    }

    private fun buildSearchUrl(searchName: String, page: Int): String {
        val encodedSearch = URLEncoder.encode(searchName, StandardCharsets.UTF_8)
        return String.format("%s/search/all/%s/seeds/%d/", baseUrl, encodedSearch, page)
    }

    override fun getBaseUrl(): String {
        return "https://limetorrents.fun"
    }

    private fun parseTorrentsOnResultPage(pageContent: String, searchName: String): List<Torrent> {
        val torrentList = ArrayList<Torrent>()
        val doc = Jsoup.parse(pageContent)
        val torrentListOnPage = doc.select(".table2 tr:has(td)")
        if (torrentListOnPage.isEmpty()) {
            return torrentList
        }
        torrentListOnPage.forEach(Consumer { torrentElement: Element ->
            val tempTorrent = Torrent(toString())
            tempTorrent.name = torrentElement.getElementsByClass("tt-name").first()?.text() ?: ""
            // the link to the torrent file holds the hash: http://itorrents.net/torrent/<hash>.torrent?title=...
            val torrentHash = torrentElement.getElementsByClass("tt-name").first()
                ?.getElementsByAttributeValueContaining("href", "itorrents")?.first()?.attr("href")
                ?.let { hashInTorrentLinkPattern.find(it)?.groupValues?.get(1) }
            tempTorrent.magnetUri = if (torrentHash != null) TorrentHelper.buildMagnetUriFromHash(torrentHash.lowercase(), tempTorrent.name) else ""
            try {
                tempTorrent.seeder = torrentElement.getElementsByClass("tdseed").first()!!.text().replace("[,.]".toRegex(), "").toInt()
                tempTorrent.leecher = torrentElement.getElementsByClass("tdleech").first()!!.text().replace("[,.]".toRegex(), "").toInt()
                tempTorrent.sizeInMB = TorrentHelper.extractTorrentSizeFromString(torrentElement.getElementsByClass("tdnormal")[1].text())
                tempTorrent.size = TorrentHelper.humanReadableByteCountBinary((tempTorrent.sizeInMB * 1024.0 * 1024.0).toLong())
            } catch (exception: Exception) {
                logger.error("parsing exception", exception)
            }
            torrentList.add(tempTorrent)
        })
        return torrentList
    }

    override fun toString(): String {
        return "LiT"
    }
}