package boat

import boat.info.CloudFileService
import boat.info.CloudService
import boat.info.MediaItem
import boat.info.MediaProxyService
import boat.info.MediaType
import boat.info.QueueService
import boat.model.Transfer
import boat.model.TransferStatus
import boat.multifileHoster.MultifileHosterService
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
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean
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
    /** A queue entry is only removed after its torrent search, which can take longer than the 30 s between two runs. */
    private val isQueueCheckInProgress = AtomicBoolean(false)

    @Async
    @Scheduled(fixedRate = (SECONDS_BETWEEN_SEARCH_ENGINE_POLLING * 1000).toLong())
    fun refreshTorrentSearchEngines() {
        if (configurationService.isSearchMode()) {
            logger.debug("refreshTorrentSearchEngines()")
            torrentSearchEngineService.refreshTorrentSearchEngines()
        }
    }

    //    @Scheduled(fixedRate = SECONDS_BETWEEN_FILE_CACHE_REFRESH * 1000)
    //    public void fillQueueWithMovies() {
    //        logger.info("fillQueueWithMovies()");
    //        final List<MediaItem> completeList = new java.util.ArrayList<>(Collections.emptyList());
    //        IntStream.rangeClosed(2006,2021)
    //            .parallel()
    //            .forEach(year -> {
    //                IntStream.rangeClosed(1,12)
    //                    .parallel()
    //                    .forEach(month -> {
    //                        logger.info("adding Queue for {}/{}", month, year);
    //                        completeList.addAll(bluRayComService.getReleasesForMonthAndYear(month, year));
    //                        logger.info("Queue Size: {}",completeList.size());
    //                    });
    //            });
    //        queueService.addAll(completeList);
    //    }
    @Async
    @Scheduled(fixedRate = (SECONDS_BETWEEN_FILE_CACHE_REFRESH * 1000).toLong())
    fun refreshCloudFileServiceCache() {
        if (configurationService.isSearchMode()) {
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
        if (configurationService.isDownloadMode()) {
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
        if (cloudService.isCloudTokenValid && configurationService.isSearchMode() && isQueueCheckInProgress.compareAndSet(false, true)) {
            try {
                logger.info("checkForQueueEntryAndAddToTransfers")
                checkForQueueEntryAndAddToTransfers()
            } finally {
                isQueueCheckInProgress.set(false)
            }
        }
    }

    private fun checkForQueueEntryAndAddToTransfers() {
        // the hoster is only asked for its transfers and the traffic if there is something to add
        if (queueService.getQueue().isEmpty()) return
        val remoteTorrents = multifileHosterService.remoteTorrents
        val numberOfActiveRemoteTorrents = remoteTorrents.count { torrent: Torrent -> torrent.remoteTransferStatus != TransferStatus.READY_TO_BE_DOWNLOADED }
        val numberOfTorrentsReadyToDownload = remoteTorrents.count { torrent: Torrent -> torrent.remoteTransferStatus == TransferStatus.READY_TO_BE_DOWNLOADED }
        if (numberOfTorrentsReadyToDownload == 0 && numberOfActiveRemoteTorrents < MAX_QUEUE_DOWNLOADS_LIMIT && multifileHosterService.getRemainingTrafficInMB() > MIN_GB_FOR_QUEUE * 1024) {
            queueService.getQueue().stream().findFirst().ifPresent { mediaItem: MediaItem ->
                logger.info("picked {}", mediaItem)
                // items from the search list are already matched, older queue entries are matched now
                val matchedMediaItem = if (isMatched(mediaItem)) {
                    mediaItem
                } else if (mediaItem.year != null) {
                    // the year might be part of the title (e.g. "Space 1999"), so fall back to the full name
                    mediaProxyService.search(mediaItem.title, mediaItem.year).firstOrNull()
                        ?: mediaProxyService.search("${mediaItem.title} ${mediaItem.year}").firstOrNull()
                } else {
                    mediaProxyService.search(mediaItem.title).firstOrNull()
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
                    torrentSearchEngineService.searchTorrents(torrentSearchString).stream()
                        .findFirst()
                        .ifPresent { torrent: Torrent? -> multifileHosterService.addTorrentToTransfer(torrent!!, remoteMediaId, matchedMediaItem?.type) }
                } else {
                    logger.warn("Looks like Torrent was already downloaded, skipped {} - matched files: {}", mediaItem, existingFiles)
                }
                removeFromQueue(mediaItem)
            }
        }
    }

    private fun removeFromQueue(mediaItem: MediaItem) {
        queueService.remove(mediaItem)
        queueService.saveQueue()
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

        private fun isMatched(mediaItem: MediaItem) =
            !mediaItem.id.isNullOrBlank() && mediaItem.type != null && mediaItem.type != MediaType.Other

        const val MIN_GB_FOR_QUEUE = 150
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