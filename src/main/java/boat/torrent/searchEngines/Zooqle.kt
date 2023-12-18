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

class Zooqle internal constructor(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {

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
        return String.format("%s/search?q=%s&fmt=rss", baseUrl, URLEncoder.encode(searchName, StandardCharsets.UTF_8))
    }

    override fun getBaseUrl(): String {
        return "https://zooqle.com"
    }

    private fun parseTorrentsOnResultPage(pageContent: String, searchName: String): List<Torrent> {
        val torrentList = ArrayList<Torrent>()
        try {
            val doc = Jsoup.parse(pageContent)
            val torrentListOnPage = doc.select("item") ?: return torrentList
            torrentListOnPage.forEach(Consumer { torrentElement: Element ->
                val tempTorrent = Torrent(toString())
                tempTorrent.name = torrentElement.getElementsByTag("title").text()
                tempTorrent.magnetUri = TorrentHelper.buildMagnetUriFromHash(
                        torrentElement.getElementsByTag("torrent").first()!!.getElementsByTag("infohash").text(),
                        tempTorrent.name
                )
                tempTorrent.seeder = torrentElement.getElementsByTag("torrent:seeds").text().toInt()
                tempTorrent.leecher = torrentElement.getElementsByTag("torrent:peers").text().toInt()
                tempTorrent.sizeInMB = (torrentElement.getElementsByTag("torrent:contentlength").text().toLong() / 1024.0f / 1024.0f).toDouble()
                tempTorrent.size = TorrentHelper.humanReadableByteCountBinary((tempTorrent.sizeInMB * 1024.0 * 1024.0).toLong())
                tempTorrent.isVerified = "1" == torrentElement.getElementsByTag("torrent:verified").text()
                TorrentHelper.evaluateRating(tempTorrent, searchName)
                if (TorrentHelper.isValidTorrent(tempTorrent)) {
                    torrentList.add(tempTorrent)
                }
            })
        } catch (exception:Exception) {
            logger.error("Zooqle",exception)
        }

        return torrentList
    }

    override fun toString(): String {
        return this.javaClass.simpleName
    }
}