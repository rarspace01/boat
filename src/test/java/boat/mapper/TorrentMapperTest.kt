package boat.mapper

import boat.model.Transfer
import boat.model.TransferStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TorrentMapperTest {

    @Test
    fun shouldMapRemoteStatusCodes() {
        assertThat(TorrentMapper.mapRemoteStatus(0)).isEqualTo(TransferStatus.ADDED_TO_MULTIHOSTER)
        assertThat(TorrentMapper.mapRemoteStatus(1)).isEqualTo(TransferStatus.DOWNLOADING_TO_MULTIHOSTER)
        assertThat(TorrentMapper.mapRemoteStatus(2)).isEqualTo(TransferStatus.UPLOADING_TO_MULTIHOSTER)
        assertThat(TorrentMapper.mapRemoteStatus(3)).isEqualTo(TransferStatus.UPLOADING_TO_MULTIHOSTER)
        assertThat(TorrentMapper.mapRemoteStatus(4)).isEqualTo(TransferStatus.READY_TO_BE_DOWNLOADED)
        (5..15).forEach { assertThat(TorrentMapper.mapRemoteStatus(it)).isEqualTo(TransferStatus.ERROR) }
    }

    @Test
    fun shouldMapUnknownRemoteStatusCodes() {
        assertThat(TorrentMapper.mapRemoteStatus(-1)).isEqualTo(TransferStatus.UNKNOWN)
        assertThat(TorrentMapper.mapRemoteStatus(16)).isEqualTo(TransferStatus.UNKNOWN)
        assertThat(TorrentMapper.mapRemoteStatus(null as Int?)).isEqualTo(TransferStatus.UNKNOWN)
    }

    @Test
    fun shouldMapRemoteStatusTexts() {
        mapOf(
            "waiting" to TransferStatus.ADDED_TO_MULTIHOSTER,
            "queued" to TransferStatus.ADDED_TO_MULTIHOSTER,
            "running" to TransferStatus.DOWNLOADING_TO_MULTIHOSTER,
            "finished" to TransferStatus.READY_TO_BE_DOWNLOADED,
            "seeding" to TransferStatus.READY_TO_BE_DOWNLOADED,
            "deleted" to TransferStatus.DELETED,
            "banned" to TransferStatus.ERROR,
            "error" to TransferStatus.ERROR,
            "timeout" to TransferStatus.ERROR,
        ).forEach { (status, expected) -> assertThat(TorrentMapper.mapRemoteStatus(status)).`as`(status).isEqualTo(expected) }
    }

    @Test
    fun shouldMapUnknownRemoteStatusTexts() {
        assertThat(TorrentMapper.mapRemoteStatus("something new")).isEqualTo(TransferStatus.UNKNOWN)
        assertThat(TorrentMapper.mapRemoteStatus("")).isEqualTo(TransferStatus.UNKNOWN)
        assertThat(TorrentMapper.mapRemoteStatus(null as String?)).isEqualTo(TransferStatus.UNKNOWN)
    }

    @Test
    fun shouldMapTransferToTorrent() {
        // Given
        val transfer = Transfer(
            name = "Ivakisaka | Ivakisaka.2010.1080p",
            remoteId = "remote-1",
            remoteMediaId = "{tmdb-27205}",
            source = "Premiumize",
            uri = "magnet:?xt=urn:btih:abc",
            transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED,
            sizeInBytes = 2048,
            feedbackMessage = "done",
        )
        // When
        val torrent = TorrentMapper.mapTransferToTorrent(transfer)
        // Then
        assertThat(torrent.name).isEqualTo("Ivakisaka.2010.1080p")
        assertThat(torrent.source).isEqualTo("Premiumize")
        assertThat(torrent.magnetUri).isEqualTo("magnet:?xt=urn:btih:abc")
        assertThat(torrent.remoteId).isEqualTo("remote-1")
        assertThat(torrent.remoteStatusText).isEqualTo("done")
        assertThat(torrent.size).isEqualTo("2.00 KiB")
        assertThat(torrent.remoteTransferStatus).isEqualTo(TransferStatus.READY_TO_BE_DOWNLOADED)
        assertThat(torrent.localTransferStatus).isEqualTo(TransferStatus.READY_TO_BE_DOWNLOADED)
    }

    @Test
    fun shouldMapTransferWithoutRemoteIdAndMessage() {
        val torrent = TorrentMapper.mapTransferToTorrent(Transfer(name = "Name"))
        assertThat(torrent.remoteId).isEmpty()
        assertThat(torrent.remoteStatusText).isEmpty()
    }
}
