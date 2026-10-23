package boat.multifileHoster

import boat.mapper.TorrentMapper
import boat.model.TransferStatus
import boat.torrent.HttpUser
import boat.torrent.Torrent
import boat.torrent.TorrentFile
import boat.torrent.TorrentHelper
import boat.utilities.HttpHelper
import boat.utilities.PropertiesHelper
import org.slf4j.LoggerFactory
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

/**
 * AllDebrid, API v4 and v4.1 (https://docs.alldebrid.com). The api key goes in the `Authorization` header, so it is
 * never part of an url (and a log), the parameters are sent as POST form. Errors come as HTTP 200 with
 * `"status": "error"`. Not registered in [MultifileHosterService] yet, only Premiumize is used.
 */
class Alldebrid(
    httpHelper: HttpHelper,
    private val apiKey: () -> String? = { PropertiesHelper.getProperty("ALLDEBRID_APIKEY") },
    /** How often and how long to wait for a link that AllDebrid has to prepare first (`delayed`). */
    private val delayedLinkPollMillis: Long = 5_000,
    private val delayedLinkMaxPolls: Int = 24,
) : HttpUser(httpHelper), MultifileHoster {
    private val jsonMapper = JsonMapper.builder().build()

    override fun addTorrentToDownloadQueue(toBeAddedTorrent: Torrent): AddTransferResult {
        val response = call("v4/magnet/upload", "magnets[]" to toBeAddedTorrent.magnetUri)
        // the service treats a message with "error" in it as failed
        if (response.isEmpty()) return AddTransferResult("error: no answer from AllDebrid")
        val magnet = data(response)?.path("magnets")?.path(0)
        val remoteId = magnet?.path("id")?.takeIf { it.isNumber }?.asString()
        if (remoteId == null || magnet.path("error")?.isMissingNode == false) {
            log.error("adding the magnet failed: {}", response)
            return AddTransferResult(if (response.contains("error")) response else "error: $response")
        }
        return AddTransferResult(response, remoteId)
    }

    override fun getRemoteTorrents(): List<Torrent> {
        val magnets = data(call("v4.1/magnet/status"))?.path("magnets") ?: return emptyList()
        return magnets.mapNotNull { magnet ->
            try {
                parseMagnet(magnet)
            } catch (e: Exception) {
                // a single malformed magnet must not hide all others
                log.error("couldn't parse magnet: {}", magnet, e)
                null
            }
        }
    }

    internal fun parseMagnet(magnet: JsonNode): Torrent {
        val torrent = Torrent(getName())
        torrent.remoteId = requiredText(magnet, "id")
        torrent.name = requiredText(magnet, "filename")
        val size = magnet.path("size").asLong()
        torrent.size = TorrentHelper.humanReadableByteCountBinary(size)
        torrent.sizeInMB = size / 1024.0 / 1024.0
        torrent.remoteStatusText = magnet.path("status").asString()
        torrent.remoteStatusCode = magnet.path("statusCode").asInt(-1)
        torrent.remoteTransferStatus = TorrentMapper.mapRemoteStatus(torrent.remoteStatusCode)
        // finished magnets don't tell what was downloaded
        val downloaded = if (torrent.remoteTransferStatus == TransferStatus.READY_TO_BE_DOWNLOADED) size
        else magnet.path("downloaded").asLong()
        torrent.remoteProgressInPercent = if (size > 0) downloaded.toDouble() / size else 0.0
        torrent.remoteProgress = String.format("%f", torrent.remoteProgressInPercent)
        val downloadSpeed = magnet.path("downloadSpeed").asLong()
        torrent.eta = if (downloadSpeed > 0 && size > downloaded) Duration.ofSeconds((size - downloaded) / downloadSpeed) else Duration.ZERO
        // documented without, but the status has it
        magnet.path("hash").asString().takeIf { it.isNotBlank() }?.let {
            torrent.magnetUri = TorrentHelper.buildMagnetUriFromHash(it, torrent.name)
        }
        return torrent
    }

    /**
     * The files of a ready magnet with direct download links. Empty if any of them can't be unlocked: a partial
     * listing would upload some files and then delete the rest from the hoster.
     */
    override fun getFilesFromTorrent(torrent: Torrent): List<TorrentFile> {
        if (torrent.remoteId.isEmpty()) return emptyList()
        val files = data(call("v4/magnet/files", "id[]" to torrent.remoteId))?.path("magnets")?.path(0)?.path("files")
        if (files == null || !files.isArray) {
            log.error("could not read the files of [{}]", torrent.name)
            return emptyList()
        }
        val torrentFiles = mutableListOf<TorrentFile>()
        try {
            collectFiles(files, "", torrentFiles)
        } catch (e: Exception) {
            log.error("could not read the files of [{}]: {}", torrent.name, e.message)
            return emptyList()
        }
        return torrentFiles
    }

    /** The files tree: a folder has a name `n` and entries `e`, a file a name `n`, a size `s` and a link `l`. */
    private fun collectFiles(nodes: JsonNode, prefix: String, torrentFiles: MutableList<TorrentFile>) {
        for (node in nodes) {
            val name = requiredText(node, "n")
            if (node.has("e")) {
                collectFiles(node.path("e"), "$prefix$name/", torrentFiles)
            } else {
                val link = requiredText(node, "l")
                val directLink = unlock(link) ?: throw IllegalStateException("could not unlock [$prefix$name]")
                torrentFiles.add(TorrentFile(name = prefix + name, id = link, filesize = node.path("s").asLong(), url = directLink))
            }
        }
    }

    /** Turns the link of a file into a direct download link, waits for links AllDebrid has to prepare first. */
    internal fun unlock(link: String): String? {
        val unlocked = data(call("v4/link/unlock", "link" to link)) ?: return null
        unlocked.path("link").asString().takeIf { it.isNotBlank() }?.let { return it }
        val delayedId = unlocked.path("delayed").takeIf { it.isNumber }?.asString() ?: return null
        repeat(delayedLinkMaxPolls) {
            Thread.sleep(delayedLinkPollMillis)
            val delayed = data(call("v4/link/delayed", "id" to delayedId)) ?: return null
            when (delayed.path("status").asInt()) {
                DELAYED_LINK_READY -> return delayed.path("link").asString().takeIf { it.isNotBlank() }
                DELAYED_LINK_FAILED -> return null
            }
        }
        log.error("the link of [{}] wasn't ready after {} tries", link, delayedLinkMaxPolls)
        return null
    }

    /**
     * There is no cache check anymore (`/v4/magnet/instant` doesn't exist), a magnet AllDebrid has already is `ready`
     * right after the upload.
     */
    override fun enrichCacheStateOfTorrents(torrents: List<Torrent>) {
    }

    override fun delete(remoteTorrent: Torrent) {
        if (data(call("v4/magnet/delete", "id" to remoteTorrent.remoteId)) == null) {
            log.error("Deleting failed: {}", remoteTorrent.remoteId)
        }
    }

    override fun getPrio(): Int {
        return 2
    }

    /**
     * Premium accounts have no traffic limit for magnets (only some file hosters have daily quotas), a trial has its
     * remaining quota, everything else none.
     */
    override fun getRemainingTrafficInMB(): Double {
        val user = data(call("v4/user"))?.path("user") ?: return 0.0
        return when {
            user.path("isPremium").asBoolean() -> UNLIMITED_TRAFFIC_MB
            user.path("isTrial").asBoolean() -> user.path("remainingTrialQuota").asDouble()
            else -> 0.0
        }
    }

    /** AllDebrid refuses more (MAGNET_TOO_MANY_ACTIVE). */
    override fun getMaximumActiveTransfers(): Int {
        return 30
    }

    override fun getName(): String {
        return this.javaClass.simpleName
    }

    override fun toString(): String {
        return getName()
    }

    private fun call(endpoint: String, vararg parameters: Pair<String, String>): String {
        val body = parameters.joinToString("&") { (name, value) -> TorrentHelper.urlEncode(name) + "=" + TorrentHelper.urlEncode(value) }
        val headers = mapOf("Authorization" to "Bearer ${apiKey()}", "Content-Type" to "application/x-www-form-urlencoded")
        return httpHelper.getPage(API_URL + endpoint, body, headers)
    }

    /** The `data` of a successful answer, null (and logged) for an error or an answer that can't be read. */
    private fun data(response: String): JsonNode? {
        if (response.isEmpty()) return null
        val root = try {
            jsonMapper.readTree(response)
        } catch (e: Exception) {
            log.error("couldn't read the answer of AllDebrid: {}", response.take(200))
            return null
        }
        if (root.path("status").asString() != "success") {
            log.error("AllDebrid answered with an error: {}", root.path("error"))
            return null
        }
        return root.path("data")
    }

    private fun requiredText(node: JsonNode, field: String): String {
        val value = node.path(field)
        require(!value.isMissingNode && !value.isNull) { "missing field [$field] in $node" }
        return value.asString()
    }

    companion object {
        private val log = LoggerFactory.getLogger(Alldebrid::class.java)
        private const val API_URL = "https://api.alldebrid.com/"
        private const val DELAYED_LINK_READY = 2
        private const val DELAYED_LINK_FAILED = 3

        /** 10 TB, more than any download; premium has no traffic limit for magnets. */
        const val UNLIMITED_TRAFFIC_MB = 10.0 * 1024 * 1024
    }
}
