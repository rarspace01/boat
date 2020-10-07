package pirateboat.multifileHoster

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import pirateboat.torrent.HttpUser
import pirateboat.torrent.Torrent
import pirateboat.torrent.TorrentFile
import pirateboat.torrent.TorrentHelper
import pirateboat.utilities.HttpHelper
import pirateboat.utilities.PropertiesHelper
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.stream.Collectors

class Alldebrid(httpHelper: HttpHelper?) : HttpUser(httpHelper), MultifileHoster {
    override fun addTorrentToQueue(toBeAddedTorrent: Torrent): String {
        val requestUrl = "https://api.alldebrid.com/v4/magnet/upload?agent=pirateboat&apikey=" + PropertiesHelper.getProperty("alldebrid_apikey") + "%s"
        val urlEncodedBrackets = TorrentHelper.urlEncode("[]")
        val collected = "&magnets" + urlEncodedBrackets + "=" + TorrentHelper.urlEncode(toBeAddedTorrent.magnetUri)
        val checkUrl = String.format(requestUrl, collected)
        val pageContent = httpHelper.getPage(checkUrl)
        val jsonRoot = JsonParser.parseString(pageContent)
        val status = jsonRoot.asJsonObject["status"]
        return if (status != null) status.asString else "error"
    }

    override fun getRemoteTorrents(): List<Torrent> {
        val torrents: MutableList<Torrent> = ArrayList()
        val requestUrl = "https://api.alldebrid.com/v4/magnet/status?agent=pirateboat&apikey=" + PropertiesHelper.getProperty("alldebrid_apikey")
        val pageContent = httpHelper.getPage(requestUrl)
        val jsonRoot = JsonParser.parseString(pageContent)
        val jsonMagnets = jsonRoot.asJsonObject["data"].asJsonObject["magnets"].asJsonArray
        jsonMagnets.forEach(Consumer { jsonElement: JsonElement ->
            val torrent = Torrent(name)
            val jsonTorrent = jsonElement.asJsonObject
            torrent.remoteId = jsonTorrent["id"].asString
            torrent.name = jsonTorrent["filename"].asString
            torrent.size = (jsonTorrent["size"].asLong / 1024 / 1024).toString() + "MB"
            torrent.lsize = TorrentHelper.extractTorrentSizeFromString(torrent)
            val downloaded = jsonTorrent["downloaded"].asLong.toDouble()
            val size = jsonTorrent["size"].asLong.toDouble()
            val downloadSpeed = jsonTorrent["downloadSpeed"].asLong.toDouble()
            val remainingSeconds = ((size - downloaded) / downloadSpeed).toLong()
            val duration = Duration.ofSeconds(remainingSeconds)
            torrent.progress = String.format("%f", downloaded / size)
            torrent.eta = String.format("ETA: %s", duration.toString())
            torrent.status = jsonTorrent["status"].asString
            torrents.add(torrent)
        })
        return torrents
    }

    private fun getRemoteTorrentById(remoteId: String?): Torrent? {
        val remoteIdString = if (remoteId == null) "" else "&id=$remoteId"
        val requestUrl = "https://api.alldebrid.com/v4/magnet/status?agent=pirateboat&apikey=" + PropertiesHelper.getProperty("alldebrid_apikey") + remoteIdString
        val pageContent = httpHelper.getPage(requestUrl)
        var torrent: Torrent? = null
        val jsonRoot = JsonParser.parseString(pageContent)
        val data = jsonRoot.asJsonObject["data"]
        if (data != null) {
            torrent = Torrent(name)
            val jsonTorrent = data.asJsonObject["magnets"].asJsonObject
            torrent.remoteId = jsonTorrent["id"].asString
            torrent.name = jsonTorrent["filename"].asString
            torrent.size = (jsonTorrent["size"].asLong / 1024 / 1024).toString() + "MB"
            torrent.lsize = TorrentHelper.extractTorrentSizeFromString(torrent)
            val downloaded = jsonTorrent["downloaded"].asLong.toDouble()
            val size = jsonTorrent["size"].asLong.toDouble()
            val downloadSpeed = jsonTorrent["downloadSpeed"].asLong.toDouble()
            val remainingSeconds = ((size - downloaded) / downloadSpeed).toLong()
            val duration = Duration.ofSeconds(remainingSeconds)
            torrent.progress = String.format("%f", downloaded / size)
            torrent.eta = String.format("ETA: %s", duration.toString())
            torrent.status = jsonTorrent["status"].asString
            val links = jsonTorrent["links"].asJsonArray
            if (remoteId != null) {
                torrent.fileList.clear()
                torrent.fileList.addAll(extractFiles(links))
            }
        }
        return torrent
    }

    private fun extractFiles(links: JsonArray?): List<TorrentFile> {
        val torrentFiles = ArrayList<TorrentFile>()
        links?.forEach(Consumer { jsonElement: JsonElement ->
            val torrentFile = TorrentFile()
            torrentFile.filesize = jsonElement.asJsonObject["size"].asLong
            torrentFile.name = jsonElement.asJsonObject["filename"].asString
            torrentFile.url = jsonElement.asJsonObject["link"].asString
            torrentFiles.add(torrentFile)
        })
        torrentFiles.forEach(Consumer { torrentFile: TorrentFile -> resolveDirectLink(torrentFile) })
        return torrentFiles
    }

    private fun resolveDirectLink(torrentFile: TorrentFile) {
        val baseUrl = "https://api.alldebrid.com/v4/link/unlock?agent=pirateboat&apikey=" + PropertiesHelper.getProperty("alldebrid_apikey") + "&link=%s"
        val requestUrl = String.format(baseUrl, TorrentHelper.urlEncode(torrentFile.url))
        val pageContent = httpHelper.getPage(requestUrl)
        val jsonRoot = JsonParser.parseString(pageContent)
        val data = jsonRoot.asJsonObject["data"]
        if (data != null) {
            torrentFile.url = data.asJsonObject["link"].asString
        }
    }

    override fun enrichCacheStateOfTorrents(torrents: List<Torrent>) {
        val requestUrl = "https://api.alldebrid.com/v4/magnet/instant?agent=pirateboat&apikey=" + PropertiesHelper.getProperty("alldebrid_apikey") + "%s"
        val urlEncodedBrackets = TorrentHelper.urlEncode("[]")
        val collected = torrents.stream().map { obj: Torrent -> obj.torrentId }.collect(Collectors.joining("&magnets$urlEncodedBrackets=", "&magnets$urlEncodedBrackets=", ""))
        val checkUrl = String.format(requestUrl, collected)
        val pageContent = httpHelper.getPage(checkUrl)
        val jsonRoot = JsonParser.parseString(pageContent)
        if (jsonRoot == null || !jsonRoot.isJsonObject) {
            log.error("couldn't retrieve cache for:$checkUrl")
        } else {
            val response = jsonRoot.asJsonObject["data"].asJsonObject["magnets"]
            val responseArray = response.asJsonArray
            val index = AtomicInteger()
            if (responseArray.size() == torrents.size) {
                responseArray.forEach(Consumer { jsonElement: JsonElement ->
                    if (jsonElement.asJsonObject["instant"].asBoolean) {
                        torrents[index.get()].cached.add(this.javaClass.simpleName)
                    }
                    index.getAndIncrement()
                })
            }
        }
    }

    override fun delete(remoteTorrent: Torrent) {
        val requestUrl = "https://api.alldebrid.com/v4/magnet/delete?agent=pirateboat&apikey=" + PropertiesHelper.getProperty("alldebrid_apikey") + "&id=" + remoteTorrent.remoteId
        httpHelper.getPage(requestUrl)
    }

    override fun getFilesFromTorrent(torrent: Torrent): List<TorrentFile> {
        val remoteTorrent = getRemoteTorrentById(torrent.remoteId)
        return remoteTorrent!!.fileList
    }

    override fun getPrio(): Int {
        return 0
    }

    override fun getName(): String {
        return this.javaClass.simpleName
    }

    companion object {
        private val log = LoggerFactory.getLogger(Alldebrid::class.java)
    }
}