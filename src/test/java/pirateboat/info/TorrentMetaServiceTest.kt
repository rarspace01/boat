package pirateboat.info

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pirateboat.multifileHoster.MultifileHosterService
import pirateboat.torrent.Torrent

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
        remoteTorrentDownloading.status = "downloading"
        val remoteTorrentFinished = Torrent("Remote")
        remoteTorrentFinished.magnetUri = "btih:321&"
        remoteTorrentFinished.status = "finished"
        val localTorrentUploading = Torrent("Remote")
        localTorrentUploading.magnetUri = "btih:321&"
        localTorrentUploading.status = "uploading (3/7)"
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
        remoteTorrentDownloading.status = "downloading"
        val remoteTorrentFinished = Torrent("Remote")
        remoteTorrentFinished.magnetUri = "btih:321&"
        remoteTorrentFinished.status = "finished"
        val localTorrentUploading = Torrent("Remote")
        localTorrentUploading.magnetUri = "btih:321&"
        localTorrentUploading.status = "uploading (3/7)"
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