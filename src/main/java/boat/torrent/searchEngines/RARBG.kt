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
import java.util.Objects
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern

class RARBG(httpHelper: HttpHelper) : HttpUser(httpHelper), TorrentSearchEngine {

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

    override fun getBaseUrl(): String {
        return "http://rarbg.to"
    }

    private fun buildSearchUrl(searchName: String): String {
        return String.format(
            "%s/torrents.php?search=%s&order=seeders&by=DESC", baseUrl,
            URLEncoder.encode(searchName, StandardCharsets.UTF_8)
        )
    }

    private fun parseTorrentsOnResultPage(pageContent: String, searchName: String): List<Torrent> {
        val doc = Jsoup.parse(pageContent)
        val torrentsOnPage = doc.select(".lista2")
        return torrentsOnPage
            .map { obj: Element -> obj.toString() }
            .mapNotNull { elementString: String -> extractSubUrl(elementString) }
            .mapNotNull { url: String -> parseTorrentsOnSubPage(httpHelper.getPage(url), searchName) }

    }

    private fun parseTorrentsOnSubPage(page: String, searchName: String): Torrent? {
        val doc = Jsoup.parse(page)
        val torrent = Torrent(toString())
        torrent.name = doc.title().replace(" Torrent download".toRegex(), "")
        torrent.magnetUri = doc.select("a[href*=magnet]")
            .stream()
            .map { element: Element -> element.attributes()["href"] }
            .filter { obj: String? -> Objects.nonNull(obj) }
            .findFirst().orElse(null)
        val text = doc.text()
        torrent.size = TorrentHelper.cleanNumberString(getValueBetweenStrings(text, "Size: ", "Show Files").trim { it <= ' ' })
        torrent.sizeInMB = TorrentHelper.extractTorrentSizeFromString(torrent)
        try {
            torrent.seeder = getValueBetweenStrings(text, "Seeders : ", " ,").trim { it <= ' ' }.toInt()
            torrent.leecher = getValueBetweenStrings(text, "Leechers : ", " ,").trim { it <= ' ' }.toInt()
        } catch (exception: Exception) {
            logger.error("parsing exception", exception)
        }
        TorrentHelper.evaluateRating(torrent, searchName)
        return if (TorrentHelper.isValidTorrent(torrent)) {
            torrent
        } else {
            null
        }
    }

    private fun extractSubUrl(elementString: String): String? {
        val subUrlPattern = Pattern.compile("href=\"(\\/torrent\\/[A-Za-z0-9]+)\"")
        val matcher = subUrlPattern.matcher(elementString)
        while (matcher.find()) {
            return baseUrl + matcher.group(1)
        }
        return null
    }

    private fun getValueBetweenStrings(input: String, firstString: String, secondString: String): String {
        val betweenPattern = Pattern.compile("$firstString(.*)$secondString")
        val matcher = betweenPattern.matcher(input)
        while (matcher.find()) {
            return matcher.group(1)
        }
        return ""
    }

    override fun toString(): String {
        return this.javaClass.simpleName
    }
}