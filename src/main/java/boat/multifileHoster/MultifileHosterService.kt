package boat.multifileHoster

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
import boat.utilities.ProcessUtil
import boat.utilities.PropertiesHelper
import boat.utilities.StreamGobbler
import org.apache.logging.log4j.util.Strings
import org.slf4j.LoggerFactory
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
import java.util.stream.Collectors

@Service
class MultifileHosterService(
    httpHelper: HttpHelper,
    private val transferService: TransferService,
    private val cloudService: CloudService,
) : HttpUser(httpHelper) {
    private var isDownloadInProgress: Boolean = false
    private var isRcloneInstalled: Boolean? = null
    private val multifileHosterList: MutableList<MultifileHoster> = mutableListOf(Premiumize(httpHelper), Alldebrid(httpHelper))
    private val multifileHosterListForDownloads: MutableList<MultifileHoster> = getEligibleMultifileHoster(httpHelper)

    val localStatusStorage = HashMap<String, String>()

    var activeTorrents: MutableList<Torrent> = java.util.ArrayList()

    private fun getEligibleMultifileHoster(httpHelper: HttpHelper): MutableList<MultifileHoster> {
        val eligibleList = mutableListOf<MultifileHoster>()
        if (!httpHelper.externalHostname.contains("happysrv") && !httpHelper.externalHostname.contains(".com")) {
            eligibleList.add(Alldebrid(httpHelper))
        } else {
            eligibleList.add(Premiumize(httpHelper))
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
        val selectedMultiFileHosterSource: MultifileHoster
        if (potentialMultiHosters.size == 1) {
            selectedMultiFileHosterSource = potentialMultiHosters[0]
        } else {
//            val potentialCachedTorrentToDownload = torrent;
// //                getCachedStateOfTorrents(listOf(torrent)).stream().findFirst().orElse(torrent)
//            val cachedMultihosters = potentialMultihosters
//                .filter { multifileHoster: MultifileHoster ->
//                    potentialCachedTorrentToDownload.cached.contains(multifileHoster.getName())
//                }
//            val multiHostersToDownload = cachedMultihosters.ifEmpty { potentialMultiHosters }
            selectedMultiFileHosterSource = potentialMultiHosters
                .stream()
                .min(Comparator.comparingInt(MultifileHoster::getPrio))
                .orElse(potentialMultiHosters.first())
        }
        val transfer = Transfer()
        transfer.name = TorrentHelper.extractTorrentName(torrent)
        transfer.transferType = extractType(torrent.magnetUri)
        transfer.transferStatus = TransferStatus.ADDED
        transfer.source = selectedMultiFileHosterSource.getName()
        transfer.uri = torrent.magnetUri
        transferService.save(transfer)
//        val listOfMultihostersWithTrafficLeft = multifileHosterList.filter { multifileHoster -> multifileHoster.getRemainingTrafficInMB() > 30000 }
//        val potentialMultihosters = listOfMultihostersWithTrafficLeft.ifEmpty { multifileHosterList }
//
//        return if (potentialMultihosters.size == 1) {
//            potentialMultihosters[0].addTorrentToQueue(torrent)
//        } else {
//            val potentialCachedTorrentToDownload =
//                getCachedStateOfTorrents(listOf(torrent)).stream().findFirst().orElse(torrent)
//            val cachedMultihosters = potentialMultihosters
//                .filter { multifileHoster: MultifileHoster ->
//                    potentialCachedTorrentToDownload.cached.contains(multifileHoster.getName())
//                }
//            val multihostersToDownload = cachedMultihosters.ifEmpty { potentialMultihosters }
//            multihostersToDownload
//                .stream()
//                .min(Comparator.comparingInt(MultifileHoster::getPrio))
//                .orElse(potentialMultihosters.first())
//                .addTorrentToQueue(torrent)
//        }
    }

    private fun extractType(magnetUri: String?): TransferType {
        if (magnetUri?.contains("btih:") == true) {
            return TransferType.TORRENT
        }
        return TransferType.URL
    }

    fun addTransfersToDownloadQueue() {
        // filter for traffic left
        val transfersToBeAdded = transferService.getAll()
            .filter { transfer -> TransferStatus.ADDED == transfer.transferStatus || TransferStatus.SERVER_ERROR == transfer.transferStatus }
            .filter { transfer -> multifileHosterListForDownloads.any { multifileHoster -> multifileHoster.getName() == transfer.source } }
        multifileHosterListForDownloads.forEach { multifileHoster ->
            val transfersForHoster = transfersToBeAdded.filter { transfer -> transfer.source.equals(multifileHoster.getName()) }
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
                    transfer.remoteId = extractRemoteIdFromMessage(transfer.feedbackMessage)
                    transfer.transferStatus = TransferStatus.ADDED_TO_MULTIHOSTER
                }
                transferService.save(transfer)
            }
        }
    }

    fun extractRemoteIdFromMessage(feedbackMessage: String): String? {
        if (Strings.isNotEmpty(feedbackMessage)) {
            val pattern = Pattern.compile("\"id\":\"([^\"]+)\"")
            val matcher = pattern.matcher(feedbackMessage)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }
//        val listOfMultihostersWithTrafficLeft = multifileHosterList.filter { multifileHoster -> multifileHoster.getRemainingTrafficInMB() > 30000 }
//        val potentialMultihosters = listOfMultihostersWithTrafficLeft.ifEmpty { multifileHosterList }
//
//        return if (potentialMultihosters.size == 1) {
//            potentialMultihosters[0].addTorrentToQueue(torrent)
//        } else {
//            val potentialCachedTorrentToDownload =
//                getCachedStateOfTorrents(listOf(torrent)).stream().findFirst().orElse(torrent)
//            val cachedMultihosters = potentialMultihosters
//                .filter { multifileHoster: MultifileHoster ->
//                    potentialCachedTorrentToDownload.cached.contains(multifileHoster.getName())
//                }
//            val multihostersToDownload = cachedMultihosters.ifEmpty { potentialMultihosters }
//            multihostersToDownload
//                .stream()
//                .min(Comparator.comparingInt(MultifileHoster::getPrio))
//                .orElse(potentialMultihosters.first())
//                .addTorrentToQueue(torrent)

    val remoteTorrents: List<Torrent>
        get() = multifileHosterListForDownloads.stream()
            .flatMap { multifileHoster: MultifileHoster -> multifileHoster.getRemoteTorrents().stream() }
            .collect(Collectors.toList())

    val remoteTorrentsForDownload: List<Torrent>
        get() = multifileHosterListForDownloads.stream()
            .flatMap { multifileHoster: MultifileHoster -> multifileHoster.getRemoteTorrents().stream() }
            .collect(Collectors.toList())

    fun isSingleFileDownload(torrentToBeDownloaded: Torrent): Boolean {
        val tfList = getFilesFromTorrent(torrentToBeDownloaded)
        var sumFileSize = 0L
        var biggestFileYet = 0L
        for (tf in tfList) {
            if (tf.filesize > biggestFileYet) {
                biggestFileYet = tf.filesize
            }
            sumFileSize += tf.filesize
        }
        // if maxfilesize >90% sumSize --> Singlefile
        return biggestFileYet > 0.9 * sumFileSize
    }

    fun isMultiFileDownload(torrentToBeDownloaded: Torrent): Boolean {
        return getFilesFromTorrent(torrentToBeDownloaded).size > 1
    }

    fun getSizeOfTorrentInMB(torrent: Torrent): Double {
        val size: Long = getFilesFromTorrent(torrent).sumOf { torrentFile: TorrentFile -> torrentFile.filesize }
        return size.toDouble() / 1024.0 / 1024.0
    }

    fun getRemainingTrafficInMB(): Double {
        return multifileHosterList.sumOf { multifileHoster: MultifileHoster -> multifileHoster.getRemainingTrafficInMB() }
    }

    fun getFilesFromTorrent(torrentToBeDownloaded: Torrent): List<TorrentFile> {
        val hoster = multifileHosterList.stream()
            .filter { multifileHoster: MultifileHoster -> multifileHoster.getName() == torrentToBeDownloaded.source }
            .findFirst()
        return if (hoster.isPresent) {
            hoster.get().getFilesFromTorrent(torrentToBeDownloaded)
        } else {
            log.error("no MFH present for downloads")
            ArrayList()
        }
    }

    fun getMainFileURLFromTorrent(torrentToBeDownloaded: Torrent): TorrentFile {
        val tfList = getFilesFromTorrent(torrentToBeDownloaded)
        // iterate over and check for One File Torrent
        var biggestFileYet: TorrentFile = tfList[0]
        for (tf in tfList) {
            if (tf.filesize > biggestFileYet.filesize) {
                biggestFileYet = tf
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
            .filter { transfer -> !transfer.transferStatus.equals(TransferStatus.UPLOADING_TO_DRIVE) && !transfer.transferStatus.equals(TransferStatus.UPLOADED) }
        val matchedTransfers = mutableListOf<Transfer>()
        val matchedTorrents = mutableListOf<Torrent>()
        val torrentsForDownload = remoteTorrentsForDownload
        torrentsForDownload.forEach { torrent ->
            transfers.find { transfer ->
                transfer.uri.lowercase().contains(torrent.torrentId.lowercase()) ||
                    transfer.remoteId != null && transfer.remoteId == torrent.remoteId ||
                    transferMatchedTorrentBySource(transfer, torrent) ||
                    transferMatchedTorrentByName(transfer, torrent)
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
                log.error("no transfer for torrent found: {}", torrent)
            }
        }
        val listOfUnmatchedTransfers = transfers.filter { matchedTransfers.none { matchedTransfer -> matchedTransfer.id.equals(it.id) } }
        val listOfUnmatchedTorrents = torrentsForDownload.filter { matchedTorrents.none { matchedTorrent -> matchedTorrent.torrentId.equals((it.torrentId)) } }
        if (listOfUnmatchedTransfers.isNotEmpty()) {
            log.warn("listOfUnmatchedTransfers: [{}]", listOfUnmatchedTransfers)
            log.warn("listOfUnmatchedTorrents: [{}]", listOfUnmatchedTorrents)
        }
    }

    private fun transferMatchedTorrentBySource(transfer: Transfer, torrent: Torrent): Boolean {
        return Strings.isNotEmpty(transfer.uri) && transfer.source.equals(torrent.magnetUri)
    }

    fun transferMatchedTorrentByName(transfer: Transfer, torrent: Torrent): Boolean {
        val transferName = TorrentHelper.getNormalizedTorrentStringWithSpaces(transfer.name).lowercase()
        val torrentName = TorrentHelper.getNormalizedTorrentStringWithSpaces(torrent.name).lowercase()
        val matchedByName = transfer.name != null && transferName.lowercase() == torrentName.lowercase()
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
                (transferInTorrentCount.toDouble() / transferWordList.count().toDouble() > 0.75) && transferDiffCount <= 2 &&
                    (torrentInTransferCount.toDouble() / torrentWordList.count().toDouble() > 0.75) && torrentDiffCount <= 2
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
                    transfer.uri != null && transfer.uri.lowercase(Locale.ROOT).contains(
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
                if (isSingleFileDownload(torrentToBeDownloaded)) {
                    transferToBeDownloaded.ifPresent { transfer: Transfer? ->
                        log.info(
                            "SFD - {}",
                            transfer
                        )
                    }
                    val fileToDownload: TorrentFile = getMainFileURLFromTorrent(torrentToBeDownloaded)
                    updateUploadStatus(torrentToBeDownloaded, listOf(fileToDownload), 0, null)
                    if (torrentToBeDownloaded.name.contains("magnet:?")) {
                        torrentToBeDownloaded.name = extractFileNameFromUrl(fileToDownload.url)
                    }
                    wasDownloadSuccessful = rcloneDownloadFileToGdrive(
                        fileToDownload.url,
                        cloudService.buildDestinationPath(torrentToBeDownloaded.name) + buildFilename(
                            torrentToBeDownloaded.name, fileToDownload.url
                        )
                    )
                    updateUploadStatus(torrentToBeDownloaded, listOf(fileToDownload), 1, null)
                    delete(torrentToBeDownloaded)
                    transferToBeDownloaded = transferService.get(transferToBeDownloaded)
                    transferToBeDownloaded.ifPresent { transfer: Transfer? -> transferService.delete(transfer!!) }
                } else if (isMultiFileDownload(torrentToBeDownloaded)) {
                    transferToBeDownloaded.ifPresent { transfer: Transfer? ->
                        log.info(
                            "MFD - {}",
                            transfer
                        )
                    }
                    val filesFromTorrent: List<TorrentFile> = getFilesFromTorrent(torrentToBeDownloaded)
                    var currentFileNumber = 0
                    var failedUploads = 0
                    val startTime = Instant.now()
                    for (torrentFile in filesFromTorrent) {
                        // check fileSize to get rid of samples and NFO files?
                        updateUploadStatus(torrentToBeDownloaded, filesFromTorrent, currentFileNumber, startTime)
                        val destinationPath: String = cloudService
                            .buildDestinationPath(torrentToBeDownloaded.name, filesFromTorrent)
                        var targetFilePath: String
                        targetFilePath = if (destinationPath.contains("transfer")) {
                            (
                                PropertiesHelper.getProperty("RCLONEDIR") + "/transfer/multipart/" +
                                    torrentToBeDownloaded.name + "/" + torrentFile.name
                                )
                        } else {
                            if (destinationPath.contains(TorrentType.SERIES_SHOWS.type)) {
                                destinationPath + torrentFile.name
                            } else {
                                destinationPath + torrentToBeDownloaded.name + "/" + torrentFile.name
                            }
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
                } else {
                    log.error(torrentToBeDownloaded.toString())
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
            transfer.uri != null && transfer.uri.toLowerCase(Locale.ROOT).contains(
                torrentToBeDownloaded.torrentId.toLowerCase(
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

    fun getUploadDuration(
        listOfFiles: List<TorrentFile>,
        currentFileNumber: Int,
        startTime: Instant?
    ): Duration? {
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
    ): String? {
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

    private fun extractFileNameFromUrl(fileURLFromTorrent: String): String? {
        val fileString = URLDecoder.decode(fileURLFromTorrent, StandardCharsets.UTF_8)
        val pattern = Pattern.compile("([\\w.%\\-]+)$")
        var foundMatch: String? = null
        val matcher = pattern.matcher(fileString)
        while (matcher.find()) {
            foundMatch = matcher.group()
        }
        foundMatch?.replace("\\s".toRegex(), ".")
        return foundMatch
    }

    private fun rcloneDownloadFileToGdrive(fileURLFromTorrent: String, destinationPath: String): Boolean {
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

    fun buildFilename(name: String?, fileURLFromTorrent: String?): String? {
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

    fun getTorrentsToDownload(): List<Torrent> {
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

    fun updateTorrent(torrentUpdate: Torrent) {
        localStatusStorage[torrentUpdate.torrentId] = torrentUpdate.remoteStatusText
        torrentUpdate.remoteId?.let {
            localStatusStorage[torrentUpdate.remoteId] = torrentUpdate.remoteStatusText
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MultifileHosterService::class.java)
    }
}
