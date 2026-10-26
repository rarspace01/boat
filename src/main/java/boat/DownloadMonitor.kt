package boat

import boat.info.CloudFileService
import boat.info.CloudService
import boat.info.MediaItem
import boat.info.MediaProxyService
import boat.info.MediaType
import boat.info.QueueService
import boat.model.QueueEntry
import boat.model.Transfer
import boat.model.TransferStatus
import boat.multifileHoster.MultifileHosterService
import boat.multifileHoster.MultifileHosterService.QueuedTorrentResult
import boat.services.ConfigurationService
import boat.services.TransferService
import boat.torrent.Torrent
import boat.torrent.TorrentHelper.removeSpecialCharacters
import boat.torrent.TorrentSearchEngineService
import boat.torrent.TorrentType
import boat.utilities.HttpHelper
import boat.utilities.LoggerDelegate
import boat.utilities.PropertiesHelper
import com.google.gson.JsonParser
import org.apache.logging.log4j.util.Strings
import org.springframework.cache.CacheManager
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.exitProcess

@EnableAsync
@Component
class DownloadMonitor(
    private val torrentSearchEngineService: TorrentSearchEngineService,
    private val cloudService: CloudService,
    private val multifileHosterService: MultifileHosterService,
    private val cloudFileService: CloudFileService,
    private val cacheManager: CacheManager,
    private val queueService: QueueService,
    private val transferService: TransferService,
    private val configurationService: ConfigurationService,
    private val httpHelper: HttpHelper,
    private val mediaProxyService: MediaProxyService
) {
    /**
     * The `@Async` jobs which are running. A job is skipped while its last run is still going (each runs on a thread of
     * its own), e.g. a queue entry is only removed after its torrent search, which can take longer than the 30 s between two runs.
     */
    private val runningJobs: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private inline fun runExclusively(job: String, block: () -> Unit) {
        if (!runningJobs.add(job)) {
            logger.debug("{} is still running, skipped", job)
            return
        }
        try {
            block()
        } finally {
            runningJobs.remove(job)
        }
    }

    @Async
    @Scheduled(fixedRate = (SECONDS_BETWEEN_SEARCH_ENGINE_POLLING * 1000).toLong())
    fun refreshTorrentSearchEngines() {
        if (configurationService.isSearchMode()) runExclusively("refreshTorrentSearchEngines") {
            logger.debug("refreshTorrentSearchEngines()")
            torrentSearchEngineService.refreshTorrentSearchEngines()
        }
    }

    @Async
    @Scheduled(fixedRate = (SECONDS_BETWEEN_FILE_CACHE_REFRESH * 1000).toLong())
    fun refreshCloudFileServiceCache() {
        if (configurationService.isSearchMode()) runExclusively("refreshCloudFileServiceCache") {
            logger.info("refreshCloudFileServiceCache()")
            val startOfCache = System.currentTimeMillis()
            if (multifileHosterService.isRcloneInstalled()) {
                val filesCache = cacheManager.getCache("filesCache")
                "abcdefghijklmnopqrstuvwxyz+0".split(Regex("")).filter { it.isNotBlank() }.map { searchName: String ->
                    TorrentType.entries
                        .forEach { torrentType: TorrentType ->
                            val destinationPath = cloudService
                                .buildDestinationPathWithTypeOfMediaWithoutSubFolders(searchName, torrentType)
                            filesCache?.evictIfPresent(destinationPath)
                            cloudFileService.getFilesInPath(destinationPath)
                        }
                    logger.info("Cache refresh done for: {}", searchName)
                }
                logger.info("Cache refresh done in: {}ms", System.currentTimeMillis() - startOfCache)
                cloudFileService.isCacheFilled = true
            } else {
                logger.error("rclone not installed")
            }
        }
    }

    @Async
    @Scheduled(fixedRate = (SECONDS_BETWEEN_TRANSFER_POLLING * 1000).toLong())
    fun addTransfersToDownloadQueueAndUpdateTransferStatus() {
        if (configurationService.isDownloadMode()) runExclusively("addTransfersToDownloadQueueAndUpdateTransferStatus") {
            multifileHosterService.addTransfersToDownloadQueue()
            multifileHosterService.updateTransferStatus()
        }
    }

    @Async
    @Scheduled(fixedRate = (SECONDS_BETWEEN_DOWNLOAD_POLLING * 1000).toLong())
    fun checkForDownloadableTorrents() {
        if (configurationService.isDownloadMode()) {
            multifileHosterService.checkForDownloadableTorrentsAndDownload()
        }
    }

    @Async
    @Scheduled(fixedRate = (SECONDS_BETWEEN_VERSION_CHECK * 1000).toLong(), initialDelay = 60 * 5 * 1000)
    fun checkForUpdatedVersionAndShutdownIfUpdateAvailable() {
        runExclusively("checkForUpdatedVersion") { checkForUpdatedVersionAndShutdown() }
    }

    private fun checkForUpdatedVersionAndShutdown() {
        logger.info("checkForUpdatedVersionAndShutdown()")
        val githubVersion = runCatching {
            val pageContent = httpHelper.getPage("https://api.github.com/repos/rarspace01/boat/releases/latest")
            if (Strings.isEmpty(pageContent)) null else JsonParser.parseString(pageContent).asJsonObject["name"]?.asString
        }.onFailure { logger.warn("version check failed: {}", it.message) }.getOrNull()
        if (githubVersion.isNullOrBlank()) return
        if (!isVersionUpToDate(PropertiesHelper.version, githubVersion)) {
            logger.info("Local [{}] != Github [{}]", PropertiesHelper.version, githubVersion)
            if (transferService.getAll().any { it.transferStatus == TransferStatus.UPLOADING_TO_DRIVE }) {
                logger.info("update available, but an upload is running, retrying later")
                return
            }
            logger.warn("version out of date, shutting down")
            exitProcess(0)
        }
    }

    @Async
    @Scheduled(fixedRate = (SECONDS_BETWEEN_QUEUE_POLLING * 1000).toLong())
    fun checkForQueueEntries() {
        if (cloudService.isTargetFolderReady && configurationService.isSearchMode()) runExclusively("checkForQueueEntries") {
            logger.info("checkForQueueEntryAndAddToTransfers")
            checkForQueueEntryAndAddToTransfers()
        }
    }

    /** Queue entries (by id) which wait until then for the traffic of the hoster which has their torrent cached. */
    private val waitingForCachedHoster = ConcurrentHashMap<String, Instant>()

    /** Why the queue waited the last time, so the reason is only logged when it changes and not every 30 s. */
    @Volatile
    private var lastQueueWaitReason: String? = null

    private fun logQueueWaitReason(reason: String?) {
        if (reason != lastQueueWaitReason) {
            if (reason != null) logger.info("queue waits: {}", reason) else if (lastQueueWaitReason != null) logger.info("queue continues")
            lastQueueWaitReason = reason
        }
    }

    private fun checkForQueueEntryAndAddToTransfers() {
        // the hoster is only asked for its transfers and the traffic if there is something to add
        val queue = queueService.getQueue()
        if (queue.isEmpty()) {
            logQueueWaitReason("the queue is empty")
            return
        }
        val remoteTorrents = multifileHosterService.remoteTorrents
        val numberOfActiveRemoteTorrents = remoteTorrents.count { torrent: Torrent -> torrent.remoteTransferStatus != TransferStatus.READY_TO_BE_DOWNLOADED }
        val readyTorrents = remoteTorrents.filter { torrent: Torrent -> torrent.remoteTransferStatus == TransferStatus.READY_TO_BE_DOWNLOADED }
        // finished torrents nobody downloads (the upload to the drive failed for good, or there is no transfer) don't block the queue
        val transfers = transferService.getAll()
        val torrentsWaitingForDownload = readyTorrents.filter { torrent -> isWaitingForDownload(torrent, transfers) }
        val waitReason = when {
            torrentsWaitingForDownload.isNotEmpty() ->
                "${torrentsWaitingForDownload.size} torrent(s) at the hoster wait for their download: ${torrentsWaitingForDownload.map { it.name }}"
            numberOfActiveRemoteTorrents >= MAX_QUEUE_DOWNLOADS_LIMIT ->
                "$numberOfActiveRemoteTorrents torrents are active at the hoster (limit $MAX_QUEUE_DOWNLOADS_LIMIT)"
            else -> multifileHosterService.getRemainingTrafficInMB().let { traffic ->
                if (traffic > MIN_GB_FOR_QUEUE * 1024) null
                else "%.1f GB traffic left at the hosters, %d GB are needed".format(traffic / 1024, MIN_GB_FOR_QUEUE)
            }
        }
        logQueueWaitReason(waitReason)
        if (waitReason == null) {
            if (readyTorrents.size > torrentsWaitingForDownload.size) {
                logger.debug("finished torrents at the hoster which aren't downloaded: {}", (readyTorrents - torrentsWaitingForDownload.toSet()).map { it.name })
            }
            // an entry which waits for the traffic of the hoster which has its torrent cached lets the others go first
            val now = Instant.now()
            waitingForCachedHoster.values.removeIf { it.isBefore(now) }
            queue.firstOrNull { !waitingForCachedHoster.containsKey(QueueEntry.idOf(it)) }?.let { mediaItem: MediaItem ->
                logger.info("picked {} ({} entries in the queue)", mediaItem, queue.size)
                logger.info("picked {}", mediaItem)
                // items from the search list are already matched, older queue entries are matched now
                val matchedMediaItem = if (isMatched(mediaItem)) {
                    mediaItem
                } else {
                    mediaProxyService.findBestMatch(mediaItem.title, mediaItem.year)
                }
                val selectedTitle = matchedMediaItem?.title ?: mediaItem.title
                val selectedYear = if (matchedMediaItem != null) {
                    matchedMediaItem.year?.takeIf { matchedMediaItem.type == MediaType.Movie }
                } else {
                    mediaItem.year
                }
                val remoteMediaId = (matchedMediaItem?.id ?: mediaItem.id)?.ifBlank { null }

                val cleanedTitle = removeSpecialCharacters(selectedTitle)
                val torrentSearchString = cleanedTitle + (selectedYear?.let { " $it" } ?: "")
                val existingFiles = (cloudService.findExistingFiles(cleanedTitle) +
                        (remoteMediaId?.let { cloudService.findExistingFiles(it) } ?: emptyList())).distinct()
                if (existingFiles.isEmpty()) {
                    val torrents = torrentSearchEngineService.searchTorrents(torrentSearchString)
                    when (val result = multifileHosterService.addQueuedTorrentToTransfer(torrents, remoteMediaId, matchedMediaItem?.type)) {
                        is QueuedTorrentResult.Added -> logger.info("added [{}] for [{}] to {}", result.torrent.name, torrentSearchString, result.hoster)
                        QueuedTorrentResult.WaitsForTrafficOfCachedHoster -> {
                            logger.info("[{}] is only cached without seeders, at hosters without enough traffic, it stays in the queue for {}h",
                                    torrentSearchString, WAIT_FOR_CACHED_HOSTER.toHours())
                            waitingForCachedHoster[QueueEntry.idOf(mediaItem)] = now.plus(WAIT_FOR_CACHED_HOSTER)
                            return
                        }
                        QueuedTorrentResult.NothingDownloadable ->
                            logger.warn("no torrent found for [{}], removed {} from the queue", torrentSearchString, mediaItem)
                    }
                } else {
                    logger.warn("Looks like Torrent was already downloaded, skipped {} - matched files: {}", mediaItem, existingFiles)
                }
                removeFromQueue(mediaItem)
            }
        }
    }

    private fun removeFromQueue(mediaItem: MediaItem) {
        queueService.remove(mediaItem)
    }

    @Scheduled(fixedRate = (SECONDS_BETWEEN_CLEAR_TRANSFER_POLLING * 1000).toLong())
    fun clearTransferAndTorrentsWithErrors() {
        if (configurationService.isDownloadMode()) {
            logger.info("clearTransferTorrents()")
            multifileHosterService.remoteTorrents.stream()
                .filter { torrent: Torrent -> isTorrentStuckOnError(torrent) }
                .forEach { torrent: Torrent? -> multifileHosterService.delete(torrent!!) }
            transferService.getAll().stream()
                .filter { transfer: Transfer -> isTransferObsolete(transfer) }
                .forEach { transfer: Transfer? -> transferService.delete(transfer!!) }
        }
    }

    private fun isTorrentStuckOnError(torrent: Torrent): Boolean {
        return torrent.remoteTransferStatus == TransferStatus.ERROR
    }

    companion object {
        /** True if [githubVersion] is the local version, ignoring that `1.2.1` is a prefix of `1.2.10`. */
        internal fun isVersionUpToDate(localVersion: String?, githubVersion: String): Boolean =
            localVersion != null && Regex("(?<![0-9.])" + Regex.escape(githubVersion) + "(?![0-9.])").containsMatchIn(localVersion)

        /** Transfers waiting in [TransferStatus.ADDED] for traffic are not updated, so they get more time. */
        internal fun isTransferObsolete(transfer: Transfer, now: Instant = Instant.now()): Boolean {
            if (TransferStatus.ERROR == transfer.transferStatus || TransferStatus.DUPLICATE_ERROR == transfer.transferStatus) return true
            val maxAgeInDays = if (TransferStatus.ADDED == transfer.transferStatus) 7L else 1L
            return transfer.updated.isBefore(now.minus(maxAgeInDays, ChronoUnit.DAYS))
        }

        /**
         * True if a transfer will download the finished [torrent]: one in [TransferStatus.READY_TO_BE_DOWNLOADED] or
         * already uploading. Its files stay at the hoster if the upload failed for good, and without a transfer nothing
         * downloads it at all.
         */
        internal fun isWaitingForDownload(torrent: Torrent, transfers: List<Transfer>): Boolean =
            transfers.any { transfer ->
                transfer.transferStatus in listOf(TransferStatus.READY_TO_BE_DOWNLOADED, TransferStatus.UPLOADING_TO_DRIVE) &&
                        ((transfer.remoteId != null && transfer.remoteId == torrent.remoteId) ||
                                (torrent.torrentId.isNotEmpty() && transfer.uri.lowercase().contains(torrent.torrentId.lowercase())))
            }

        private fun isMatched(mediaItem: MediaItem) =
            !mediaItem.id.isNullOrBlank() && mediaItem.type != null && mediaItem.type != MediaType.Other

        const val MIN_GB_FOR_QUEUE = 150
        private val WAIT_FOR_CACHED_HOSTER: Duration = Duration.ofHours(1)
        const val MAX_QUEUE_DOWNLOADS_LIMIT = 20
        private const val SECONDS_BETWEEN_DOWNLOAD_POLLING = 30
        private const val SECONDS_BETWEEN_TRANSFER_POLLING = 30
        private const val SECONDS_BETWEEN_VERSION_CHECK = 60 * 60
        private const val SECONDS_BETWEEN_QUEUE_POLLING = 30
        private const val SECONDS_BETWEEN_SEARCH_ENGINE_POLLING = 240
        private const val SECONDS_BETWEEN_CLEAR_TRANSFER_POLLING = 3600
        private const val SECONDS_BETWEEN_FILE_CACHE_REFRESH = 60 * 60 * 6
        private val logger by LoggerDelegate()
    }
}