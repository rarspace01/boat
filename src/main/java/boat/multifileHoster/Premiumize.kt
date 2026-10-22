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
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.regex.Pattern
import java.util.stream.Collectors

class Premiumize(httpHelper: HttpHelper) : HttpUser(httpHelper), MultifileHoster {
    private val jsonMapper = JsonMapper.builder().build()

    /**
     * For already cached torrents the id returned by transfer/create differs from the id in transfer/list,
     * so the remote id is resolved by diffing the transfer list before and after the add.
     */
    @Synchronized
    override fun addTorrentToDownloadQueue(toBeAddedTorrent: Torrent): AddTransferResult {
        val remoteIdsBefore = getRemoteTorrents().map { it.remoteId }.toSet()
        val addTorrenntUrl = buildAddTransferUrl(PropertiesHelper.getProperty("PREMIUMIZE_APIKEY"), toBeAddedTorrent.magnetUri)
        val response = httpHelper.getPage(addTorrenntUrl)
        val createdId = extractCreatedId(response) ?: return AddTransferResult(response)
        var remoteTorrentsAfter = getRemoteTorrents()
        if (remoteTorrentsAfter.none { it.remoteId !in remoteIdsBefore }) {
            Thread.sleep(2000)
            remoteTorrentsAfter = getRemoteTorrents()
        }
        val remoteId = resolveRemoteId(toBeAddedTorrent, remoteIdsBefore, remoteTorrentsAfter, createdId)
        return AddTransferResult(response, remoteId)
    }

    private fun extractCreatedId(response: String): String? {
        return try {
            val jsonNode = jsonMapper.readTree(response)
            if (jsonNode.path("status").asString() == "success") jsonNode.path("id").asString().ifEmpty { null } else null
        } catch (_: Exception) {
            null
        }
    }

    fun resolveRemoteId(
        toBeAddedTorrent: Torrent,
        remoteIdsBefore: Set<String>,
        remoteTorrentsAfter: List<Torrent>,
        createdId: String
    ): String {
        val newRemoteTorrents = remoteTorrentsAfter.filter { it.remoteId !in remoteIdsBefore }
        val hash = toBeAddedTorrent.torrentId.lowercase()
        newRemoteTorrents.firstOrNull { it.magnetUri.isNotEmpty() && it.torrentId == hash }?.let { return it.remoteId }
        if (newRemoteTorrents.size == 1) {
            val remoteId = newRemoteTorrents.first().remoteId
            if (remoteId != createdId) {
                log.info("resolved remote id [{}] instead of created id [{}] for [{}]", remoteId, createdId, toBeAddedTorrent.magnetUri)
            }
            return remoteId
        }
        log.warn(
            "couldn't resolve remote id for [{}], {} new remote transfers, using created id [{}]",
            toBeAddedTorrent.magnetUri, newRemoteTorrents.size, createdId
        )
        return createdId
    }

    /**
     * The magnet uri has to be url encoded as a whole, otherwise its own parameters (dn, tr) are interpreted
     * as parameters of the premiumize api and premiumize names the transfer after the truncated magnet uri
     */
    fun buildAddTransferUrl(apiKey: String?, magnetUri: String): String {
        return "https://www.premiumize.me/api/transfer/create?apikey=" + apiKey +
                "&type=hello.torrent&src=" + TorrentHelper.urlEncode(cleanMagnetUri(magnetUri))
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
        } catch (e: Exception) {
            // a partial listing would upload some files and then delete the rest from the hoster
            log.error("could not read the files of [{}]: {}", torrent.name, e.message)
            return emptyList()
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
        // errors propagate to getFilesFromTorrent, which then returns no files at all
        val rootNode = jsonMapper.readTree(responseFiles)
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
    }

    private fun requiredText(node: JsonNode, field: String): String {
        val value = node.path(field)
        require(!value.isMissingNode && !value.isNull) { "missing field [$field] in $node" }
        return value.asString()
    }

    private fun extractTorrentFileFromJSON(
        returnList: MutableList<TorrentFile>,
        jsonFile: JsonNode,
        prefix: String
    ) {
        val tf = TorrentFile(
            name = prefix + requiredText(jsonFile, "name"),
            id = requiredText(jsonFile, "id"),
            filesize = jsonFile.path("size").asLong(),
            url = requiredText(jsonFile, "link"),
        )
        returnList.add(tf)
    }

    private fun parseRemoteTorrents(pageContent: String): ArrayList<Torrent> {
        val remoteTorrentList = ArrayList<Torrent>()
        try {
            val rootNode = jsonMapper.readTree(pageContent)
            val localNodes = rootNode.path("transfers")
            for (localNode in localNodes) {
                try {
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
                } catch (e: Exception) {
                    // a single malformed transfer must not hide all other transfers
                    log.error("couldn't parse transfer: {}", localNode, e)
                }
            }
        } catch (e: Exception) {
            log.error("couldn't parse transfers: {}", pageContent, e)
        }
        return remoteTorrentList
    }

    /**
     * Parses the ETA of a transfer message like "2 days left", "1 hour left" or "about 3 mins left", unknown -> zero.
     */
    private fun extractDurationFromString(durationString: String): Duration {
        val match = durationPattern.find(durationString) ?: return Duration.ZERO
        val value = match.groupValues[1].toLong()
        return when (match.groupValues[2].lowercase()) {
            "day" -> Duration.ofDays(value)
            "hour" -> Duration.ofHours(value)
            "min" -> Duration.ofMinutes(value)
            else -> Duration.ofSeconds(value)
        }
    }

    override fun delete(remoteTorrent: Torrent) {
        val removeTorrenntUrl = "https://www.premiumize.me/api/transfer/delete?id=" + remoteTorrent.remoteId +
                "&apikey=" + PropertiesHelper.getProperty("PREMIUMIZE_APIKEY")
        val page = httpHelper.getPage(removeTorrenntUrl)
        if (!page.contains("success")) {
            log.error("Deleting failed: {}", HttpHelper.redactSecrets(removeTorrenntUrl))
        }
    }

    /** The cache of up to [MAXIMUM_QUERY_CACHE_SIZE] torrents is checked per request, the requests run in parallel. */
    override fun enrichCacheStateOfTorrents(torrents: List<Torrent>) {
        val batches = torrents.chunked(MAXIMUM_QUERY_CACHE_SIZE)
        if (batches.size <= 1) {
            batches.forEach { enrichCacheStatusForGivenTorrents(it) }
        } else {
            batches.parallelStream().forEach { enrichCacheStatusForGivenTorrents(it) }
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
            log.error("couldn't retrieve cache for:${HttpHelper.redactSecrets(checkUrl)}")
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
                log.error("couldn't retrieve cache for:${HttpHelper.redactSecrets(checkUrl)}")
                log.error(pageContent)
            }
        }
    }

    companion object {
        private const val MAXIMUM_QUERY_CACHE_SIZE = 100
        private val log = LoggerFactory.getLogger(Premiumize::class.java)
        private val durationPattern = Regex("(\\d+)\\s*(day|hour|min|sec)", RegexOption.IGNORE_CASE)
    }
}
