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
import org.springframework.util.StringUtils
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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
    private var isRcloneInstalled: Boolean? = null
    private val multifileHosterList: MutableList<MultifileHoster> = mutableListOf(Premiumize(httpHelper))//, Alldebrid(httpHelper))
    private val multifileHosterListForDownloads: MutableList<MultifileHoster> = getEligibleMultifileHoster(httpHelper)

    private val localStatusStorage = ConcurrentHashMap<String, String>()

    @Volatile
    var activeTorrents: List<Torrent> = emptyList()

    private fun getEligibleMultifileHoster(httpHelper: HttpHelper): MutableList<MultifileHoster> {
        val eligibleList = mutableListOf<MultifileHoster>()
        eligibleList.add(Premiumize(httpHelper))
        //eligibleList.add(Alldebrid(httpHelper))
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
                .filter { transfer -> TransferStatus.ADDED == transfer.transferStatus || TransferStatus.SERVER_ERROR == transfer.transferStatus }
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
                        transfer.transferStatus = TransferStatus.SERVER_ERROR
                    }
                } else {
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

    val remoteTorrentsForDownload: List<Torrent>
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

    private fun getSizeOfTorrentInMB(torrent: Torrent): Double {
        val size: Long = getFilesFromTorrent(torrent).sumOf { torrentFile: TorrentFile -> torrentFile.filesize }
        return size.toDouble() / 1024.0 / 1024.0
    }

    fun getRemainingTrafficInMB(): Double {
        return multifileHosterList.sumOf { multifileHoster: MultifileHoster -> multifileHoster.getRemainingTrafficInMB() }
    }

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
        var listOfUnmatchedTransfers = transfers.filter { matchedTransfers.none { matchedTransfer -> matchedTransfer.id.equals(it.id) } }
        var listOfUnmatchedTorrents = torrentsForDownload.filter { matchedTorrents.none { matchedTorrent -> matchedTorrent.torrentId == (it.torrentId) } }
        if (listOfUnmatchedTorrents.isNotEmpty() && listOfUnmatchedTransfers.isNotEmpty()) {
            listOfUnmatchedTorrents.forEach { torrent ->
                transfers.find { transfer ->
                    transferMatchedTorrentByName(transfer, torrent, 0)
                }?.also { transfer ->
                    transfer.transferStatus = torrent.remoteTransferStatus
                    transfer.progressInPercentage = torrent.remoteProgressInPercent
                    transfer.updateHosterName(TorrentHelper.resolveDownloadName(torrent, transfer.hosterName()))
                    transfer.eta = torrent.eta
                    transfer.remoteId = torrent.remoteId
                    transferService.save(transfer)
                    matchedTransfers.add(transfer)
                    matchedTorrents.add(torrent)
                } ?: also {
                    log.warn("no transfer for torrent found after name match: {}", torrent)
                }
            }
            listOfUnmatchedTransfers = transfers.filter { matchedTransfers.none { matchedTransfer -> matchedTransfer.id.equals(it.id) } }
            listOfUnmatchedTorrents =
                    torrentsForDownload.filter { matchedTorrents.none { matchedTorrent -> matchedTorrent.torrentId == (it.torrentId) } }

            if (listOfUnmatchedTorrents.isNotEmpty() && listOfUnmatchedTransfers.isNotEmpty()) {
                listOfUnmatchedTorrents.forEach { torrent ->
                    transfers.find { transfer ->
                        transferMatchedTorrentByName(transfer, torrent, 2)
                    }?.also { transfer ->
                        transfer.transferStatus = torrent.remoteTransferStatus
                        transfer.progressInPercentage = torrent.remoteProgressInPercent
                        transfer.updateHosterName(TorrentHelper.resolveDownloadName(torrent, transfer.hosterName()))
                        transfer.eta = torrent.eta
                        transfer.remoteId = torrent.remoteId
                        transferService.save(transfer)
                        matchedTransfers.add(transfer)
                        matchedTorrents.add(torrent)
                    } ?: also {
                        log.warn("no transfer for torrent found after name match: {}", torrent)
                    }
                }
                listOfUnmatchedTransfers = transfers.filter { matchedTransfers.none { matchedTransfer -> matchedTransfer.id.equals(it.id) } }
                listOfUnmatchedTorrents =
                        torrentsForDownload.filter { matchedTorrents.none { matchedTorrent -> matchedTorrent.torrentId == (it.torrentId) } }
            }
        }
        if (listOfUnmatchedTransfers.isNotEmpty() || listOfUnmatchedTorrents.isNotEmpty()) {
            log.warn("listOfUnmatchedTransfers: [{}]", listOfUnmatchedTransfers)
            log.warn("listOfUnmatchedTorrents: [{}]", listOfUnmatchedTorrents)
        }
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
                        transferMatchedTorrentBySource(transfer, torrent)
            }
                    ?.also { transfer ->
                        transfer.transferStatus = torrent.remoteTransferStatus
                        transfer.progressInPercentage = torrent.remoteProgressInPercent
                        transfer.updateHosterName(TorrentHelper.resolveDownloadName(torrent, transfer.hosterName()))
                        transfer.eta = torrent.eta
                        transfer.remoteId = torrent.remoteId
                        transferService.save(transfer)
                        matchedTransfers.add(transfer)
                        matchedTorrents.add(torrent)
                    } ?: also {
                log.warn("no transfer for torrent found: {}", torrent)
            }
        }
    }

    private fun transferMatchedTorrentBySource(transfer: Transfer, torrent: Torrent): Boolean {
        return Strings.isNotEmpty(transfer.uri) && transfer.uri == torrent.magnetUri
    }

    fun transferMatchedTorrentByName(transfer: Transfer, torrent: Torrent, maxDiff: Int = 0): Boolean {
        val transferName = TorrentHelper.getNormalizedTorrentStringWithSpaces(transfer.hosterName()).lowercase()
        val torrentName = TorrentHelper.getNormalizedTorrentStringWithSpaces(torrent.name).lowercase()
        val matchedByName = transfer.hosterName().isNotEmpty() && transferName.lowercase() == torrentName.lowercase()
        return if (matchedByName) {
            log.warn("transfer only matched by name: {} <-> {}", transfer, torrent)
            true
        } else {
            val transferWordList = transferName.split(" ")
            val torrentWordList = torrentName.split(" ")
            val transferInTorrentCount = transferWordList.count { torrentWordList.contains(it) }
            val torrentInTransferCount = torrentWordList.count { transferWordList.contains(it) }
            val transferDiffCount = transferWordList.size - transferInTorrentCount
            val torrentDiffCount = torrentWordList.size - torrentInTransferCount
            (
                    (transferInTorrentCount.toDouble() / transferWordList.count().toDouble() > 0.75) && transferDiffCount <= maxDiff &&
                            (torrentInTransferCount.toDouble() / torrentWordList.count().toDouble() > 0.75) && torrentDiffCount <= maxDiff
                    )
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
                    ?: mediaProxyService.search(transferToBeDownloaded.hosterName(), transferToBeDownloaded.mediaType).firstOrNull())

                val filesFromTorrent = getFilesFromTorrent(remoteTorrent)
                if (filesFromTorrent.isEmpty()) {
                    // might be a temporary hoster failure, the transfer stays ready to be downloaded
                    log.warn("No files found to download for transfer: {}", transferToBeDownloaded)
                    return
                }

                val basePath = PropertiesHelper.getProperty(CloudService.RCLONE_DIR) ?: (System.getProperty("user.home") + "/pfdb/PFDB")
                val listOfCharactersToIndexUpdate = mutableSetOf<String>()

                if (isSingleFileDownload(filesFromTorrent)) {
                    log.info("SFD - {}", transferToBeDownloaded)
                    val fileToDownload: TorrentFile = getMainFileURLFromTorrent(filesFromTorrent)
                    updateUploadStatus(transferToBeDownloaded, remoteTorrent, listOf(fileToDownload), 0, null)

                    val file = File(if (!fileToDownload.name.isNullOrBlank()) fileToDownload.name!! else extractFileNameFromUrl(fileToDownload.url))
                    val targetFilePath: String
                    val indexLetter: String

                    if (mediaItem != null) {
                        val mediaTarget = buildMediaTargetFile(file, mediaItem, basePath)
                        targetFilePath = mediaTarget.first
                        indexLetter = mediaTarget.second
                    } else {
                        val destination = cloudService.buildDestinationPath(remoteTorrent.name, listOf(fileToDownload))
                        targetFilePath = destination.second + buildFilename(remoteTorrent.name, fileToDownload.url)
                        indexLetter = destination.first
                    }

                    listOfCharactersToIndexUpdate.add(indexLetter)
                    wasDownloadSuccessful = rcloneUploader.copyUrl(fileToDownload.url, targetFilePath)
                    updateUploadStatus(transferToBeDownloaded, remoteTorrent, listOf(fileToDownload), 1, null)
                } else {
                    log.info("MFD - {}", transferToBeDownloaded)
                    var currentFileNumber = 0
                    var failedUploads = 0
                    val startTime = Instant.now()

                    for (torrentFile in filesFromTorrent) {
                        updateUploadStatus(transferToBeDownloaded, remoteTorrent, filesFromTorrent, currentFileNumber, startTime)
                        val file = File(if (!torrentFile.name.isNullOrBlank()) torrentFile.name!! else extractFileNameFromUrl(torrentFile.url))
                        val targetFilePath: String
                        val indexLetter: String

                        if (mediaItem != null) {
                            val mediaTarget = buildMediaTargetFile(file, mediaItem, basePath)
                            targetFilePath = mediaTarget.first
                            indexLetter = mediaTarget.second
                        } else {
                            val destination = cloudService.buildDestinationPath(remoteTorrent.name, filesFromTorrent)
                            val destinationPath: String = destination.second
                            targetFilePath = if (destinationPath.contains(TorrentType.SERIES_SHOWS.type)) {
                                destinationPath + (torrentFile.name ?: "")
                            } else {
                                destinationPath + remoteTorrent.name + "/" + (torrentFile.name ?: "")
                            }
                            indexLetter = destination.first
                        }

                        listOfCharactersToIndexUpdate.add(indexLetter)
                        if (!rcloneUploader.copyUrl(torrentFile.url, targetFilePath)) {
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
     * Every download from the multifilehoster costs traffic, so a transfer whose upload to the drive failed is only
     * retried [MAX_UPLOAD_TO_DRIVE_RETRIES] times, after the other waiting transfers (they are picked by their last
     * update). After that it is marked as failed and its files stay on the multifilehoster.
     */
    private fun handleFailedUploadToDrive(transfer: Transfer) {
        val currentTransfer = transferService.getAll().firstOrNull { it.id == transfer.id } ?: transfer
        currentTransfer.failedUploadsToDrive++
        currentTransfer.progressInPercentage = 0.0
        currentTransfer.eta = Duration.ZERO
        if (currentTransfer.failedUploadsToDrive > MAX_UPLOAD_TO_DRIVE_RETRIES) {
            currentTransfer.transferStatus = TransferStatus.UPLOAD_TO_DRIVE_FAILED
            currentTransfer.feedbackMessage = "upload to drive failed ${currentTransfer.failedUploadsToDrive} times, files kept on the multifilehoster"
            log.error("giving up on transfer after {} failed uploads to drive: {}", currentTransfer.failedUploadsToDrive, currentTransfer)
        } else {
            currentTransfer.transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED
            currentTransfer.feedbackMessage = "upload to drive failed, retry ${currentTransfer.failedUploadsToDrive}/$MAX_UPLOAD_TO_DRIVE_RETRIES"
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
        val transfer = transferService.getAll().firstOrNull { it.id == transferToBeDownloaded.id }
        if (transfer != null) {
            transfer.transferStatus = TransferStatus.UPLOADING_TO_DRIVE
            transfer.progressInPercentage = currentFileNumber.toDouble() / listOfFiles.size.toDouble()
            transfer.eta = getUploadDuration(listOfFiles, currentFileNumber, startTime)
            transferService.save(transfer)
        } else {
            log.warn("couldn't update transfer: $transferToBeDownloaded")
        }
        torrentToBeDownloaded.remoteStatusText = getUploadStatusString(
                torrentToBeDownloaded, listOfFiles, currentFileNumber,
                startTime
        )
        updateTorrent(torrentToBeDownloaded)
    }

    fun getTorrentToBeDownloaded(): Torrent? {
        val activeTorrents: List<Torrent> = getTorrentsToDownload()
        val remainingTrafficInMB: Double = getRemainingTrafficInMB()
        return activeTorrents
                .filter { remoteTorrent: Torrent -> checkIfTorrentCanBeDownloaded(remoteTorrent) }
                .filter { torrent: Torrent -> getSizeOfTorrentInMB(torrent) < remainingTrafficInMB }.minOrNull()
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

    private fun checkIfTorrentCanBeDownloaded(remoteTorrent: Torrent): Boolean {
        return TransferStatus.READY_TO_BE_DOWNLOADED === remoteTorrent.remoteTransferStatus
    }

    private fun getUploadDuration(
            listOfFiles: List<TorrentFile>,
            currentFileNumber: Int,
            startTime: Instant?
    ): Duration {
        val remainingDuration: Duration
        val fileCount = listOfFiles.size
        remainingDuration = if (startTime == null || currentFileNumber == 0) {
            val size = listOfFiles.stream()
                    .map { torrentFile: TorrentFile -> torrentFile.filesize }
                    .reduce(0L) { a: Long, b: Long -> java.lang.Long.sum(a, b) }
            val lsize = size.toDouble() / 1024.0 / 1024.0
            val expectedSecondsRemaining = (lsize / 10.0).toLong()
            Duration.of(expectedSecondsRemaining, ChronoUnit.SECONDS)
        } else {
            val diffTime = Instant.now().toEpochMilli() - startTime.toEpochMilli()
            val milliPerFile = diffTime / currentFileNumber.toLong()
            val remainingFileCount = fileCount - currentFileNumber
            val expectedMilliRemaining = milliPerFile * remainingFileCount
            Duration.of(expectedMilliRemaining, ChronoUnit.MILLIS)
        }
        return remainingDuration
    }

    fun getUploadStatusString(
            torrentToBeDownloaded: Torrent?,
            listOfFiles: List<TorrentFile>,
            currentFileNumber: Int,
            startTime: Instant?
    ): String {
        val remainingDuration: Duration
        val fileCount = listOfFiles.size
        remainingDuration = if (startTime == null || currentFileNumber == 0) {
            val size = listOfFiles.stream()
                    .map { torrentFile: TorrentFile -> torrentFile.filesize }
                    .reduce(0L) { a: Long, b: Long -> java.lang.Long.sum(a, b) }
            val lsize = size.toDouble() / 1024.0 / 1024.0
            val expectedSecondsRemaining = (lsize / 10.0).toLong()
            Duration.of(expectedSecondsRemaining, ChronoUnit.SECONDS)
        } else {
            val diffTime = Instant.now().toEpochMilli() - startTime.toEpochMilli()
            val milliPerFile = diffTime / currentFileNumber.toLong()
            val remainingFileCount = fileCount - currentFileNumber
            val expectedMilliRemaining = milliPerFile * remainingFileCount
            Duration.of(expectedMilliRemaining, ChronoUnit.MILLIS)
        }
        return String.format(
                "Uploading: %d/%d done ETA: %02d:%02d:%02d",
                currentFileNumber,
                fileCount,
                remainingDuration.toHours(),
                remainingDuration.toMinutesPart(),
                remainingDuration.toSecondsPart()
        )
    }

    private fun extractFileNameFromUrl(fileURLFromTorrent: String): String {
        val fileString = URLDecoder.decode(fileURLFromTorrent, StandardCharsets.UTF_8)
        val pattern = Pattern.compile("[^\\/]+\$")
        var foundMatch: String? = null
        val matcher = pattern.matcher(fileString)
        while (matcher.find()) {
            foundMatch = matcher.group()
        }
        return foundMatch?.replace("\\s".toRegex(), ".") ?: ""
    }

    fun buildFilename(name: String?, fileURLFromTorrent: String?): String {
        val fileEndingFromUrl: String? = extractFileEndingFromUrl(fileURLFromTorrent)
        var returnName = if (StringUtils.hasText(name)) name ?: fileURLFromTorrent!! else fileURLFromTorrent!!
        returnName = returnName.replace("\"".toRegex(), "")
        returnName = returnName.replace("\\.torrent".toRegex(), "")
        returnName = returnName.replace("[wW][wW][wW]\\.[A-Za-z0-9-]*\\.[a-zA-Z]+[\\s-]*".toRegex(), "").trim { it <= ' ' }
        returnName = returnName.replace("\\s".toRegex(), ".")
        return if (fileEndingFromUrl?.let { !returnName.contains(it) } ?: true) {
            "$returnName.$fileEndingFromUrl"
        } else {
            returnName
        }
    }

    private fun extractFileEndingFromUrl(fileURLFromTorrent: String?): String? {
        val pattern = Pattern.compile("[A-Za-z0-9]+$")
        var foundMatch: String? = null
        val matcher = fileURLFromTorrent?.let { pattern.matcher(it) }
        matcher?.let {
            while (matcher.find()) {
                foundMatch = matcher.group()
            }
        }
        // remove quotes && .torrent
        return foundMatch?.replace("\"".toRegex(), "")?.replace(".torrent".toRegex(), "") ?: fileURLFromTorrent
    }

    fun refreshTorrents() {
        val remoteTorrents = remoteTorrents
        remoteTorrents.forEach(
                Consumer { torrent: Torrent ->
                    if (isReadyForDownloadStatus(torrent.remoteStatusText)) {
                        val localStatus = localStatusStorage[torrent.torrentId]
                        torrent.remoteStatusText = localStatus ?: torrent.remoteStatusText
                    } else {
                        localStatusStorage.remove(torrent.torrentId)
                    }
                }
        )
        activeTorrents = remoteTorrents
    }

    private fun getTorrentsToDownload(): List<Torrent> {
        val remoteTorrents = remoteTorrentsForDownload.apply {
            forEach(
                    Consumer { torrent: Torrent ->
                        if (isReadyForDownloadStatus(torrent.remoteStatusText)) {
                            val localStatus = localStatusStorage[torrent.torrentId]
                            torrent.remoteStatusText = localStatus ?: torrent.remoteStatusText
                        } else {
                            localStatusStorage.remove(torrent.torrentId)
                        }
                    }
            )
        }
        activeTorrents = remoteTorrents
        return remoteTorrents
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
    }
}
