package boat.multifileHoster

import boat.model.Transfer
import boat.torrent.Torrent
import boat.torrent.TorrentHelper
import boat.utilities.LoggerDelegate
import org.apache.logging.log4j.util.Strings

/**
 * Decides if a transfer of boat belongs to a torrent at the multifilehoster.
 */
object TransferMatching {

    private val log by LoggerDelegate()

    /**
     * A transfer only belongs to a torrent at its own hoster: another hoster can have the same torrent (e.g. an older
     * transfer), downloading that one would use the traffic of the wrong hoster.
     */
    fun isAtTheHosterOf(transfer: Transfer, torrent: Torrent): Boolean = transfer.source == torrent.source

    fun transferMatchedTorrentBySource(transfer: Transfer, torrent: Torrent): Boolean {
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
}
