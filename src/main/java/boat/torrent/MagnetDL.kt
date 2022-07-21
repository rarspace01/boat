package boat.torrent

import boat.utilities.HttpHelper
import boat.utilities.LoggerDelegate
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

class MagnetDL internal constructor(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {

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
        return String.format("%s/%s/%s/se/desc/", baseUrl, encodedSearch.lowercase(Locale.getDefault())[0], encodedSearch)
    }

    override fun getBaseUrl(): String {
        return "https://www.magnetdl.com"
    }

    private fun parseTorrentsOnResultPage(pageContent: String, searchName: String): List<Torrent> {
        val torrentList = ArrayList<Torrent>()
        val doc = Jsoup.parse(pageContent)
        val torrentListOnPage = doc.select("tr:has(.m)") ?: return torrentList
        torrentListOnPage.forEach(Consumer { torrentElement: Element ->
            val tempTorrent = Torrent(toString())
            tempTorrent.name = torrentElement.getElementsByClass("n").first()!!.getElementsByAttribute("title")
                .attr("title")
            tempTorrent.magnetUri = torrentElement.getElementsByClass("m").first()!!.getElementsByAttribute("href")
                .first()!!.attr("href")
            tempTorrent.seeder = torrentElement.getElementsByClass("s").first()!!.text().toInt()
            tempTorrent.leecher = torrentElement.getElementsByClass("l").first()!!.text().toInt()
            tempTorrent.sizeInMB = TorrentHelper.extractTorrentSizeFromString(torrentElement.child(5).text())
            tempTorrent.size = TorrentHelper.humanReadableByteCountBinary((tempTorrent.sizeInMB * 1024.0 * 1024.0).toLong())
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