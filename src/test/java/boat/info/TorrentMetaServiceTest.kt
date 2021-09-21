package boat.info

import boat.multifileHoster.MultifileHosterService
import boat.torrent.Torrent
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TorrentMetaServiceTest {
    private val multifileHosterService: MultifileHosterService = mockk()
    private val torrentMetaService: TorrentMetaService = TorrentMetaService(multifileHosterService)

    @BeforeEach
    fun setUp() {
        clearAllMocks()
    }

    @Test
    fun `should refreshTorrents and have local State`() {
        // Given
        val remoteTorrentDownloading = Torrent("Remote")
        remoteTorrentDownloading.magnetUri = "btih:123&"
        remoteTorrentDownloading.remoteStatusText = "downloading"
        val remoteTorrentFinished = Torrent("Remote")
        remoteTorrentFinished.magnetUri = "btih:321&"
        remoteTorrentFinished.remoteStatusText = "finished"
        val localTorrentUploading = Torrent("Remote")
        localTorrentUploading.magnetUri = "btih:321&"
        localTorrentUploading.remoteStatusText = "uploading (3/7)"
        every {multifileHosterService.remoteTorrents} returns (listOf(remoteTorrentDownloading, remoteTorrentFinished))
        torrentMetaService.updateTorrent(localTorrentUploading)
        // When
        torrentMetaService.refreshTorrents()
        // Then
        val activeTorrents = torrentMetaService.activeTorrents
        Assertions.assertThat(activeTorrents).containsExactlyInAnyOrder(remoteTorrentDownloading, localTorrentUploading)
    }

    @Test
    fun `should refreshTorrents multiple times and have local State after`() {
        // Given
        val remoteTorrentDownloading = Torrent("Remote")
        remoteTorrentDownloading.magnetUri = "btih:123&"
        remoteTorrentDownloading.remoteStatusText = "downloading"
        val remoteTorrentFinished = Torrent("Remote")
        remoteTorrentFinished.magnetUri = "btih:321&"
        remoteTorrentFinished.remoteStatusText = "finished"
        val localTorrentUploading = Torrent("Remote")
        localTorrentUploading.magnetUri = "btih:321&"
        localTorrentUploading.remoteStatusText = "uploading (3/7)"
        every {multifileHosterService.remoteTorrents} returns (listOf(remoteTorrentDownloading, remoteTorrentFinished))
        torrentMetaService.updateTorrent(localTorrentUploading)
        // When
        torrentMetaService.refreshTorrents()
        torrentMetaService.refreshTorrents()
        // Then
        val activeTorrents = torrentMetaService.activeTorrents
        Assertions.assertThat(activeTorrents).containsExactlyInAnyOrder(remoteTorrentDownloading, localTorrentUploading)
    }
}