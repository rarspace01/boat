package boat.multifileHoster

import boat.info.CloudFileService
import boat.info.CloudService
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
import boat.utilities.StreamGobbler
import org.apache.logging.log4j.util.Strings
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.Executors
import java.util.function.Consumer
import java.util.regex.Pattern

@Service
class MultifileHosterService(
    httpHelper: HttpHelper,
    private val transferService: TransferService,
    private val cloudService: CloudService,
    private val cacheManager: CacheManager,
    private val cloudFileService: CloudFileService,
) : HttpUser(httpHelper) {
    private var isDownloadInProgress: Boolean = false
    private var isRcloneInstalled: Boolean? = null
    private val multifileHosterList: MutableList<MultifileHoster> = mutableListOf(Premiumize(httpHelper), Alldebrid(httpHelper))
    private val multifileHosterListForDownloads: MutableList<MultifileHoster> = getEligibleMultifileHoster(httpHelper)

    private val localStatusStorage = HashMap<String, String>()

    var activeTorrents: MutableList<Torrent> = mutableListOf()

    private fun getEligibleMultifileHoster(httpHelper: HttpHelper): MutableList<MultifileHoster> {
        val eligibleList = mutableListOf<MultifileHoster>()
        if (PREMIUMIZE_WHITELIST.any { httpHelper.externalHostname().contains(it) }) {
            eligibleList.add(Premiumize(httpHelper))
        } else {
            eligibleList.add(Alldebrid(httpHelper))
        }
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

    fun addTorrentToTransfer(torrent: Torrent) {
        val listOfMultiHostersWithTrafficLeft = multifileHosterList.filter { multifileHoster -> multifileHoster.getRemainingTrafficInMB() > 30000 }
        val potentialMultiHosters = listOfMultiHostersWithTrafficLeft.ifEmpty { multifileHosterList }
        val selectedMultiFileHosterSource: MultifileHoster = if (potentialMultiHosters.size == 1) {
            potentialMultiHosters.first()
        } else {
            potentialMultiHosters
                .minByOrNull { it.getPrio() }
                ?: potentialMultiHosters.first()
        }
        val transfer = Transfer(
            name = TorrentHelper.extractTorrentName(torrent),
            transferType = extractType(torrent.magnetUri),
            transferStatus = TransferStatus.ADDED,
            source = selectedMultiFileHosterSource.getName(),
            uri = torrent.magnetUri,
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

    fun addTransfersToDownloadQueue() {
        // filter for traffic left
        val transfersToBeDownloaded = transferService.getAll()
            .filter { transfer -> TransferStatus.ADDED == transfer.transferStatus || TransferStatus.SERVER_ERROR == transfer.transferStatus }
            .filter { transfer -> multifileHosterListForDownloads.any { multifileHoster -> multifileHoster.getName() == transfer.source } }
        multifileHosterListForDownloads.forEach { multifileHoster ->
            val transfersForHoster = transfersToBeDownloaded.filter { transfer -> transfer.source == multifileHoster.getName() }
            transfersForHoster.forEach { transfer ->
                val addTorrentToQueueMessage = multifileHoster.addTorrentToDownloadQueue(TorrentMapper.mapTransferToTorrent(transfer))
                transfer.feedbackMessage = addTorrentToQueueMessage
                if (addTorrentToQueueMessage.contains("error")) {
                    log.error("addTorrentToQueueMessage error: {} for transfer {}", addTorrentToQueueMessage, transfer)
                    if (addTorrentToQueueMessage.contains("You already added this job")) {
                        transfer.transferStatus = TransferStatus.ERROR
                    } else {
                        transfer.transferStatus = TransferStatus.SERVER_ERROR
                    }
                } else {
                    transfer.remoteId = extractRemoteIdFromMessage(transfer)
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
            .filter { transfer -> transfer.transferStatus != TransferStatus.UPLOADING_TO_DRIVE && transfer.transferStatus != TransferStatus.UPLOADED }
        val matchedTransfers = mutableListOf<Transfer>()
        val matchedTorrents = mutableListOf<Torrent>()
        val torrentsForDownload = remoteTorrents
        matchTransfersAndTorrentsByIdAndUpdateStatus(torrentsForDownload, transfers, matchedTransfers, matchedTorrents)
        var listOfUnmatchedTransfers = transfers.filter { matchedTransfers.none { matchedTransfer -> matchedTransfer.id.equals(it.id) } }
        var listOfUnmatchedTorrents = torrentsForDownload.filter { matchedTorrents.none { matchedTorrent -> matchedTorrent.torrentId == (it.torrentId) } }
        if (listOfUnmatchedTorrents.isNotEmpty() && listOfUnmatchedTorrents.isNotEmpty()) {
            listOfUnmatchedTorrents.forEach { torrent ->
                transfers.find { transfer ->
                    transferMatchedTorrentByName(transfer, torrent, 0)
                }?.also { transfer ->
                    transfer.transferStatus = torrent.remoteTransferStatus
                    transfer.progressInPercentage = torrent.remoteProgressInPercent
                    transfer.name = torrent.name
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

            if (listOfUnmatchedTorrents.isNotEmpty() && listOfUnmatchedTorrents.isNotEmpty()) {
                listOfUnmatchedTorrents.forEach { torrent ->
                    transfers.find { transfer ->
                        transferMatchedTorrentByName(transfer, torrent, 2)
                    }?.also { transfer ->
                        transfer.transferStatus = torrent.remoteTransferStatus
                        transfer.progressInPercentage = torrent.remoteProgressInPercent
                        transfer.name = torrent.name
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
                    transfer.name = torrent.name
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
        return Strings.isNotEmpty(transfer.uri) && transfer.source == torrent.magnetUri
    }

    fun transferMatchedTorrentByName(transfer: Transfer, torrent: Torrent, maxDiff: Int = 0): Boolean {
        val transferName = TorrentHelper.getNormalizedTorrentStringWithSpaces(transfer.name).lowercase()
        val torrentName = TorrentHelper.getNormalizedTorrentStringWithSpaces(torrent.name).lowercase()
        val matchedByName = transfer.name.isNotEmpty() && transferName.lowercase() == torrentName.lowercase()
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

    fun checkForDownloadableTorrents() {
        if (!isDownloadInProgress && isRcloneInstalled() && cloudService.isCloudTokenValid) {
            checkForDownloadableTorrentsAndDownloadTheFirst()
        }
    }

    private fun checkForDownloadableTorrentsAndDownloadTheFirst() {
        val torrentToBeDownloaded: Torrent? = getTorrentToBeDownloaded()
        if (torrentToBeDownloaded != null) {
            var transferToBeDownloaded = transferService.getAll().stream()
                .filter { transfer: Transfer ->
                    transfer.uri.isNotEmpty() && transfer.uri.lowercase(Locale.ROOT).contains(
                        torrentToBeDownloaded.torrentId.lowercase(
                            Locale.ROOT
                        )
                    ) || transfer.remoteId != null && transfer.remoteId == torrentToBeDownloaded.remoteId
                }.findFirst()
            isDownloadInProgress = true
            var wasDownloadSuccessful = false
            if (transferToBeDownloaded.isEmpty) {
                log.warn("Torrent not in transfers but downloading it: {}", torrentToBeDownloaded)
            }
            try {
                val filesFromTorrent = getFilesFromTorrent(torrentToBeDownloaded)
                if (isSingleFileDownload(filesFromTorrent)) {
                    transferToBeDownloaded.ifPresent { transfer: Transfer? ->
                        log.info(
                            "SFD - {}",
                            transfer
                        )
                    }
                    val fileToDownload: TorrentFile = getMainFileURLFromTorrent(filesFromTorrent)
                    updateUploadStatus(torrentToBeDownloaded, listOf(fileToDownload), 0, null)
                    //if (torrentToBeDownloaded.name.lowercase().contains("magnet:")) {
                    val extractFileNameFromUrl = extractFileNameFromUrl(fileToDownload.url)
                    log.info("Name before: [${torrentToBeDownloaded.name}] after [$extractFileNameFromUrl]")
                    torrentToBeDownloaded.name = extractFileNameFromUrl
                    //}
                    val destination = cloudService.buildDestinationPath(torrentToBeDownloaded.name)
                    wasDownloadSuccessful = rcloneDownloadFileToGdrive(
                        fileToDownload.url,
                        destination.second + buildFilename(
                            torrentToBeDownloaded.name, fileToDownload.url
                        )
                    )
                    updateUploadStatus(torrentToBeDownloaded, listOf(fileToDownload), 1, null)
                    delete(torrentToBeDownloaded)

                    transferToBeDownloaded = transferService.get(transferToBeDownloaded)
                    transferToBeDownloaded.ifPresent { transfer: Transfer? -> transferService.delete(transfer!!) }
                    updateIndexForLetters(destination.first)
                } else {
                    transferToBeDownloaded.ifPresent { transfer: Transfer? ->
                        log.info(
                            "MFD - {}",
                            transfer
                        )
                    }
                    var currentFileNumber = 0
                    var failedUploads = 0
                    val startTime = Instant.now()
                    val listOfCharactersToIndexUpdate = mutableSetOf<String>()
                    for (torrentFile in filesFromTorrent) {
                        // check fileSize to get rid of samples and NFO files?
                        updateUploadStatus(torrentToBeDownloaded, filesFromTorrent, currentFileNumber, startTime)
                        log.info("Name before: [${torrentToBeDownloaded.name}]")
                        val destination = cloudService
                            .buildDestinationPath(torrentToBeDownloaded.name, filesFromTorrent)
                        listOfCharactersToIndexUpdate.add(destination.first)
                        val destinationPath: String = destination.second
                        var targetFilePath: String
                        targetFilePath =
                            if (destinationPath.contains(TorrentType.SERIES_SHOWS.type)) {
                                destinationPath + torrentFile.name
                            } else {
                                destinationPath + torrentToBeDownloaded.name + "/" + torrentFile.name
                            }
                        if (!rcloneDownloadFileToGdrive(torrentFile.url, targetFilePath)) {
                            failedUploads++
                        }
                        currentFileNumber++
                        updateUploadStatus(torrentToBeDownloaded, filesFromTorrent, currentFileNumber, startTime)
                    }
                    wasDownloadSuccessful = failedUploads == 0
                    delete(torrentToBeDownloaded)
                    transferToBeDownloaded = transferService.get(transferToBeDownloaded)
                    transferToBeDownloaded.ifPresent { transfer: Transfer? -> transferService.delete(transfer!!) }

                    updateIndexForLetters(listOfCharactersToIndexUpdate.joinToString())

                }
            } catch (exception: Exception) {
                log.error(String.format("Couldn't download Torrent: %s", torrentToBeDownloaded), exception)
            } finally {
                isDownloadInProgress = false
            }
            if (!wasDownloadSuccessful) {
                log.error("Couldn't download Torrent: {}", torrentToBeDownloaded)
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

    private fun updateUploadStatus(
        torrentToBeDownloaded: Torrent,
        listOfFiles: List<TorrentFile>,
        currentFileNumber: Int,
        startTime: Instant?
    ) {
        val transferOptional = transferService.getAll().stream().filter { transfer: Transfer ->
            transfer.uri.isNotEmpty() && transfer.uri.lowercase(Locale.ROOT).contains(
                torrentToBeDownloaded.torrentId.lowercase(
                    Locale.ROOT
                )
            )
        }.findFirst()
        if (transferOptional.isPresent) {
            val transfer = transferOptional.get()
            transfer.transferStatus = TransferStatus.UPLOADING_TO_DRIVE
            transfer.progressInPercentage = currentFileNumber.toDouble() / listOfFiles.size.toDouble()
            transfer.eta = getUploadDuration(listOfFiles, currentFileNumber, startTime)
            transferService.save(transfer)
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
            isRcloneInstalled = ProcessUtil.isRcloneInstalled()
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
        val pattern = Pattern.compile("([\\w.%\\-]+)$")
        var foundMatch: String? = null
        val matcher = pattern.matcher(fileString)
        while (matcher.find()) {
            foundMatch = matcher.group()
        }
        return foundMatch?.replace("\\s".toRegex(), ".") ?: ""
    }

    private fun rcloneDownloadFileToGdrive(fileURLFromTorrent: String?, destinationPath: String): Boolean {
        log.info("D>[{}]", destinationPath)
        val builder = ProcessBuilder()
        val commandToRun = String.format("rclone copyurl '%s' '%s'", fileURLFromTorrent, destinationPath.replace("'".toRegex(), ""))
        builder.command("bash", "-c", commandToRun)
        builder.directory(File(System.getProperty("user.home")))
        var process: Process? = null
        var exitCode = -1
        try {
            process = builder.start()
            val streamGobbler = StreamGobbler(process.inputStream) { x: String? -> println(x) }
            Executors.newSingleThreadExecutor().submit(streamGobbler)
            exitCode = process.waitFor()
        } catch (e: IOException) {
            log.error("upload failed: {}", destinationPath)
            log.error(e.message)
            return false
        } catch (e: InterruptedException) {
            log.error("upload failed: {}", destinationPath)
            log.error(e.message)
            return false
        }
        return if (exitCode != 0) {
            log.error("upload failed: {}", destinationPath)
            false
        } else {
            log.info("DF>[{}]", destinationPath)
            true
        }
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
        val matcher = pattern.matcher(fileURLFromTorrent)
        while (matcher.find()) {
            foundMatch = matcher.group()
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
        activeTorrents = java.util.ArrayList()
        activeTorrents.addAll(remoteTorrents)
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
        activeTorrents = java.util.ArrayList()
        activeTorrents.addAll(remoteTorrents)
        return activeTorrents
    }

    private fun isReadyForDownloadStatus(status: String?): Boolean {
        return status != null && status.lowercase(Locale.getDefault()).matches(Regex("finished|seeding|ready"))
    }

    private fun updateTorrent(torrentUpdate: Torrent) {
        localStatusStorage[torrentUpdate.torrentId] = torrentUpdate.remoteStatusText
        torrentUpdate.remoteId.let {
            localStatusStorage[torrentUpdate.remoteId] = torrentUpdate.remoteStatusText
        }
    }

    fun updateIndexForLetters(searchChars: String) {
        log.info("refreshCloudFileServiceCache for letters: $searchChars")
        val filesCache = cacheManager.getCache("filesCache")
        searchChars.split("").forEach { searchChar: String ->
            TorrentType.values()
                .forEach { torrentType: TorrentType ->
                    val destinationPath = cloudService
                        .buildDestinationPathWithTypeOfMediaWithoutSubFolders(searchChar, torrentType)
                    filesCache?.evictIfPresent(destinationPath)
                    cloudFileService.getFilesInPath(destinationPath)
                }
            log.info("Cache refresh done for: $searchChars")
        }
    }


    companion object {
        private val log by LoggerDelegate()
        private val PREMIUMIZE_WHITELIST = listOf("happysrv", "powersrv", ".com", ".club")
    }
}
