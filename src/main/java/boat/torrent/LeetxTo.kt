package boat.torrent

import boat.utilities.HttpHelper
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.springframework.util.CollectionUtils
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

class LeetxTo internal constructor(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {
    override fun searchTorrents(searchName: String): List<Torrent> {
        val torrentList = CopyOnWriteArrayList<Torrent>()
        val resultString = httpHelper.getPage(buildSearchUrl(searchName))
        torrentList.addAll(parseTorrentsOnResultPage(resultString, searchName))
        torrentList.sortWith(TorrentComparator)
        return torrentList
    }

    private fun buildSearchUrl(searchName: String): String {
        return String.format(
            "%s/sort-search/%s/seeders/desc/1/", baseUrl,
            URLEncoder.encode(searchName, StandardCharsets.UTF_8)
        )
    }

    override fun getBaseUrl(): String {
        return "https://1337x.to"
    }

    private fun parseTorrentsOnResultPage(pageContent: String, searchName: String): List<Torrent> {
        val torrentList = ArrayList<Torrent>()
        val doc = Jsoup.parse(pageContent)
        val torrentListOnPage = doc.select(".table-list > tbody > tr")
        if (torrentListOnPage.isNotEmpty()) {
            for (torrent in torrentListOnPage) {
                val tempTorrent = Torrent(toString())
                if (torrent.childNodeSize() > 0) {
                    torrent.children().forEach(Consumer { element: Element ->
                        if (element.attr("class").contains("name")) {
                            //extract name
                            tempTorrent.name = element.getElementsByAttributeValueContaining("class", "name")[0]
                                .getElementsByAttributeValueContaining("href", "torrent")[0].html()
                            tempTorrent.isVerified = tempTorrent.name.contains("⭐")
                            //save remote url for later
                            tempTorrent.remoteUrl = baseUrl + element.getElementsByAttributeValueContaining("class", "name")[0]
                                .getElementsByAttributeValueContaining("href", "torrent")[0].attr("href").trim { it <= ' ' }
                        }
                        if (element.attr("class").contains("size")) {
                            tempTorrent.size = TorrentHelper.cleanNumberString(
                                element.getElementsByAttributeValueContaining("class", "size")[0].textNodes()[0].text().trim { it <= ' ' })
                            tempTorrent.sizeInMB = TorrentHelper.extractTorrentSizeFromString(tempTorrent)
                        }
                        if (element.attr("class").contains("seeds")) {
                            tempTorrent.seeder = TorrentHelper.cleanNumberString(
                                element.getElementsByAttributeValueContaining("class", "seeds")[0].textNodes()[0].text().trim { it <= ' ' }).toInt()
                        }
                        if (element.attr("class").contains("leeches")) {
                            tempTorrent.leecher = TorrentHelper.cleanNumberString(
                                element.getElementsByAttributeValueContaining("class", "leeches")[0].textNodes()[0].text().trim { it <= ' ' }).toInt()
                        }
                    })
                }

                // evaluate result
                TorrentHelper.evaluateRating(tempTorrent, searchName)
                torrentList.add(tempTorrent)
            }
        }
        //retrieve magneturis for torrents
        torrentList.parallelStream().forEach { torrent: Torrent ->
            torrent.magnetUri = retrieveMagnetUri(torrent)
            torrent.remoteUrl = ""
        }

        // remove torrents without magneturi
        return torrentList.filter { torrent: Torrent -> TorrentHelper.isValidTorrent(torrent) }
    }

    private fun retrieveMagnetUri(torrent: Torrent): String {
        val pageContent = httpHelper.getPage(torrent.remoteUrl)
        val doc = Jsoup.parse(pageContent)
        return if (!CollectionUtils.isEmpty(doc.select("* > li > a[href*=magnet]"))) {
            doc.select("* > li > a[href*=magnet]")[0].attr("href").trim { it <= ' ' }
        } else {
            ""
        }
    }

    override fun toString(): String {
        return this.javaClass.simpleName
    }
}