package boat.torrent

import boat.utilities.HttpHelper
import boat.utilities.LoggerDelegate
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

class Torrentz internal constructor(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {

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
        return String.format("%s/data.php?q=%s", baseUrl, encodedSearch)
    }

    override fun getBaseUrl(): String {
        return "https://torrentzeu.org/"
    }

    private fun parseTorrentsOnResultPage(pageContent: String, searchName: String): List<Torrent> {
        val torrentList = ArrayList<Torrent>()
        val doc = Jsoup.parse(pageContent)
        val torrentListOnPage = doc.select("tr:has(td)") ?: return torrentList
        torrentListOnPage.forEach(Consumer { torrentElement: Element ->
            val tempTorrent = Torrent(toString())
            tempTorrent.name = torrentElement.getElementsByAttributeValue("data-title", "Name").first()!!.text()
            tempTorrent.magnetUri = torrentElement.getElementsByTag("a").first()!!.attr("href")
            try {
                tempTorrent.seeder = torrentElement.getElementsByAttributeValue("data-title", "Last Updated").stream()
                    .filter { element: Element -> !element.hasAttr("class") }.findFirst().get().text().toInt()
                tempTorrent.leecher = torrentElement.getElementsByAttributeValue("data-title", "Leeches").first()!!.text().toInt()
                tempTorrent.sizeInMB = TorrentHelper.extractTorrentSizeFromString(
                    torrentElement.getElementsByAttributeValue("data-title", "Size").first()!!.text()
                        .replace(".*Size\\s|,.*".toRegex(), "")
                )
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