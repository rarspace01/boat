package boat.torrent

import boat.utilities.HttpHelper
import boat.utilities.LoggerDelegate
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

class LimeTorrents internal constructor(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {

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
        val encodedSearch = URLEncoder.encode(searchName, StandardCharsets.UTF_8)
        return String.format("%s/search/all/%s/seeds/1/", baseUrl, encodedSearch)
    }

    override fun getBaseUrl(): String {
        return "https://www.limetorrents.pro/"
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
            val torrentHash = torrentElement.getElementsByClass("tt-name").first()
                ?.getElementsByAttributeValueContaining("href", "itorrents")?.first()?.attr("href")
                ?.replace("http://itorrents.org/torrent/".toRegex(), "")?.replace("\\.torrent.*".toRegex(), "")
            tempTorrent.magnetUri = TorrentHelper.buildMagnetUriFromHash(torrentHash, tempTorrent.name)
            try {
                tempTorrent.seeder = torrentElement.getElementsByClass("tdseed").first()!!.text().replace("[,.]".toRegex(), "").toInt()
                tempTorrent.leecher = torrentElement.getElementsByClass("tdleech").first()!!.text().replace("[,.]".toRegex(), "").toInt()
                tempTorrent.sizeInMB = TorrentHelper
                    .extractTorrentSizeFromString(torrentElement.getElementsByClass("tdnormal")[1].text())
                tempTorrent.size = TorrentHelper
                    .humanReadableByteCountBinary((tempTorrent.sizeInMB * 1024.0 * 1024.0).toLong())
            } catch (exception: Exception) {
                logger.error("parsing exception", exception)
            }
            TorrentHelper.evaluateRating(tempTorrent, searchName)
            if (TorrentHelper.isValidTorrent(tempTorrent)) {
                torrentList.add(tempTorrent)
            }
        })
        return torrentList
    }

    override fun toString(): String {
        return this.javaClass.simpleName
    }
}