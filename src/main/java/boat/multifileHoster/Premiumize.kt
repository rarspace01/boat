package boat.multifileHoster

import boat.mapper.TorrentMapper
import boat.torrent.HttpUser
import boat.torrent.Torrent
import boat.torrent.TorrentFile
import boat.torrent.TorrentHelper
import boat.utilities.HttpHelper
import boat.utilities.PropertiesHelper
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.apache.logging.log4j.util.Strings
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.slf4j.LoggerFactory
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.regex.Pattern
import java.util.stream.Collectors

class Premiumize(httpHelper: HttpHelper) : HttpUser(httpHelper), MultifileHoster {
    private val jsonMapper = JsonMapper.builder().build()

    override fun addTorrentToDownloadQueue(toBeAddedTorrent: Torrent): String {
        val response: String
        val addTorrenntUrl =
            "https://www.premiumize.me/api/transfer/create?apikey=" + PropertiesHelper.getProperty("PREMIUMIZE_APIKEY") +
                    "&type=hello.torrent&src=" + cleanMagnetUri(toBeAddedTorrent.magnetUri)
        response = httpHelper.getPage(addTorrenntUrl)
        return response
    }

    private fun cleanMagnetUri(magnetUri: String): String {
        return magnetUri.replace(" ".toRegex(), "_")
    }

    override fun getRemoteTorrents(): List<Torrent> {
        val remoteTorrentList: List<Torrent>
        val responseTorrents: String =
            httpHelper.getPage("https://www.premiumize.me/api/transfer/list?apikey=" + PropertiesHelper.getProperty("PREMIUMIZE_APIKEY"))
        remoteTorrentList = parseRemoteTorrents(responseTorrents)
        return remoteTorrentList
    }

    override fun getPrio(): Int {
        return 1
    }

    override fun getRemainingTrafficInMB(): Double {
        val responseAccount: String =
            httpHelper.getPage("https://www.premiumize.me/api/account/info?apikey=" + PropertiesHelper.getProperty("PREMIUMIZE_APIKEY"))
        val boostAccount: String =
            httpHelper.getPage("https://www.premiumize.me/account?apikey=" + PropertiesHelper.getProperty("PREMIUMIZE_APIKEY"))
        return parseRemainingTrafficInMB(responseAccount) + parseRemainingBoostTrafficInMB(boostAccount)
    }

    override fun getMaximumActiveTransfers(): Int {
        return 25
    }

    private fun parseRemainingBoostTrafficInMB(boostAccount: String): Double {
        val document = Jsoup.parse(boostAccount)
        return document.getElementsByClass("col-md-12")
            .filter { element -> element.text().matches(Regex(".*Booster Points .* points available learn more.*")) }
            .filterNotNull()
            .map { element: Element ->
                var boosterPoints = 0.0
                val matcher =
                    Pattern.compile(".*Booster Points (.*) points available learn more.*").matcher(element.text())
                while (matcher.find()) {
                    boosterPoints = matcher.group(1).toDouble()
                }
                boosterPoints * 1024.0
            }.getOrElse(0, defaultValue = { 0.0 })
    }

    private fun parseRemainingTrafficInMB(responseAccount: String): Double {
        return try {
            val jsonNode = jsonMapper.readTree(responseAccount)
            (1.0 - jsonNode.get("limit_used").asDouble()) * 1024.0 * 1024.0
        } catch (_: Exception) {
            0.0
        }
    }

    override fun getName(): String {
        return this.javaClass.simpleName
    }

    override fun toString(): String {
        return getName()
    }

    override fun getFilesFromTorrent(torrent: Torrent): List<TorrentFile> {
        val returnList: MutableList<TorrentFile> = ArrayList()
        val responseFilesPage = if (isSingleFileTorrent(torrent)) {
            httpHelper.getPage(
                "https://www.premiumize.me/api/folder/list?" +
                        "includebreadcrumbs=false&apikey=" + PropertiesHelper.getProperty("PREMIUMIZE_APIKEY")
            )
        } else {
            httpHelper.getPage(
                "https://www.premiumize.me/api/folder/list?id=" + torrent.folder_id +
                        "&includebreadcrumbs=false&apikey=" + PropertiesHelper.getProperty("PREMIUMIZE_APIKEY")
            )
        }

        try {
            val rootNode = jsonMapper.readTree(responseFilesPage)
            val localNodes = rootNode.path("content")
            val fileList = localNodes.findParents("type")
            for (jsonFile in fileList) {
                if (jsonFile["type"].asString() == "file") {
                    extractTorrentFileFromJSON(returnList, jsonFile, "")
                } else if (jsonFile["type"].asString() == "folder") {
                    extractTorrentFilesFromJSONFolder(returnList, jsonFile, "")
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        val filteredList: List<TorrentFile> = if (isSingleFileTorrent(torrent)) {
            returnList.filter { torrentFile -> torrentFile.id.equals(torrent.file_id) }
        } else {
            returnList
        }
        return filteredList
    }

    private fun isSingleFileTorrent(torrent: Torrent) =
        torrent.file_id.isNotEmpty() && !torrent.file_id.contains("null")

    private fun extractTorrentFilesFromJSONFolder(
        returnList: MutableList<TorrentFile>,
        jsonFolder: JsonNode,
        prefix: String
    ) {
        val responseFiles = httpHelper.getPage(
            "https://www.premiumize.me/api/folder/list?id=" + jsonFolder["id"].asString() +
                    "&apikey=" + PropertiesHelper.getProperty("PREMIUMIZE_APIKEY")
        )
        val folderName = prefix + jsonFolder["name"].asString() + "/"
        val rootNode: JsonNode
        try {
            rootNode = jsonMapper.readTree(responseFiles)
            val localNodes = rootNode.path("content")
            val fileList = localNodes.findParents("type")
            for (jsonFile in fileList) {
                if (jsonFile["type"].asString() == "file") {
                    extractTorrentFileFromJSON(returnList, jsonFile, folderName)
                } else if (jsonFile["type"].asString() == "folder") {
                    extractTorrentFilesFromJSONFolder(returnList, jsonFile, folderName)
                } else {
                    log.error("file extraction error, type: {} - file: {}", jsonFile["type"].asString(), jsonFile)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun extractTorrentFileFromJSON(
        returnList: MutableList<TorrentFile>,
        jsonFile: JsonNode,
        prefix: String
    ) {
        val tf = TorrentFile(
            name = prefix + jsonFile["name"].asString(),
            id = jsonFile["id"].asString(),
            filesize = jsonFile["size"].asLong(),
            url = jsonFile["link"].asString(),
        )
        returnList.add(tf)
    }

    private fun parseRemoteTorrents(pageContent: String): ArrayList<Torrent> {
        val remoteTorrentList = ArrayList<Torrent>()
        try {
            val rootNode = jsonMapper.readTree(pageContent)
            val localNodes = rootNode.path("transfers")
            for (localNode in localNodes) {
                val tempTorrent = Torrent(getName())
                tempTorrent.name = localNode["name"].asString()
                tempTorrent.folder_id = localNode["folder_id"].asString()
                tempTorrent.file_id = localNode["file_id"].asString()
                tempTorrent.remoteId = localNode["id"].asString()
                tempTorrent.remoteStatusText = localNode["status"].asString()
                tempTorrent.remoteTransferStatus = TorrentMapper.mapRemoteStatus(tempTorrent.remoteStatusText)
                val src = localNode["src"].asString()
                if (src.contains("btih")) {
                    tempTorrent.magnetUri = src
                }
                val messages = localNode["message"].asString().split(",").toTypedArray()
                if (messages.size == 3) {
                    val extractDurationFromString = extractDurationFromString(messages[2])
                    tempTorrent.eta = extractDurationFromString
                }
                tempTorrent.remoteProgress = localNode["progress"].toString()
                tempTorrent.remoteProgressInPercent = localNode["progress"].asDouble()
                remoteTorrentList.add(tempTorrent)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return remoteTorrentList
    }

    private fun extractDurationFromString(durationString: String): Duration {
        if (durationString.contains("unknown")) return Duration.ZERO
        val valueOfDuration = durationString
            .replace("days", "", true)
            .replace("hours", "", true)
            .replace("mins", "", true)
            .replace("seconds", "", true)
            .replace("left", "", true)
            .trim()
        if (durationString.contains("days")) return Duration.ofDays(valueOfDuration.toLong())
        if (durationString.contains("hours")) return Duration.ofHours(valueOfDuration.toLong())
        if (durationString.contains("mins")) return Duration.ofMinutes(valueOfDuration.toLong())
        if (durationString.contains("seconds")) return Duration.ofSeconds(valueOfDuration.toLong())
        return Duration.ZERO
    }

    override fun delete(remoteTorrent: Torrent) {
        val removeTorrenntUrl = "https://www.premiumize.me/api/transfer/delete?id=" + remoteTorrent.remoteId +
                "&apikey=" + PropertiesHelper.getProperty("PREMIUMIZE_APIKEY")
        val page = httpHelper.getPage(removeTorrenntUrl)
        if (!page.contains("success")) {
            log.error("Deleting failed: {}", removeTorrenntUrl)
        }
    }

    override fun enrichCacheStateOfTorrents(torrents: List<Torrent>) {
        val maximumQueryCacheSize = 100
        if (torrents.size <= maximumQueryCacheSize) {
            enrichCacheStatusForGivenTorrents(torrents)
        } else {
            for (i in torrents.indices step maximumQueryCacheSize) {
                enrichCacheStatusForGivenTorrents(
                    torrents.subList(
                        i,
                        (i + maximumQueryCacheSize).coerceAtMost(torrents.size - 1)
                    )
                )
            }
        }
    }

    private fun enrichCacheStatusForGivenTorrents(torrents: List<Torrent>) {
        val requestUrl =
            "https://www.premiumize.me/api/cache/check?" + "apikey=" + PropertiesHelper.getProperty("PREMIUMIZE_APIKEY") + "%s"
        val urlEncodedBrackets = TorrentHelper.urlEncode("[]")
        val collected = torrents.stream().map { obj: Torrent -> obj.torrentId }
            .collect(Collectors.joining("&items$urlEncodedBrackets=", "&items$urlEncodedBrackets=", ""))
        val checkUrl = String.format(requestUrl, collected)
        var pageContent = httpHelper.getPage(checkUrl)
        if (Strings.isEmpty(pageContent)) {
            Thread.sleep(100)
            pageContent = httpHelper.getPage(checkUrl)
        }
        val jsonRoot = JsonParser.parseString(pageContent)
        if (jsonRoot == null || !jsonRoot.isJsonObject) {
            log.error("couldn't retrieve cache for:$checkUrl")
            log.error(pageContent)
        } else {
            val response = jsonRoot.asJsonObject["response"]
            if (response != null && !response.isJsonNull) {
                val responseArray = response.asJsonArray
                val index = AtomicInteger()
                if (responseArray.size() == torrents.size) {
                    responseArray.forEach(
                        Consumer { jsonElement: JsonElement ->
                            if (jsonElement.asBoolean) {
                                torrents[index.get()].cached.add(this.javaClass.simpleName)
                            }
                            index.getAndIncrement()
                        }
                    )
                }
            } else {
                log.error("couldn't retrieve cache for:$checkUrl")
                log.error(pageContent)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(Premiumize::class.java)
    }
}
