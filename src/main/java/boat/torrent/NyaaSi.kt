package boat.torrent

import boat.utilities.HttpHelper
import boat.utilities.LoggerDelegate
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import java.util.stream.Collectors

class NyaaSi internal constructor(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {

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
        return String.format(
            "%s/?f=0&c=0_0&q=%s&s=seeders&o=desc", baseUrl,
            URLEncoder.encode(searchName, StandardCharsets.UTF_8)
        )
    }

    override fun getBaseUrl(): String {
        return "https://nyaa.si"
    }

    private fun parseTorrentsOnResultPage(pageContent: String, searchName: String): List<Torrent> {
        val torrentList = ArrayList<Torrent>()
        val doc = Jsoup.parse(pageContent)
        val torrentListOnPage = doc.select(".torrent-list > tbody > tr")
        if (torrentListOnPage != null) {
            for (torrent in torrentListOnPage) {
                val tempTorrent = Torrent(toString())
                val attributesOnTableRecord = torrent.attributes()
                if (torrent.childNodeSize() > 0) {
                    torrent.children().forEach(Consumer { element: Element ->
                        if (element.getElementsByTag("a").size > 0
                            && getTorrentTitle(element).length > 0
                        ) {
                            //extract name
                            tempTorrent.name = getTorrentTitle(element)
                        }
                        if (attributesOnTableRecord.size() > 0 && "success" == attributesOnTableRecord["class"]) {
                            tempTorrent.isVerified = true
                        }
                        if (elementContainsMagnetUri(element)) {
                            //extract magneturi
                            tempTorrent.magnetUri = getMagnetUri(element)
                        }
                        if (element.text().contains("MiB") || element.text().contains("GiB")) {
                            tempTorrent.size = TorrentHelper.cleanNumberString(element.text().trim { it <= ' ' })
                            tempTorrent.lsize = TorrentHelper.extractTorrentSizeFromString(tempTorrent)
                        }
                    })
                }
                val index = torrent.children().size - 3
                if (index > 0) {
                    tempTorrent.seeder = TorrentHelper.cleanNumberString(torrent.children()[index].text()).toInt()
                    tempTorrent.leecher = TorrentHelper.cleanNumberString(torrent.children()[index + 1].text()).toInt()
                }

                // evaluate result
                TorrentHelper.evaluateRating(tempTorrent, searchName)
                if (TorrentHelper.isValidTorrent(tempTorrent)) {
                    torrentList.add(tempTorrent)
                }
            }
        }
        return torrentList
    }

    private fun getMagnetUri(metaElement: Element): String {
        return metaElement.getElementsByTag("a").stream()
            .filter { element: Element -> element.attributes()["href"].contains("magnet") }
            .map { element: Element -> element.attributes()["href"].trim { it <= ' ' } }.collect(Collectors.joining(""))
    }

    private fun elementContainsMagnetUri(metaElement: Element): Boolean {
        return metaElement.getElementsByTag("a").stream().anyMatch { element: Element -> element.attributes()["href"].contains("magnet") }
    }

    private fun getTorrentTitle(metaElement: Element): String {
        val elementsByTag = metaElement.getElementsByTag("a")
        return elementsByTag.stream()
            .filter { element: Element -> !element.attributes()["href"].contains("magnet") }
            .filter { element: Element -> !element.attributes()["href"].contains("comment") }
            .filter { element: Element -> element.attributes()["href"].contains("/view/") }
            .filter { element: Element -> element.text().trim { it <= ' ' }.length > 0 }
            .map { element: Element -> element.text().trim { it <= ' ' } }.collect(Collectors.joining())
    }

    override fun toString(): String {
        return this.javaClass.simpleName
    }
}