package boat

import boat.info.CloudFileService
import boat.info.CloudService
import boat.info.MediaItem
import boat.info.QueueService
import boat.model.Transfer
import boat.model.TransferStatus
import boat.multifileHoster.MultifileHosterService
import boat.services.ConfigurationService
import boat.services.TransferService
import boat.torrent.Torrent
import boat.torrent.TorrentHelper.getSearchNameFrom
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
    private val httpHelper: HttpHelper
) {
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
                    TorrentType.values()
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
                logger.warn("rclone not installed")
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
            multifileHosterService.checkForDownloadableTorrents()
        }
    }

    @Async
    @Scheduled(fixedRate = (SECONDS_BETWEEN_VERSION_CHECK * 1000).toLong(), initialDelay = 60 * 5 * 1000)
    fun checkForUpdatedVersionAndShutdownIfUpdateAvailable() {
        logger.info("checkForUpdatedVersionAndShutdown()")
        val pageContent = httpHelper.getPage("https://api.github.com/repos/rarspace01/boat/releases/latest")
        if (!Strings.isEmpty(pageContent)) {
            val jsonRoot = JsonParser.parseString(pageContent)
            val githubVersion = jsonRoot.asJsonObject["name"].asString
            if (!PropertiesHelper.getVersion().contains(githubVersion)) {
                logger.info("Local [{}] != Github [{}]", PropertiesHelper.getVersion(), githubVersion)
                logger.warn("version out of date, shutting down")
                exitProcess(0)
            }
        }
    }

    @Async
    @Scheduled(fixedRate = (SECONDS_BETWEEN_QUEUE_POLLING * 1000).toLong())
    fun checkForQueueEntries() {
        if (cloudService.isCloudTokenValid && configurationService.isSearchMode()) {
            logger.info("checkForQueueEntryAndAddToTransfers")
            checkForQueueEntryAndAddToTransfers()
        }
    }

    private fun checkForQueueEntryAndAddToTransfers() {
        val remoteTorrents = multifileHosterService.remoteTorrents
        val numberOfActiveRemoteTorrents = remoteTorrents.count { torrent: Torrent -> torrent.remoteTransferStatus != TransferStatus.READY_TO_BE_DOWNLOADED }
        val numberOfTorrentsReadyToDownload = remoteTorrents.count { torrent: Torrent -> torrent.remoteTransferStatus == TransferStatus.READY_TO_BE_DOWNLOADED }
        if (numberOfTorrentsReadyToDownload == 0 && numberOfActiveRemoteTorrents < MAX_QUEUE_DOWNLOADS_LIMIT && multifileHosterService.getRemainingTrafficInMB() > MIN_GB_FOR_QUEUE * 1024) {
            queueService.getQueue().stream().findFirst().ifPresent { mediaItem: MediaItem ->
                logger.info("picked {}", mediaItem)
                val searchName = getSearchNameFrom(mediaItem)
                val existingFiles = cloudService.findExistingFiles(searchName)
                if (existingFiles.isEmpty()) {
                    torrentSearchEngineService.searchTorrents(searchName).stream()
                        .findFirst()
                        .ifPresent { torrent: Torrent? -> multifileHosterService.addTorrentToTransfer(torrent!!) }
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
                .filter { transfer: Transfer ->
                    TransferStatus.ERROR == transfer.transferStatus || TransferStatus.DUPLICATE_ERROR == transfer.transferStatus || transfer.updated.isBefore(
                        Instant.now().minus(1, ChronoUnit.DAYS)
                    )
                }
                .forEach { transfer: Transfer? -> transferService.delete(transfer!!) }
        }
    }

    private fun isTorrentStuckOnError(torrent: Torrent): Boolean {
        return torrent.remoteTransferStatus == TransferStatus.ERROR
    }

    companion object {
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