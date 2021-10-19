package boat.multifileHoster

import boat.mapper.TorrentMapper
import boat.model.Transfer
import boat.model.TransferStatus
import boat.model.TransferType
import boat.services.TransferService
import boat.torrent.HttpUser
import boat.torrent.Torrent
import boat.torrent.TorrentFile
import boat.utilities.HttpHelper
import org.apache.logging.log4j.util.Strings
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.function.Consumer
import java.util.regex.Pattern
import java.util.stream.Collectors

@Service
class MultifileHosterService(httpHelper: HttpHelper,
                             private val transferService: TransferService) : HttpUser(httpHelper) {
    private val multifileHosterList: MutableList<MultifileHoster> = mutableListOf(Premiumize(httpHelper), Alldebrid(httpHelper))
    private val multifileHosterListForDownloads: MutableList<MultifileHoster> = getEligibleMultifileHoster(httpHelper)

    private fun getEligibleMultifileHoster(httpHelper: HttpHelper): MutableList<MultifileHoster> {
        val eligibleList = mutableListOf<MultifileHoster>()
        if(!httpHelper.externalHostname.contains("happysrv") && !httpHelper.externalHostname.contains(".com")){
            eligibleList.add(Alldebrid(httpHelper))
        } else {
            eligibleList.add(Premiumize(httpHelper))
        }
        return eligibleList
    }

    fun getCachedStateOfTorrents(returnResults: List<Torrent>): List<Torrent> {
        multifileHosterList.forEach(Consumer { multifileHoster: MultifileHoster ->
            multifileHoster.enrichCacheStateOfTorrents(
                returnResults
            )
        })
        return returnResults
    }

    fun addTorrentToTransfer(torrent: Torrent) {
        val listOfMultiHostersWithTrafficLeft = multifileHosterList.filter { multifileHoster -> multifileHoster.getRemainingTrafficInMB() > 30000 }
        val potentialMultiHosters = listOfMultiHostersWithTrafficLeft.ifEmpty { multifileHosterList }
        var selectedMultiFileHosterSource: MultifileHoster
        if (potentialMultiHosters.size == 1) {
            selectedMultiFileHosterSource = potentialMultiHosters[0]
        } else {
//            val potentialCachedTorrentToDownload = torrent;
////                getCachedStateOfTorrents(listOf(torrent)).stream().findFirst().orElse(torrent)
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
        if(magnetUri?.contains("btih:") == true) {
            return TransferType.TORRENT
        }
        return TransferType.URL
    }

    fun addTransfersToDownloadQueue() {
        // filter for traffic left
        val transfersToBeAdded = transferService.getAll()
            .filter { transfer -> TransferStatus.ADDED == transfer.transferStatus || TransferStatus.SERVER_ERROR == transfer.transferStatus}
            .filter { transfer -> multifileHosterListForDownloads.any { multifileHoster -> multifileHoster.getName() == transfer.source } }
        multifileHosterListForDownloads.forEach { multifileHoster ->
            val transfersForHoster = transfersToBeAdded.filter { transfer -> transfer.source.equals(multifileHoster.getName()) }
            transfersForHoster.forEach { transfer ->
                val addTorrentToQueueMessage = multifileHoster.addTorrentToDownloadQueue(TorrentMapper.mapTransferToTorrent(transfer))
                transfer.feedbackMessage = addTorrentToQueueMessage
                if (addTorrentToQueueMessage.contains("error")) {
                    log.error("addTorrentToQueueMessage error: {} for transfer {}", addTorrentToQueueMessage, transfer)
                    if(addTorrentToQueueMessage.contains("You already added this job")) {
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
        if(Strings.isNotEmpty(feedbackMessage)) {
            val pattern = Pattern.compile("\"id\":\"([^\"]+)\"")
            val matcher = pattern.matcher(feedbackMessage)
            if(matcher.find()){
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

    fun getActiveMultifileHosters():List<MultifileHoster>{
        return multifileHosterList
    }

    fun getActiveMultifileHosterForDownloads():List<MultifileHoster>{
        return multifileHosterListForDownloads
    }

    fun updateTransferStatus() {
        val transfers = transferService.getAll()
            .filter { transfer -> multifileHosterListForDownloads.any { multifileHoster -> multifileHoster.getName() == transfer.source } }
            .filter { transfer -> !transfer.transferStatus.equals(TransferStatus.UPLOADING_TO_DRIVE) && !transfer.transferStatus.equals(TransferStatus.UPLOADED) }
        val matchedTransfers = mutableListOf<Transfer>()
        remoteTorrentsForDownload.forEach { torrent ->
            transfers.find { transfer -> transfer.uri.lowercase().contains(torrent.torrentId.lowercase()) || transfer.remoteId != null && transfer.remoteId == torrent.remoteId }
                ?.also { transfer ->
                    transfer.transferStatus = torrent.remoteTransferStatus
                    transfer.progressInPercentage = torrent.remoteProgressInPercent
                    transfer.name = torrent.name
                    transfer.remoteId = torrent.remoteId
                    transferService.save(transfer)
                    matchedTransfers.add(transfer)
                } ?: also {
                log.error("no transfer for torrent found: {}", torrent)
            }
        }
        val listOfUnmatchedTransfers = transfers.filter { matchedTransfers.none { matchedTransfer -> matchedTransfer.id.equals(it.id) } }
        if (listOfUnmatchedTransfers.isNotEmpty()) {
            log.warn("listOfUnmatchedTransfers: [{}]", listOfUnmatchedTransfers)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MultifileHosterService::class.java)
    }

}
