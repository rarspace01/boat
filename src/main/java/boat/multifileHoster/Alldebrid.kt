package boat.multifileHoster

import boat.mapper.TorrentMapper
import boat.torrent.HttpUser
import boat.torrent.Torrent
import boat.torrent.TorrentFile
import boat.torrent.TorrentHelper
import boat.utilities.HttpHelper
import boat.utilities.PropertiesHelper
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.stream.Collectors

class Alldebrid(httpHelper: HttpHelper?) : HttpUser(httpHelper), MultifileHoster {
    override fun addTorrentToDownloadQueue(toBeAddedTorrent: Torrent): String {
        val requestUrl =
            "https://api.alldebrid.com/v4/magnet/upload?agent=pirateboat&apikey=" + PropertiesHelper.getProperty("ALLDEBRID_APIKEY") + "%s"
        val urlEncodedBrackets = TorrentHelper.urlEncode("[]")
        val collected = "&magnets" + urlEncodedBrackets + "=" + TorrentHelper.urlEncode(toBeAddedTorrent.magnetUri)
        val checkUrl = String.format(requestUrl, collected)
        log.info("Add Magnet URL: {}", checkUrl)
        val pageContent = httpHelper.getPage(checkUrl)
        val jsonRoot = JsonParser.parseString(pageContent)
        val status = jsonRoot.asJsonObject["status"]
        return if (status != null) status.asString else "error"
    }

    override fun getRemoteTorrents(): List<Torrent> {
        val torrents: MutableList<Torrent> = ArrayList()
        val requestUrl =
            "https://api.alldebrid.com/v4/magnet/status?agent=pirateboat&apikey=" + PropertiesHelper.getProperty("ALLDEBRID_APIKEY")
        val pageContent = httpHelper.getPage(requestUrl)
        val jsonRoot = JsonParser.parseString(pageContent)
        if (!pageContent.contains("data")) {
            log.error("page didn't contain data: {}", pageContent)
            return torrents
        }
        val jsonMagnets = jsonRoot.asJsonObject["data"].asJsonObject["magnets"].asJsonArray
        jsonMagnets.forEach(
            Consumer { jsonElement: JsonElement ->
                val torrent = Torrent(getName())
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
                torrent.remoteProgress = String.format("%f", downloaded / size)
                torrent.remoteProgressInPercent = downloaded / size
                torrent.eta = duration
                torrent.magnetUri = TorrentHelper.buildMagnetUriFromHash(jsonTorrent["hash"].asString, torrent.name)
                torrent.remoteStatusText = jsonTorrent["status"].asString
                torrent.remoteStatusCode = jsonTorrent["statusCode"].asInt
                torrent.remoteTransferStatus = TorrentMapper.mapRemoteStatus(torrent.remoteStatusCode)
                torrents.add(torrent)
            }
        )
        return torrents
    }

    private fun getRemoteTorrentById(remoteId: String?): Torrent? {
        val remoteIdString = if (remoteId == null) "" else "&id=$remoteId"
        val requestUrl =
            "https://api.alldebrid.com/v4/magnet/status?agent=pirateboat&apikey=" + PropertiesHelper.getProperty("ALLDEBRID_APIKEY") + remoteIdString
        val pageContent = httpHelper.getPage(requestUrl)
        var torrent: Torrent? = null
        val jsonRoot = JsonParser.parseString(pageContent)
        val data = jsonRoot.asJsonObject["data"]
        if (data != null) {
            torrent = Torrent(getName())
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
            torrent.remoteProgress = String.format("%f", downloaded / size)
            torrent.remoteProgressInPercent = downloaded / size
            torrent.eta = duration
            torrent.magnetUri = TorrentHelper.buildMagnetUriFromHash(jsonTorrent["hash"].asString, torrent.name)
            torrent.remoteStatusText = jsonTorrent["status"].asString
            torrent.remoteStatusCode = jsonTorrent["statusCode"].asInt
            torrent.remoteTransferStatus = TorrentMapper.mapRemoteStatus(torrent.remoteStatusCode)
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
        links?.forEach(
            Consumer { jsonElement: JsonElement ->
                val torrentFile = TorrentFile()
                torrentFile.filesize = jsonElement.asJsonObject["size"].asLong
                torrentFile.name = jsonElement.asJsonObject["filename"].asString
                torrentFile.url = jsonElement.asJsonObject["link"].asString
                torrentFiles.add(torrentFile)
            }
        )
        torrentFiles.forEach(Consumer { torrentFile: TorrentFile -> resolveDirectLink(torrentFile) })
        return torrentFiles
    }

    private fun resolveDirectLink(torrentFile: TorrentFile) {
        val baseUrl =
            "https://api.alldebrid.com/v4/link/unlock?agent=pirateboat&apikey=" + PropertiesHelper.getProperty("ALLDEBRID_APIKEY") + "&link=%s"
        val requestUrl = String.format(baseUrl, TorrentHelper.urlEncode(torrentFile.url))
        val pageContent = httpHelper.getPage(requestUrl)
        val jsonRoot = JsonParser.parseString(pageContent)
        val data = jsonRoot.asJsonObject["data"]
        if (data != null) {
            torrentFile.url = data.asJsonObject["link"].asString
        }
    }

    override fun enrichCacheStateOfTorrents(torrents: List<Torrent>) {
        val requestUrl =
            "https://api.alldebrid.com/v4/magnet/instant?agent=pirateboat&apikey=" + PropertiesHelper.getProperty("ALLDEBRID_APIKEY") + "%s"
        val urlEncodedBrackets = TorrentHelper.urlEncode("[]")
        val collected = torrents.stream().map { obj: Torrent -> obj.torrentId }
            .collect(Collectors.joining("&magnets$urlEncodedBrackets=", "&magnets$urlEncodedBrackets=", ""))
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
                responseArray.forEach(
                    Consumer { jsonElement: JsonElement ->
                        if (jsonElement.asJsonObject["instant"].asBoolean) {
                            torrents[index.get()].cached.add(this.javaClass.simpleName)
                        }
                        index.getAndIncrement()
                    }
                )
            }
        }
    }

    override fun delete(remoteTorrent: Torrent) {
        val requestUrl =
            "https://api.alldebrid.com/v4/magnet/delete?agent=pirateboat&apikey=" + PropertiesHelper.getProperty("ALLDEBRID_APIKEY") + "&id=" + remoteTorrent.remoteId
        httpHelper.getPage(requestUrl)
    }

    override fun getFilesFromTorrent(torrent: Torrent): List<TorrentFile> {
        val remoteTorrent = getRemoteTorrentById(torrent.remoteId)
        if (remoteTorrent != null) {
            return remoteTorrent.fileList
        } else {
            return emptyList()
        }
    }

    override fun getPrio(): Int {
        return 2
    }

    override fun getRemainingTrafficInMB(): Double {
        return 9999999.0
    }

    override fun getMaximumActiveTransfers(): Int {
        return 25
    }

    override fun getName(): String {
        return this.javaClass.simpleName
    }

    override fun toString(): String {
        return getName()
    }

    companion object {
        private val log = LoggerFactory.getLogger(Alldebrid::class.java)
    }
}
