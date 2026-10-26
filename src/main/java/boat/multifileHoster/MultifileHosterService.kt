package boat.multifileHoster

import boat.ContentRenamer
import boat.info.CloudFileService
import boat.info.CloudService
import boat.info.MediaItem
import boat.info.MediaProxyService
import boat.info.MediaType
import boat.mapper.TorrentMapper
import boat.model.Transfer
import boat.model.TransferStatus
import boat.model.TransferType
import boat.rename.LibraryPaths
import boat.rename.ReleaseMatcher
import boat.services.TransferService
import boat.torrent.HttpUser
import boat.torrent.Torrent
import boat.torrent.TorrentFile
import boat.torrent.TorrentHelper
import boat.torrent.TorrentType
import boat.utilities.HttpHelper
import boat.utilities.LoggerDelegate
import boat.utilities.ProcessUtil
import boat.utilities.RcloneUploader
import boat.utilities.PropertiesHelper
import boat.utilities.StringUtil
import jakarta.annotation.PostConstruct
import org.apache.logging.log4j.util.Strings
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.regex.Pattern

@Service
class MultifileHosterService(
        httpHelper: HttpHelper,
        private val transferService: TransferService,
        private val cloudService: CloudService,
        private val cacheManager: CacheManager,
        private val cloudFileService: CloudFileService,
        private val mediaProxyService: MediaProxyService,
        private val contentRenamer: ContentRenamer = ContentRenamer(mediaProxyService),
        private val rcloneUploader: RcloneUploader = RcloneUploader(),
) : HttpUser(httpHelper) {
    private val isDownloadInProgress = AtomicBoolean(false)

    /** Transfers already logged as too big for the free space, so the poll doesn't log them every time. */
    private val transfersWaitingForSpace = ConcurrentHashMap.newKeySet<String>()
    private val releaseMatcher = ReleaseMatcher(mediaProxyService)
    @Volatile
    private var isRcloneInstalled: Boolean? = null
    private var rcloneCheckedAtNanos = 0L
    private val multifileHosterList: List<MultifileHoster> = createMultifileHosters(httpHelper, PropertiesHelper::getProperty)
    private val multifileHosterListForDownloads: List<MultifileHoster> = multifileHosterList

    private val localStatusStorage = ConcurrentHashMap<String, String>()

    @Volatile
    var activeTorrents: List<Torrent> = emptyList()

    fun getCachedStateOfTorrents(returnResults: List<Torrent>): List<Torrent> {
        multifileHosterList.forEach(
                Consumer { multifileHoster: MultifileHoster ->
                    multifileHoster.enrichCacheStateOfTorrents(
                            returnResults
                    )
                }
        )
        return returnResults
    }

    /**
     * A torrent the user picked: to the hoster with the lowest prio among those with enough traffic left. A cached one
     * without seeders can only come from a hoster which has it cached: it goes there if that one has the traffic for
     * it, otherwise it isn't added (false), the caller can queue its title instead.
     */
    fun addTorrentToTransfer(torrent: Torrent, remoteMediaId: String? = null, mediaType: MediaType? = null): Boolean {
        val hoster = if (torrent.cached.isNotEmpty() && torrent.seeder <= 0) {
            cachedHosterWithTrafficFor(torrent, minimumTrafficInMB = 0.0) ?: run {
                log.info("[{}] is cached at {} without seeders, but there is not enough traffic left there: {}",
                        torrent.name, torrent.cached, getRemainingTrafficInMBByHoster())
                return false
            }
        } else {
            selectHosterByPrio(torrent)
        }
        saveTransfer(torrent, hoster, remoteMediaId, mediaType)
        return true
    }

    /** What [addQueuedTorrentToTransfer] did with the torrents found for an entry of the queue. */
    sealed interface QueuedTorrentResult {
        data class Added(val torrent: Torrent, val hoster: String) : QueuedTorrentResult

        /** Only cached torrents without seeders, at hosters without enough traffic: the entry should wait. */
        data object WaitsForTrafficOfCachedHoster : QueuedTorrentResult
        data object NothingDownloadable : QueuedTorrentResult
    }

    /**
     * The best of the [torrents] (sorted, best first) for an entry of the queue, and its hoster:
     * - cached at a hoster with [MIN_TRAFFIC_FOR_CACHED_MB] left (and enough for it): there,
     * - otherwise with seeders: the hoster with the most traffic left, so the queue doesn't use up the traffic of one.
     * A cached torrent without seeders can only come from a hoster which has it cached, so without one that has
     * enough traffic it is skipped for the next.
     */
    fun addQueuedTorrentToTransfer(torrents: List<Torrent>, remoteMediaId: String? = null, mediaType: MediaType? = null): QueuedTorrentResult {
        var waitsForCachedHoster = false
        for (torrent in torrents) {
            val cachedHoster = cachedHosterWithTrafficFor(torrent, MIN_TRAFFIC_FOR_CACHED_MB)
            val hoster = cachedHoster ?: if (torrent.seeder > 0) hosterWithTheMostTraffic() else null
            if (hoster == null) {
                if (torrent.cached.isNotEmpty()) waitsForCachedHoster = true
                log.info("skipped [{}] for the queue: no seeders, and no hoster with it cached has enough traffic (cached at {})", torrent.name, torrent.cached)
                continue
            }
            log.info("queued torrent [{}] goes to {}{} (traffic left: {})", torrent.name, hoster.getName(),
                    if (cachedHoster != null) ", cached there" else "", getRemainingTrafficInMBByHoster())
            saveTransfer(torrent, hoster, remoteMediaId, mediaType)
            return QueuedTorrentResult.Added(torrent, hoster.getName())
        }
        return if (waitsForCachedHoster) QueuedTorrentResult.WaitsForTrafficOfCachedHoster else QueuedTorrentResult.NothingDownloadable
    }

    /** Whether a hoster which has the torrent cached has the traffic for it. */
    fun canTakeCached(torrent: Torrent): Boolean = cachedHosterWithTrafficFor(torrent, minimumTrafficInMB = 0.0) != null

    /** The hoster with the most traffic left among those which have the torrent cached and more than it needs. */
    private fun cachedHosterWithTrafficFor(torrent: Torrent, minimumTrafficInMB: Double): MultifileHoster? {
        if (torrent.cached.isEmpty()) return null
        val trafficByHoster = getRemainingTrafficInMBByHoster()
        val sizeInMB = torrent.getByteSize() / 1024.0 / 1024.0
        return multifileHosterList
                .filter { torrent.cached.contains(it.getName()) }
                .map { it to (trafficByHoster[it.getName()] ?: 0.0) }
                .filter { (_, traffic) -> traffic >= minimumTrafficInMB && traffic > sizeInMB }
                .maxByOrNull { (_, traffic) -> traffic }
                ?.first
    }

    private fun hosterWithTheMostTraffic(): MultifileHoster {
        val trafficByHoster = getRemainingTrafficInMBByHoster()
        return multifileHosterList.maxBy { trafficByHoster[it.getName()] ?: 0.0 }
    }

    private fun selectHosterByPrio(torrent: Torrent): MultifileHoster {
        val trafficByHoster = getRemainingTrafficInMBByHoster()
        val sizeInMB = torrent.getByteSize() / 1024.0 / 1024.0
        // enough for this torrent and some more
        val listOfMultiHostersWithTrafficLeft = multifileHosterList.filter { multifileHoster ->
            val traffic = trafficByHoster[multifileHoster.getName()] ?: 0.0
            traffic > MIN_TRAFFIC_FOR_NEW_TRANSFER_MB && traffic > sizeInMB
        }
        val potentialMultiHosters = listOfMultiHostersWithTrafficLeft.ifEmpty { multifileHosterList }
        return potentialMultiHosters.minByOrNull { it.getPrio() } ?: potentialMultiHosters.first()
    }

    private fun saveTransfer(torrent: Torrent, selectedMultiFileHosterSource: MultifileHoster, remoteMediaId: String?, mediaType: MediaType?) {
        val hosterName = TorrentHelper.extractTorrentName(torrent)
        val mediaItem = remoteMediaId?.let { mediaProxyService.findByRemoteMediaId(it, hosterName, mediaType) }
        val transfer = Transfer(
                name = Transfer.buildName(mediaItem?.title, hosterName),
                transferType = extractType(torrent.magnetUri),
                transferStatus = TransferStatus.ADDED,
                source = selectedMultiFileHosterSource.getName(),
                uri = torrent.magnetUri,
                remoteMediaId = remoteMediaId,
                mediaType = mediaItem?.type ?: mediaType,
                sizeInBytes = torrent.getByteSize()
        )
        transferService.save(transfer)
    }

    private fun extractType(magnetUri: String?): TransferType {
        if (magnetUri?.contains("btih:") == true) {
            return TransferType.TORRENT
        }
        return TransferType.URL
    }

    /**
     * A new process uploads nothing: a transfer still marked as uploading was interrupted by a restart (e.g. the
     * deployment calls `/boat/shutdown`). Without this it would never be uploaded nor matched again.
     */
    @PostConstruct
    fun resumeInterruptedUploads() {
        runCatching {
            transferService.getAll().filter { it.transferStatus == TransferStatus.UPLOADING_TO_DRIVE }.forEach { transfer ->
                log.warn("upload interrupted by a restart, uploading again: {}", transfer)
                transfer.transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED
                transfer.progressInPercentage = 0.0
                transfer.eta = Duration.ZERO
                transfer.feedbackMessage = "upload interrupted by a restart"
                transferService.save(transfer)
            }
        }.onFailure { log.error("could not check for interrupted uploads: {}", it.message) }
    }

    @Synchronized
    fun addTransfersToDownloadQueue() {
        // filter for traffic left
        val transfersToBeDownloaded = transferService.getAll()
                .filter { transfer -> TransferStatus.ADDED == transfer.transferStatus || isServerErrorRetryDue(transfer) }
                .filter { transfer -> multifileHosterListForDownloads.any { multifileHoster -> multifileHoster.getName() == transfer.source } }
        multifileHosterListForDownloads.forEach { multifileHoster ->
            val transfersForHoster = transfersToBeDownloaded.filter { transfer -> transfer.source == multifileHoster.getName() }
            transfersForHoster.forEach { transfer ->
                val addTransferResult = multifileHoster.addTorrentToDownloadQueue(TorrentMapper.mapTransferToTorrent(transfer))
                val addTorrentToQueueMessage = addTransferResult.message
                transfer.feedbackMessage = addTorrentToQueueMessage
                if (addTorrentToQueueMessage.contains("error")) {
                    log.error("addTorrentToQueueMessage error: {} for transfer {}", addTorrentToQueueMessage, transfer)
                    if (addTorrentToQueueMessage.contains("You already added this job")) {
                        transfer.transferStatus = TransferStatus.ERROR
                    } else {
                        handleRejectedByHoster(transfer)
                    }
                } else {
                    transfer.failedAddsToHoster = 0
                    transfer.remoteId = addTransferResult.remoteId ?: extractRemoteIdFromMessage(transfer)
                    transfer.transferStatus = TransferStatus.ADDED_TO_MULTIHOSTER
                }
                transferService.save(transfer)
            }
        }
        // once for all added transfers, it asks the hoster for its whole list
        if (transfersToBeDownloaded.isNotEmpty()) updateTransferStatus()
    }

    fun extractRemoteIdFromMessage(transfer: Transfer): String? {
        if (Strings.isNotEmpty(transfer.feedbackMessage)) {
            val pattern = Pattern.compile("\"id\":\"([^\"]+)\"")
            transfer.feedbackMessage?.let {
                val matcher = pattern.matcher(it)
                if (matcher.find()) {
                    return matcher.group(1)
                }
            }

        }
        return null
    }

    val remoteTorrents: List<Torrent>
        get() = multifileHosterListForDownloads
                .flatMap { multifileHoster: MultifileHoster -> multifileHoster.getRemoteTorrents() }

    private fun isSingleFileDownload(torrentFiles: List<TorrentFile>): Boolean {
        var sumFileSize = 0L
        var biggestFileYet = 0L
        for (torrentFile in torrentFiles) {
            if (torrentFile.filesize > biggestFileYet) {
                biggestFileYet = torrentFile.filesize
            }
            sumFileSize += torrentFile.filesize
        }
        // if maxfilesize >90% sumSize --> Singlefile
        return biggestFileYet > 0.9 * sumFileSize
    }

    /**
     * Asking the multifilehosters costs HTTP calls and this is needed very often, so the sum is reused for
     * [TRAFFIC_CACHE_SECONDS].
     */
    fun getRemainingTrafficInMB(): Double = getRemainingTrafficInMBByHoster().values.sum()

    /** The traffic left at each multifilehoster by its name, reused for [TRAFFIC_CACHE_SECONDS]. */
    fun getRemainingTrafficInMBByHoster(): Map<String, Double> {
        val cached = cachedRemainingTraffic
        val now = System.nanoTime()
        if (cached != null && now - cached.first < TimeUnit.SECONDS.toNanos(TRAFFIC_CACHE_SECONDS)) {
            return cached.second
        }
        val remainingTraffic = multifileHosterList.associate { multifileHoster -> multifileHoster.getName() to multifileHoster.getRemainingTrafficInMB() }
        cachedRemainingTraffic = now to remainingTraffic
        return remainingTraffic
    }

    @Volatile
    private var cachedRemainingTraffic: Pair<Long, Map<String, Double>>? = null

    private fun getFilesFromTorrent(torrentToBeDownloaded: Torrent): List<TorrentFile> {
        val hoster = multifileHosterList.firstOrNull { multifileHoster: MultifileHoster -> multifileHoster.getName() == torrentToBeDownloaded.source }
        return if (hoster != null) {
            hoster.getFilesFromTorrent(torrentToBeDownloaded)
        } else {
            log.error("no MFH Files present to be downloaded")
            ArrayList()
        }
    }

    private fun getMainFileURLFromTorrent(torrentFiles: List<TorrentFile>): TorrentFile {
        // iterate over and check for One File Torrent
        var biggestFileYet: TorrentFile = torrentFiles[0]
        for (torrentFile in torrentFiles) {
            if (torrentFile.filesize > biggestFileYet.filesize) {
                biggestFileYet = torrentFile
            }
        }
        return biggestFileYet
    }

    fun delete(torrent: Torrent) {
        val hoster = multifileHosterList.firstOrNull { multifileHoster: MultifileHoster -> multifileHoster.getName() == torrent.source }
        if (hoster != null) {
            log.info("Deleting torrent: {}", torrent)
            hoster.delete(torrent)
        } else {
            log.error("Deletion of Torrent not possible: {}", torrent.toString())
        }
    }

    /**
     * Deletes a transfer and its torrent at the multifilehoster. Returns a message for the user.
     */
    fun deleteTransfer(transferId: String): String {
        val transfer = transferService.getById(transferId) ?: return "transfer not found"
        if (transfer.transferStatus == TransferStatus.UPLOADING_TO_DRIVE) {
            return "${transfer.name} is uploading to the drive, try again when it's done"
        }
        findRemoteTorrent(transfer)?.let { delete(it) }
        transferService.delete(transfer)
        return "deleted ${transfer.name}"
    }

    /**
     * Restarts a transfer: if the multifilehoster still has the files ready, they are uploaded to the drive again,
     * otherwise the torrent is removed at the multifilehoster and added again. Returns a message for the user.
     */
    fun restartTransfer(transferId: String): String {
        val transfer = transferService.getById(transferId) ?: return "transfer not found"
        if (transfer.transferStatus == TransferStatus.UPLOADING_TO_DRIVE) {
            return "${transfer.name} is uploading to the drive, try again when it's done"
        }
        val remoteTorrent = findRemoteTorrent(transfer)
        transfer.failedUploadsToDrive = 0
        transfer.failedAddsToHoster = 0
        transfer.progressInPercentage = 0.0
        transfer.eta = Duration.ZERO
        val message = if (remoteTorrent?.remoteTransferStatus == TransferStatus.READY_TO_BE_DOWNLOADED) {
            transfer.transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED
            "restarted ${transfer.name}, uploading to the drive again"
        } else {
            remoteTorrent?.let { delete(it) }
            transfer.remoteId = null
            transfer.transferStatus = TransferStatus.ADDED
            "restarted ${transfer.name}, adding to ${transfer.source} again"
        }
        transfer.feedbackMessage = message
        transferService.save(transfer)
        return message
    }

    private fun findRemoteTorrent(transfer: Transfer): Torrent? {
        val hoster = multifileHosterList.firstOrNull { it.getName() == transfer.source } ?: return null
        val infoHash = Torrent.of(transfer.uri).infoHash
        return hoster.getRemoteTorrents().firstOrNull { remoteTorrent ->
            (!transfer.remoteId.isNullOrEmpty() && remoteTorrent.remoteId == transfer.remoteId) ||
                    (infoHash != null && remoteTorrent.infoHash == infoHash)
        }
    }

    fun getActiveMultifileHosters(): List<MultifileHoster> {
        return multifileHosterList
    }

    fun getActiveMultifileHosterForDownloads(): List<MultifileHoster> {
        return multifileHosterListForDownloads
    }

    fun updateTransferStatus() {
        val transfers = transferService.getAll()
                .filter { transfer -> multifileHosterListForDownloads.any { multifileHoster -> multifileHoster.getName() == transfer.source } }
                // the multifilehoster still reports these as ready, which would start the download again
                .filter { transfer -> transfer.transferStatus !in listOf(TransferStatus.UPLOADING_TO_DRIVE, TransferStatus.UPLOADED, TransferStatus.UPLOAD_TO_DRIVE_FAILED) }
        val matchedTransfers = mutableListOf<Transfer>()
        val matchedTorrents = mutableListOf<Torrent>()
        val torrentsForDownload = remoteTorrents
        matchTransfersAndTorrentsByIdAndUpdateStatus(torrentsForDownload, transfers, matchedTransfers, matchedTorrents)

        fun unmatchedTransfers() = transfers.filter { matchedTransfers.none { matchedTransfer -> matchedTransfer.id == it.id } }
        fun unmatchedTorrents() = torrentsForDownload.filter { matchedTorrents.none { matchedTorrent -> matchedTorrent.torrentId == it.torrentId } }

        // the names must be the same first, then up to two words may differ
        for (maxDiff in listOf(0, 2)) {
            if (unmatchedTorrents().isEmpty() || unmatchedTransfers().isEmpty()) break
            unmatchedTorrents().forEach { torrent ->
                transfers.find { transfer ->
                    matchedTransfers.none { it.id == transfer.id } && TransferMatching.isAtTheHosterOf(transfer, torrent) &&
                            TransferMatching.transferMatchedTorrentByName(transfer, torrent, maxDiff)
                }?.also { transfer ->
                    updateTransferFromTorrent(transfer, torrent)
                    matchedTransfers.add(transfer)
                    matchedTorrents.add(torrent)
                } ?: also {
                    log.warn("no transfer for torrent found after name match: {}", torrent)
                }
            }
        }
        unmatchedTorrents().forEach { torrent ->
            // the transfers which are left out on purpose, so the log tells why a torrent has none
            transferService.getAll().filter { it !in transfers && it.remoteId != null && it.remoteId == torrent.remoteId && TransferMatching.isAtTheHosterOf(it, torrent) }.forEach {
                log.warn("torrent {} belongs to transfer {} in status {}", torrent.name, it.name, it.transferStatus)
            }
        }
        if (unmatchedTransfers().isNotEmpty() || unmatchedTorrents().isNotEmpty()) {
            log.warn("listOfUnmatchedTransfers: [{}]", unmatchedTransfers())
            log.warn("listOfUnmatchedTorrents: [{}]", unmatchedTorrents())
        }
    }

    private fun updateTransferFromTorrent(transfer: Transfer, torrent: Torrent) {
        transfer.transferStatus = torrent.remoteTransferStatus
        transfer.progressInPercentage = torrent.remoteProgressInPercent
        transfer.updateHosterName(TorrentHelper.resolveDownloadName(torrent, transfer.hosterName()))
        transfer.eta = torrent.eta
        transfer.remoteId = torrent.remoteId
        transferService.save(transfer)
    }

    private fun matchTransfersAndTorrentsByIdAndUpdateStatus(
            torrentsForDownload: List<Torrent>,
            transfers: List<Transfer>,
            matchedTransfers: MutableList<Transfer>,
            matchedTorrents: MutableList<Torrent>
    ) {
        torrentsForDownload.forEach { torrent ->
            transfers.find { transfer ->
                TransferMatching.isAtTheHosterOf(transfer, torrent) && (
                        transfer.uri.lowercase().contains(torrent.torrentId.lowercase()) ||
                        transfer.remoteId != null && transfer.remoteId == torrent.remoteId ||
                        TransferMatching.transferMatchedTorrentBySource(transfer, torrent))
            }
                    ?.also { transfer ->
                        updateTransferFromTorrent(transfer, torrent)
                        matchedTransfers.add(transfer)
                        matchedTorrents.add(torrent)
                    } ?: also {
                log.warn("no transfer for torrent found: {}", torrent)
            }
        }
    }

    fun checkForDownloadableTorrentsAndDownload() {
        if (isRcloneInstalled() && cloudService.isTargetFolderReady && isDownloadInProgress.compareAndSet(false, true)) {
            try {
                checkForDownloadableTorrentsAndDownloadTheFirst()
            } finally {
                isDownloadInProgress.set(false)
            }
        }
    }

    internal fun checkForDownloadableTorrentsAndDownloadTheFirst() {
        val transferToBeDownloaded = getTransferToBeDownloaded()
        if (transferToBeDownloaded != null) {
            val remoteTorrentsList = remoteTorrents
            val remoteTorrent = remoteTorrentsList.firstOrNull {
                TransferMatching.isAtTheHosterOf(transferToBeDownloaded, it) && (it.remoteId == transferToBeDownloaded.remoteId ||
                        (transferToBeDownloaded.uri.isNotEmpty() && transferToBeDownloaded.uri.lowercase(Locale.ROOT).contains(it.torrentId.lowercase(Locale.ROOT))))
            } ?: TorrentMapper.mapTransferToTorrent(transferToBeDownloaded)

            if (remoteTorrent.source.isEmpty()) {
                remoteTorrent.source = transferToBeDownloaded.source
            }
            if (remoteTorrent.remoteId.isEmpty()) {
                remoteTorrent.remoteId = transferToBeDownloaded.remoteId ?: ""
            }

            var wasDownloadSuccessful = false
            try {
                val mediaItem = (transferToBeDownloaded.remoteMediaId?.let {
                    mediaProxyService.findByRemoteMediaId(it, transferToBeDownloaded.hosterName(), transferToBeDownloaded.mediaType)
                }
                    ?: transferToBeDownloaded.remoteMediaId?.let { mediaProxyService.search(it, transferToBeDownloaded.mediaType).firstOrNull() }
                    // without an id only a confident match of name and year, otherwise the download keeps its own name
                    ?: releaseMatcher.match(transferToBeDownloaded.hosterName(), transferToBeDownloaded.mediaType))

                val filesFromTorrent = getFilesFromTorrent(remoteTorrent)
                if (filesFromTorrent.isEmpty()) {
                    // might be a temporary hoster failure, so it is retried like a failed upload, but not forever
                    log.warn("No files found to download for transfer: {}", transferToBeDownloaded)
                    handleFailedUploadToDrive(transferToBeDownloaded, "no files found at the multifilehoster")
                    return
                }

                val basePath = PropertiesHelper.requireProperty(CloudService.RCLONE_DIR)
                val listOfCharactersToIndexUpdate = mutableSetOf<String>()

                if (isSingleFileDownload(filesFromTorrent)) {
                    log.info("SFD - {}", transferToBeDownloaded)
                    val fileToDownload: TorrentFile = getMainFileURLFromTorrent(filesFromTorrent)
                    updateUploadStatus(transferToBeDownloaded, remoteTorrent, listOf(fileToDownload), 0, null)

                    val file = File(if (!fileToDownload.name.isNullOrBlank()) fileToDownload.name!! else DownloadFileNames.extractFileNameFromUrl(fileToDownload.url))
                    val targetFilePath: String
                    val indexLetter: String

                    if (mediaItem != null) {
                        val mediaTarget = buildMediaTargetFile(file, mediaItem, basePath)
                        targetFilePath = mediaTarget.first
                        indexLetter = mediaTarget.second
                    } else {
                        val destination = cloudService.buildDestinationPath(remoteTorrent.name, listOf(fileToDownload))
                        targetFilePath = destination.second + LibraryPaths.getSafeName(DownloadFileNames.buildFilename(remoteTorrent.name, fileToDownload.url))
                        indexLetter = destination.first
                    }

                    listOfCharactersToIndexUpdate.add(indexLetter)
                    wasDownloadSuccessful = rcloneUploader.copyUrl(fileToDownload.url, targetFilePath, fileToDownload.filesize)
                    updateUploadStatus(transferToBeDownloaded, remoteTorrent, listOf(fileToDownload), 1, null)
                } else {
                    log.info("MFD - {}", transferToBeDownloaded)
                    var currentFileNumber = 0
                    var failedUploads = 0
                    val startTime = Instant.now()

                    for (torrentFile in filesFromTorrent) {
                        updateUploadStatus(transferToBeDownloaded, remoteTorrent, filesFromTorrent, currentFileNumber, startTime)
                        val file = File(if (!torrentFile.name.isNullOrBlank()) torrentFile.name!! else DownloadFileNames.extractFileNameFromUrl(torrentFile.url))
                        val targetFilePath: String
                        val indexLetter: String

                        if (mediaItem != null) {
                            val mediaTarget = buildMediaTargetFile(file, mediaItem, basePath)
                            targetFilePath = mediaTarget.first
                            indexLetter = mediaTarget.second
                        } else {
                            val destination = cloudService.buildDestinationPath(remoteTorrent.name, filesFromTorrent)
                            val destinationPath: String = destination.second
                            // the names come from the torrent, a "../" in them must not write elsewhere in the remote
                            val safeFileName = LibraryPaths.getSafeRelativePath(torrentFile.name ?: "", LibraryPaths.getSafeName(DownloadFileNames.extractFileNameFromUrl(torrentFile.url)))
                            targetFilePath = if (destinationPath.contains(TorrentType.SERIES_SHOWS.type)) {
                                destinationPath + safeFileName
                            } else {
                                destinationPath + LibraryPaths.getSafeName(remoteTorrent.name) + "/" + safeFileName
                            }
                            indexLetter = destination.first
                        }

                        listOfCharactersToIndexUpdate.add(indexLetter)
                        if (!rcloneUploader.copyUrl(torrentFile.url, targetFilePath, torrentFile.filesize)) {
                            failedUploads++
                        }
                        currentFileNumber++
                        updateUploadStatus(transferToBeDownloaded, remoteTorrent, filesFromTorrent, currentFileNumber, startTime)
                    }

                    wasDownloadSuccessful = failedUploads == 0
                }

                // the files are only removed from the hoster once all of them are in the drive
                if (wasDownloadSuccessful) {
                    delete(remoteTorrent)
                    transferService.delete(transferToBeDownloaded)
                }
                updateIndexForLetters(listOfCharactersToIndexUpdate)
            } catch (exception: Exception) {
                log.error(String.format("Couldn't download Transfer: %s", transferToBeDownloaded), exception)
            } finally {
                // the download used some of it, the next one is checked against the space which is left now
                cloudService.forgetFreeSpace()
            }
            if (!wasDownloadSuccessful) {
                log.error("Couldn't download Transfer: {}", transferToBeDownloaded)
                handleFailedUploadToDrive(transferToBeDownloaded)
            }
        } else {
            val optionalTransfer = transferService.getAll().stream()
                    .filter { transfer: Transfer -> TransferStatus.READY_TO_BE_DOWNLOADED == transfer.transferStatus }
                    // those already told they wait for space
                    .filter { transfer: Transfer -> transfer.id !in transfersWaitingForSpace }.findFirst()
            optionalTransfer.ifPresent { transfer: Transfer? ->
                log.error(
                        "According to State we could download but doesn't exist: {}",
                        transfer
                )
            }
        }
    }

    /**
     * A transfer the hoster rejects is sent again later, with a growing pause ([isServerErrorRetryDue]). After
     * [MAX_ADD_TO_HOSTER_RETRIES] tries it is an error, the cleanup removes it.
     */
    private fun handleRejectedByHoster(transfer: Transfer) {
        transfer.failedAddsToHoster++
        if (transfer.failedAddsToHoster > MAX_ADD_TO_HOSTER_RETRIES) {
            transfer.transferStatus = TransferStatus.ERROR
            transfer.feedbackMessage = "${transfer.feedbackMessage}, rejected by the hoster ${transfer.failedAddsToHoster} times, gave up"
            log.error("giving up on transfer after {} rejections by the hoster: {}", transfer.failedAddsToHoster, transfer)
        } else {
            transfer.transferStatus = TransferStatus.SERVER_ERROR
        }
    }

    /**
     * Every download from the multifilehoster costs traffic, so a transfer whose upload to the drive failed is only
     * retried [MAX_UPLOAD_TO_DRIVE_RETRIES] times, after the other waiting transfers (they are picked by their last
     * update). After that it is marked as failed and its files stay on the multifilehoster.
     */
    private fun handleFailedUploadToDrive(transfer: Transfer, reason: String = "upload to drive failed") {
        val currentTransfer = transferService.getById(transfer.id) ?: transfer
        currentTransfer.failedUploadsToDrive++
        currentTransfer.progressInPercentage = 0.0
        currentTransfer.eta = Duration.ZERO
        if (currentTransfer.failedUploadsToDrive > MAX_UPLOAD_TO_DRIVE_RETRIES) {
            currentTransfer.transferStatus = TransferStatus.UPLOAD_TO_DRIVE_FAILED
            currentTransfer.feedbackMessage = "$reason ${currentTransfer.failedUploadsToDrive} times, files kept on the multifilehoster"
            log.error("giving up on transfer after {} failed uploads to drive: {}", currentTransfer.failedUploadsToDrive, currentTransfer)
        } else {
            currentTransfer.transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED
            currentTransfer.feedbackMessage = "$reason, retry ${currentTransfer.failedUploadsToDrive}/$MAX_UPLOAD_TO_DRIVE_RETRIES"
        }
        transferService.save(currentTransfer)
    }

    /**
     * Builds the target path and index letter for a file of a known media item.
     */
    private fun buildMediaTargetFile(file: File, mediaItem: MediaItem, basePath: String): Pair<String, String> {
        val safeTitle = contentRenamer.getSafeTitle(mediaItem)
        val (season, episode) = contentRenamer.extractSeasonAndEpisode(file, mediaItem)
        val targetFilePath = contentRenamer.getTargetFile(file, mediaItem, safeTitle, season, episode, basePath)
        return Pair(targetFilePath, StringUtil.deductFirstTorrentLetter(safeTitle))
    }

    fun getTransferToBeDownloaded(): Transfer? {
        val readyTransfers = transferService.getAll()
                .filter { transfer -> TransferStatus.READY_TO_BE_DOWNLOADED == transfer.transferStatus }
        // the traffic of the hoster which has the transfer, the others' doesn't help
        val trafficByHoster = if (readyTransfers.any { it.sizeInBytes != 0L }) getRemainingTrafficInMBByHoster() else emptyMap()
        val transfersWithTrafficLeft = readyTransfers
                .filter { transfer -> transfer.sizeInBytes == 0L || (transfer.sizeInBytes.toDouble() / 1024.0 / 1024.0) < (trafficByHoster[transfer.source] ?: 0.0) }
        // only if the remote tells its free space, otherwise the download is tried like before
        val freeSpaceInBytes = if (transfersWithTrafficLeft.isEmpty()) null else cloudService.freeSpaceInBytes
        return transfersWithTrafficLeft
                .filter { transfer -> fitsIntoFreeSpace(transfer, freeSpaceInBytes) }
                .minByOrNull { it.updated }
    }

    private fun fitsIntoFreeSpace(transfer: Transfer, freeSpaceInBytes: Long?): Boolean {
        val fits = freeSpaceInBytes == null || transfer.sizeInBytes == 0L || transfer.sizeInBytes < freeSpaceInBytes
        if (fits) {
            transfersWaitingForSpace.remove(transfer.id)
        } else if (transfersWaitingForSpace.add(transfer.id)) {
            log.warn(
                "transfer needs {}, only {} left in the target folder, waits for space: {}",
                TorrentHelper.humanReadableByteCountBinary(transfer.sizeInBytes),
                TorrentHelper.humanReadableByteCountBinary(freeSpaceInBytes!!),
                transfer.hosterName()
            )
        }
        return fits
    }

    private fun updateUploadStatus(
            transferToBeDownloaded: Transfer,
            torrentToBeDownloaded: Torrent,
            listOfFiles: List<TorrentFile>,
            currentFileNumber: Int,
            startTime: Instant?
    ) {
        val transfer = transferService.getById(transferToBeDownloaded.id)
        if (transfer != null) {
            transfer.transferStatus = TransferStatus.UPLOADING_TO_DRIVE
            transfer.progressInPercentage = currentFileNumber.toDouble() / listOfFiles.size.toDouble()
            transfer.eta = UploadProgress.getUploadDuration(listOfFiles, currentFileNumber, startTime)
            transferService.save(transfer)
        } else {
            log.warn("couldn't update transfer: $transferToBeDownloaded")
        }
        torrentToBeDownloaded.remoteStatusText = UploadProgress.getUploadStatusString(
                torrentToBeDownloaded, listOfFiles, currentFileNumber,
                startTime
        )
        updateTorrent(torrentToBeDownloaded)
    }

    /** Found once is enough, a missing rclone is looked for again every few minutes (it might be installed later). */
    fun isRcloneInstalled(): Boolean {
        if (isRcloneInstalled == true) return true
        val now = System.nanoTime()
        if (isRcloneInstalled == false && now - rcloneCheckedAtNanos < TimeUnit.MINUTES.toNanos(RCLONE_CHECK_MINUTES)) return false
        rcloneCheckedAtNanos = now
        isRcloneInstalled = ProcessUtil.isRcloneInstalled
        if (isRcloneInstalled == false) {
            log.error("no rclone found. Downloads not possible")
        }
        return isRcloneInstalled == true
    }

    fun refreshTorrents() {
        val remoteTorrents = remoteTorrents
        remoteTorrents.forEach { torrent: Torrent ->
            if (isReadyForDownloadStatus(torrent.remoteStatusText)) {
                val localStatus = localStatusStorage[torrent.torrentId]
                torrent.remoteStatusText = localStatus ?: torrent.remoteStatusText
            } else {
                localStatusStorage.remove(torrent.torrentId)
            }
        }
        // the torrents which are gone from the hoster (uploaded and deleted); an empty list might be a failure of the hoster
        if (remoteTorrents.isNotEmpty()) localStatusStorage.keys.retainAll(remoteTorrents.map { it.torrentId }.toSet())
        activeTorrents = remoteTorrents
    }

    private fun isReadyForDownloadStatus(status: String?): Boolean {
        return status != null && status.lowercase(Locale.getDefault()).matches(Regex("finished|seeding|ready"))
    }

    /** The upload progress, shown instead of the status of the hoster, by the info hash. */
    private fun updateTorrent(torrentUpdate: Torrent) {
        localStatusStorage[torrentUpdate.torrentId] = torrentUpdate.remoteStatusText
    }

    /**
     * Refreshes the files cache of the given index letters, e.g. "A" or "0-9" as given by [StringUtil.deductFirstTorrentLetter].
     */
    fun updateIndexForLetters(indexLetters: Collection<String>) {
        log.info("refreshCloudFileServiceCache for letters: $indexLetters")
        val filesCache = cacheManager.getCache("filesCache")
        indexLetters.filter { it.isNotBlank() }.distinct().forEach { searchChar: String ->
            TorrentType.entries
                    .forEach { torrentType: TorrentType ->
                        val destinationPath = cloudService
                                .buildDestinationPathWithTypeOfMediaWithoutSubFolders(searchChar, torrentType)
                        filesCache?.evictIfPresent(destinationPath)
                        cloudFileService.getFilesInPath(destinationPath)
                    }
            log.info("Cache refresh done for: $searchChar")
        }
    }


    companion object {
        private val log by LoggerDelegate()
        const val MAX_UPLOAD_TO_DRIVE_RETRIES = 2
        const val MAX_ADD_TO_HOSTER_RETRIES = 5
        private const val RCLONE_CHECK_MINUTES = 5L
        private val FIRST_ADD_TO_HOSTER_RETRY_PAUSE: Duration = Duration.ofMinutes(5)

        /** A rejected transfer is sent again after 5, 10, 20, 40 and 80 minutes. */
        internal fun isServerErrorRetryDue(transfer: Transfer, now: Instant = Instant.now()): Boolean {
            if (transfer.transferStatus != TransferStatus.SERVER_ERROR) return false
            val pause = FIRST_ADD_TO_HOSTER_RETRY_PAUSE.multipliedBy(1L shl (transfer.failedAddsToHoster - 1).coerceIn(0, 10))
            return !transfer.updated.plus(pause).isAfter(now)
        }
        const val TRAFFIC_CACHE_SECONDS = 30L

        /** A new transfer goes to a multifilehoster with more traffic left than this (and than the torrent needs). */
        private const val MIN_TRAFFIC_FOR_NEW_TRANSFER_MB = 30000.0

        /** A queued torrent cached at a hoster goes there only with this much traffic left there (150 GB). */
        private const val MIN_TRAFFIC_FOR_CACHED_MB = 150.0 * 1024

        /**
         * Premiumize, and AllDebrid if `ALLDEBRID_APIKEY` is set. A transfer the user adds goes to the hoster with the
         * lowest [MultifileHoster.getPrio] among those with traffic left, so AllDebrid (prio 2) only gets those when
         * Premiumize runs out; without a subscription it has no traffic and gets none. A queued one goes by traffic,
         * see [addQueuedTorrentToTransfer].
         */
        internal fun createMultifileHosters(httpHelper: HttpHelper, lookup: (String) -> String?): List<MultifileHoster> =
            listOfNotNull(
                Premiumize(httpHelper),
                Alldebrid(httpHelper).takeIf { !lookup("ALLDEBRID_APIKEY").isNullOrBlank() },
            )
    }
}
