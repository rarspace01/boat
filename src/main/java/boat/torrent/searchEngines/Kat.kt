package boat.torrent.searchEngines

import boat.torrent.HttpUser
import boat.torrent.Torrent
import boat.torrent.TorrentComparator
import boat.torrent.TorrentHelper
import boat.torrent.TorrentSearchEngine
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
            "%s/search?query=%s&sort_by=seeders&sort_direction=desc", baseUrl,
            URLEncoder.encode(searchName, StandardCharsets.UTF_8)
        )
    }

    override fun getBaseUrl(): String {
        return "https://kickasstorrents.cc"
    }

    private fun parseTorrentsOnResultPage(pageContent: String, searchName: String): List<Torrent> {
        val torrentList = ArrayList<Torrent>()
        val doc = Jsoup.parse(pageContent)
        val torrentListOnPage = doc.select("table > tbody > tr .even")
        torrentListOnPage.parallelStream().forEach { torrentElement: Element ->
            val tempTorrent = Torrent(toString())
            if (torrentElement.childNodeSize() > 0) {
                torrentElement.children().forEach(Consumer { element: Element ->
                    if (element.getElementsByClass("filmType").size > 0) {
                        //extract name
                        tempTorrent.name = element.getElementsByClass("filmType")[0]
                            .text()
                    }
                    if (element.getElementsByClass("cellMainLink").size > 0) {
                        //extract magneturi
                        val urlForMagnet = element.getElementsByClass("cellMainLink")
                            .attr("href").trim { it <= ' ' }

                        tempTorrent.magnetUri = parseMagnetFromUrl(urlForMagnet)
                    }
                    if (element.getElementsByClass("nobr").size > 0) {
                        tempTorrent.size = TorrentHelper.cleanNumberString(
                            element.getElementsByClass("nobr").text().trim { it <= ' ' })
                        tempTorrent.sizeInMB = TorrentHelper.extractTorrentSizeFromString(tempTorrent)
                    }
                    if (element.getElementsByClass("green").size > 0) {
                        tempTorrent.seeder = TorrentHelper.cleanNumberString(
                            element.getElementsByClass("green").text().trim { it <= ' ' }).toInt()
                    }
                    if (element.getElementsByClass("red").size > 0) {
                        tempTorrent.seeder = TorrentHelper.cleanNumberString(
                            element.getElementsByClass("red").text().trim { it <= ' ' }).toInt()
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

    private fun parseMagnetFromUrl(urlForMagnet: String): String {
        httpHelper.getPage(urlForMagnet).let {
            return Jsoup.parse(it).getElementsByAttributeValueMatching("href", "magnet:").attr("href")
        }
    }

    override fun toString(): String {
        return this.javaClass.simpleName
    }
}