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
    private val releaseMatcher = ReleaseMatcher(mediaProxyService)
    private var isRcloneInstalled: Boolean? = null
    private val multifileHosterList: MutableList<MultifileHoster> = mutableListOf(Premiumize(httpHelper))
    private val multifileHosterListForDownloads: MutableList<MultifileHoster> = getEligibleMultifileHoster(httpHelper)

    private val localStatusStorage = ConcurrentHashMap<String, String>()

    @Volatile
    var activeTorrents: List<Torrent> = emptyList()

    private fun getEligibleMultifileHoster(httpHelper: HttpHelper): MutableList<MultifileHoster> {
        val eligibleList = mutableListOf<MultifileHoster>()
        eligibleList.add(Premiumize(httpHelper))
        return eligibleList
    }

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

    fun addTorrentToTransfer(torrent: Torrent, remoteMediaId: String? = null, mediaType: MediaType? = null) {
        val listOfMultiHostersWithTrafficLeft = multifileHosterList.filter { multifileHoster -> multifileHoster.getRemainingTrafficInMB() > 30000 }
        val potentialMultiHosters = listOfMultiHostersWithTrafficLeft.ifEmpty { multifileHosterList }
        val selectedMultiFileHosterSource: MultifileHoster = if (potentialMultiHosters.size == 1) {
            potentialMultiHosters.first()
        } else {
            potentialMultiHosters
                    .minByOrNull { it.getPrio() }
                    ?: potentialMultiHosters.first()
        }
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
                updateTransferStatus()
            }
        }
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
    fun getRemainingTrafficInMB(): Double {
        val cached = cachedRemainingTraffic
        val now = System.nanoTime()
        if (cached != null && now - cached.first < TimeUnit.SECONDS.toNanos(TRAFFIC_CACHE_SECONDS)) {
            return cached.second
        }
        val remainingTraffic = multifileHosterList.sumOf { multifileHoster: MultifileHoster -> multifileHoster.getRemainingTrafficInMB() }
        cachedRemainingTraffic = now to remainingTraffic
        return remainingTraffic
    }

    @Volatile
    private var cachedRemainingTraffic: Pair<Long, Double>? = null

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
                    matchedTransfers.none { it.id == transfer.id } && TransferMatching.transferMatchedTorrentByName(transfer, torrent, maxDiff)
                }?.also { transfer ->
                    updateTransferFromTorrent(transfer, torrent)
                    matchedTransfers.add(transfer)
                    matchedTorrents.add(torrent)
                } ?: also {
                    log.warn("no transfer for torrent found after name match: {}", torrent)
                }
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
                transfer.uri.lowercase().contains(torrent.torrentId.lowercase()) ||
                        transfer.remoteId != null && transfer.remoteId == torrent.remoteId ||
                        TransferMatching.transferMatchedTorrentBySource(transfer, torrent)
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
        if (isRcloneInstalled() && cloudService.isCloudTokenValid && isDownloadInProgress.compareAndSet(false, true)) {
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
                it.remoteId == transferToBeDownloaded.remoteId ||
                        (transferToBeDownloaded.uri.isNotEmpty() && transferToBeDownloaded.uri.lowercase(Locale.ROOT).contains(it.torrentId.lowercase(Locale.ROOT)))
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
            }
            if (!wasDownloadSuccessful) {
                log.error("Couldn't download Transfer: {}", transferToBeDownloaded)
                handleFailedUploadToDrive(transferToBeDownloaded)
            }
        } else {
            val optionalTransfer = transferService.getAll().stream()
                    .filter { transfer: Transfer -> TransferStatus.READY_TO_BE_DOWNLOADED == transfer.transferStatus }.findFirst()
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
        val remainingTrafficInMB: Double = getRemainingTrafficInMB()
        return transferService.getAll()
                .filter { transfer -> TransferStatus.READY_TO_BE_DOWNLOADED == transfer.transferStatus }
                .filter { transfer -> transfer.sizeInBytes == 0L || (transfer.sizeInBytes.toDouble() / 1024.0 / 1024.0) < remainingTrafficInMB }
                .minByOrNull { it.updated }
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

    fun isRcloneInstalled(): Boolean {
        if (isRcloneInstalled == null) {
            isRcloneInstalled = ProcessUtil.isRcloneInstalled
            if (isRcloneInstalled == false) {
                log.error("no rclone found. Downloads not possible")
            }
        }
        return isRcloneInstalled as Boolean
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
        activeTorrents = remoteTorrents
    }

    private fun isReadyForDownloadStatus(status: String?): Boolean {
        return status != null && status.lowercase(Locale.getDefault()).matches(Regex("finished|seeding|ready"))
    }

    private fun updateTorrent(torrentUpdate: Torrent) {
        localStatusStorage[torrentUpdate.torrentId] = torrentUpdate.remoteStatusText
        if (torrentUpdate.remoteId.isNotEmpty()) {
            localStatusStorage[torrentUpdate.remoteId] = torrentUpdate.remoteStatusText
        }
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
        private val FIRST_ADD_TO_HOSTER_RETRY_PAUSE: Duration = Duration.ofMinutes(5)

        /** A rejected transfer is sent again after 5, 10, 20, 40 and 80 minutes. */
        internal fun isServerErrorRetryDue(transfer: Transfer, now: Instant = Instant.now()): Boolean {
            if (transfer.transferStatus != TransferStatus.SERVER_ERROR) return false
            val pause = FIRST_ADD_TO_HOSTER_RETRY_PAUSE.multipliedBy(1L shl (transfer.failedAddsToHoster - 1).coerceIn(0, 10))
            return !transfer.updated.plus(pause).isAfter(now)
        }
        const val TRAFFIC_CACHE_SECONDS = 30L
    }
}
