package boat.torrent

import boat.utilities.HttpHelper
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

class Kat internal constructor(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {
    override fun searchTorrents(searchName: String): List<Torrent> {
        val torrentList = CopyOnWriteArrayList<Torrent>()
        val resultString = httpHelper.getPage(buildSearchUrl(searchName))
        torrentList.addAll(parseTorrentsOnResultPage(resultString, searchName))
        torrentList.sortWith(TorrentComparator)
        return torrentList
    }

    private fun buildSearchUrl(searchName: String): String {
        return String.format(
            "%s/usearch/%s/1/?field=seeders&sorder=desc", baseUrl,
            URLEncoder.encode(searchName, StandardCharsets.UTF_8)
        )
    }

    override fun getBaseUrl(): String {
        return "https://kat.rip"
    }

    private fun parseTorrentsOnResultPage(pageContent: String, searchName: String): List<Torrent> {
        val torrentList = ArrayList<Torrent>()
        val doc = Jsoup.parse(pageContent)
        val torrentListOnPage = doc.select(".table > tbody > tr")
        for (torrent in torrentListOnPage) {
            val tempTorrent = Torrent(toString())
            if (torrent.childNodeSize() > 0) {
                torrent.children().forEach(Consumer { element: Element ->
                    if (element.getElementsByClass("torrents_table__torrent_title").size > 0) {
                        //extract name
                        tempTorrent.name = element.getElementsByClass("torrents_table__torrent_title")[0]
                            .text()
                    }
                    if (element.getElementsByAttributeValueMatching("href", "magnet:").size > 0) {
                        //extract magneturi
                        tempTorrent.magnetUri = element.getElementsByAttributeValueMatching("href", "magnet:")
                            .attr("href").trim { it <= ' ' }
                    }
                    if (element.getElementsByAttributeValueMatching("data-title", "Size").size > 0) {
                        tempTorrent.size = TorrentHelper.cleanNumberString(
                            element.getElementsByAttributeValueMatching("data-title", "Size").text().trim { it <= ' ' })
                        tempTorrent.lsize = TorrentHelper.extractTorrentSizeFromString(tempTorrent)
                    }
                    if (element.getElementsByAttributeValueMatching("data-title", "Seed").size > 0) {
                        tempTorrent.seeder = TorrentHelper.cleanNumberString(
                            element.getElementsByAttributeValueMatching("data-title", "Seed").text().trim { it <= ' ' }).toInt()
                    }
                    if (element.getElementsByAttributeValueMatching("data-title", "Leech").size > 0) {
                        tempTorrent.leecher = TorrentHelper.cleanNumberString(
                            element.getElementsByAttributeValueMatching("data-title", "Leech").text().trim { it <= ' ' }).toInt()
                    }
                    if (element.getElementsByClass("ka ka16 ka-verify ka-green").size > 0) {
                        tempTorrent.isVerified = true
                    }
                })
            }

            // evaluate result
            TorrentHelper.evaluateRating(tempTorrent, searchName)
            if (TorrentHelper.isValidTorrent(tempTorrent)) {
                torrentList.add(tempTorrent)
            }
        }
        return torrentList
    }

    override fun toString(): String {
        return this.javaClass.simpleName
    }
}