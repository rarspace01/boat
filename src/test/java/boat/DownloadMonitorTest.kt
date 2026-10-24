package boat

import boat.info.CloudFileService
import boat.info.CloudService
import boat.info.MediaItem
import boat.info.MediaProxyService
import boat.info.MediaType
import boat.info.QueueService
import boat.model.Transfer
import boat.model.TransferStatus
import boat.multifileHoster.MultifileHosterService
import boat.services.ConfigurationService
import boat.services.TransferService
import boat.torrent.Torrent
import boat.torrent.TorrentSearchEngineService
import boat.utilities.HttpHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.cache.CacheManager

internal class DownloadMonitorTest {
    private val torrentSearchEngineService: TorrentSearchEngineService = mockk(relaxed = true)
    private val cloudService: CloudService = mockk(relaxed = true)
    private val multifileHosterService: MultifileHosterService = mockk(relaxed = true)
    private val cloudFileService: CloudFileService = mockk(relaxed = true)
    private val cacheManager: CacheManager = mockk(relaxed = true)
    private val queueService: QueueService = mockk(relaxed = true)
    private val transferService: TransferService = mockk(relaxed = true)
    private val configurationService: ConfigurationService = mockk(relaxed = true)
    private val httpHelper: HttpHelper = mockk(relaxed = true)
    private val mediaProxyService: MediaProxyService = mockk(relaxed = true)

    private lateinit var downloadMonitor: DownloadMonitor

    @BeforeEach
    fun setUp() {
        downloadMonitor = DownloadMonitor(
            torrentSearchEngineService,
            cloudService,
            multifileHosterService,
            cloudFileService,
            cacheManager,
            queueService,
            transferService,
            configurationService,
            httpHelper,
            mediaProxyService
        )
        every { cloudService.isTargetFolderReady } returns true
        every { configurationService.isSearchMode() } returns true
        every { multifileHosterService.remoteTorrents } returns emptyList()
        every { multifileHosterService.getRemainingTrafficInMB() } returns 200_000.0
    }

    @Test
    fun shouldNotAskTheHosterIfTheQueueIsEmpty() {
        every { queueService.getQueue() } returns emptyList()

        downloadMonitor.checkForQueueEntries()

        verify(exactly = 0) { multifileHosterService.remoteTorrents }
        verify(exactly = 0) { multifileHosterService.getRemainingTrafficInMB() }
    }

    @Test
    fun shouldNotPickTheSameQueueEntryWhileItIsStillSearched() {
        val queueItem = MediaItem(title = "Arapef", year = 2009, type = MediaType.Movie, id = "{tmdb-19995}")
        val searching = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        every { queueService.getQueue() } returns listOf(queueItem)
        every { cloudService.findExistingFiles(any()) } returns emptyList()
        every { torrentSearchEngineService.searchTorrents("Arapef 2009") } answers {
            searching.countDown()
            release.await()
            emptyList()
        }

        val first = Thread { downloadMonitor.checkForQueueEntries() }.apply { start() }
        searching.await()
        downloadMonitor.checkForQueueEntries()
        release.countDown()
        first.join()

        verify(exactly = 1) { torrentSearchEngineService.searchTorrents("Arapef 2009") }
        verify(exactly = 1) { queueService.remove(queueItem) }
    }

    @Test
    fun shouldSearchMovieAndIncludeYearAndRemoteMediaId() {
        val queueItem = MediaItem(title = "Arapef", year = 2009)
        val resolvedMedia = MediaItem(title = "Arapef", year = 2009, type = MediaType.Movie, id = "{tmdb-19995}")
        val torrent = Torrent("Arapef 2009 1080p")

        every { queueService.getQueue() } returns listOf(queueItem)
        every { mediaProxyService.findBestMatch("Arapef", 2009) } returns resolvedMedia
        every { cloudService.findExistingFiles("Arapef") } returns emptyList()
        every { cloudService.findExistingFiles("{tmdb-19995}") } returns emptyList()
        every { torrentSearchEngineService.searchTorrents("Arapef 2009") } returns listOf(torrent)

        downloadMonitor.checkForQueueEntries()

        verify(exactly = 1) { torrentSearchEngineService.searchTorrents("Arapef 2009") }
        verify(exactly = 1) { multifileHosterService.addTorrentToTransfer(torrent, "{tmdb-19995}", MediaType.Movie) }
        verify(exactly = 1) { queueService.remove(queueItem) }
    }

    @Test
    fun shouldSearchSeriesWithoutYearAndIncludeRemoteMediaId() {
        val queueItem = MediaItem(title = "Bacozami Bap: Season 1", year = 2008)
        val resolvedMedia = MediaItem(title = "Bacozami Bap: Season 1", year = 2008, type = MediaType.Series, id = "{tvdb-81189}")
        val torrent = Torrent("Bacozami Bap S01 1080p")

        every { queueService.getQueue() } returns listOf(queueItem)
        every { mediaProxyService.findBestMatch("Bacozami Bap: Season 1", 2008) } returns resolvedMedia
        every { cloudService.findExistingFiles("Bacozami Bap Season 1") } returns emptyList()
        every { cloudService.findExistingFiles("{tvdb-81189}") } returns emptyList()
        every { torrentSearchEngineService.searchTorrents("Bacozami Bap Season 1") } returns listOf(torrent)

        downloadMonitor.checkForQueueEntries()

        verify(exactly = 1) { torrentSearchEngineService.searchTorrents("Bacozami Bap Season 1") }
        verify(exactly = 1) { multifileHosterService.addTorrentToTransfer(torrent, "{tvdb-81189}", MediaType.Series) }
        verify(exactly = 1) { queueService.remove(queueItem) }
    }

    @Test
    fun shouldSkipDownloadIfFilesAlreadyExistByRemoteMediaId() {
        val queueItem = MediaItem(title = "Arapef", year = 2009)
        val resolvedMedia = MediaItem(title = "Arapef", year = 2009, type = MediaType.Movie, id = "{tmdb-19995}")

        every { queueService.getQueue() } returns listOf(queueItem)
        every { mediaProxyService.findBestMatch("Arapef", 2009) } returns resolvedMedia
        every { cloudService.findExistingFiles("Arapef") } returns emptyList()
        every { cloudService.findExistingFiles("{tmdb-19995}") } returns listOf("Arapef (2009) {tmdb-19995}.mkv")

        downloadMonitor.checkForQueueEntries()

        verify(exactly = 0) { torrentSearchEngineService.searchTorrents(any()) }
        verify(exactly = 0) { multifileHosterService.addTorrentToTransfer(any(), any(), any()) }
        verify(exactly = 1) { queueService.remove(queueItem) }
    }

    @Test
    fun shouldUseAlreadyMatchedQueueItemWithoutSearchingTheMediaProxy() {
        val queueItem = MediaItem(title = "Arapef", year = 2009, type = MediaType.Movie, id = "{tmdb-19995}")
        val torrent = Torrent("Arapef 2009 1080p")

        every { queueService.getQueue() } returns listOf(queueItem)
        every { cloudService.findExistingFiles("Arapef") } returns emptyList()
        every { cloudService.findExistingFiles("{tmdb-19995}") } returns emptyList()
        every { torrentSearchEngineService.searchTorrents("Arapef 2009") } returns listOf(torrent)

        downloadMonitor.checkForQueueEntries()

        verify(exactly = 0) { mediaProxyService.findBestMatch(any(), any()) }
        verify(exactly = 1) { multifileHosterService.addTorrentToTransfer(torrent, "{tmdb-19995}", MediaType.Movie) }
        verify(exactly = 1) { queueService.remove(queueItem) }
    }

    @Test
    fun shouldFallbackWhenMediaProxyReturnsNoResults() {
        val queueItem = MediaItem(title = "Custom Show!", year = 2021, id = "{custom-id}")
        val torrent = Torrent("Custom Show 2021")

        every { queueService.getQueue() } returns listOf(queueItem)
        every { mediaProxyService.findBestMatch("Custom Show!", 2021) } returns null
        every { cloudService.findExistingFiles("Custom Show") } returns emptyList()
        every { cloudService.findExistingFiles("{custom-id}") } returns emptyList()
        every { torrentSearchEngineService.searchTorrents("Custom Show 2021") } returns listOf(torrent)

        downloadMonitor.checkForQueueEntries()

        verify(exactly = 1) { torrentSearchEngineService.searchTorrents("Custom Show 2021") }
        verify(exactly = 1) { multifileHosterService.addTorrentToTransfer(torrent, "{custom-id}", null) }
        verify(exactly = 1) { queueService.remove(queueItem) }
    }

    @Test
    fun shouldFallbackToFullNameWhenYearIsPartOfTheTitle() {
        val queueItem = MediaItem(title = "Space", year = 1999)
        val resolvedMedia = MediaItem(title = "Space: 1999", year = 1975, type = MediaType.Series, id = "{tvdb-77853}")
        val torrent = Torrent("Space 1999 S01")

        every { queueService.getQueue() } returns listOf(queueItem)
        every { mediaProxyService.findBestMatch("Space", 1999) } returns resolvedMedia
        every { torrentSearchEngineService.searchTorrents("Space 1999") } returns listOf(torrent)

        downloadMonitor.checkForQueueEntries()

        verify(exactly = 1) { multifileHosterService.addTorrentToTransfer(torrent, "{tvdb-77853}", MediaType.Series) }
    }

    private fun finishedTorrent(remoteId: String) = Torrent("Premiumize").apply {
        name = "Finished $remoteId"
        this.remoteId = remoteId
        remoteTransferStatus = TransferStatus.READY_TO_BE_DOWNLOADED
    }

    private fun queueOneMovie(): MediaItem {
        val queueItem = MediaItem(title = "Arapef", year = 2009, type = MediaType.Movie, id = "{tmdb-19995}")
        every { queueService.getQueue() } returns listOf(queueItem)
        every { cloudService.findExistingFiles(any()) } returns emptyList()
        return queueItem
    }

    @Test
    fun shouldWaitWhileAFinishedTorrentIsDownloaded() {
        queueOneMovie()
        every { multifileHosterService.remoteTorrents } returns listOf(finishedTorrent("r1"))
        every { transferService.getAll() } returns listOf(Transfer(remoteId = "r1", transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED))

        downloadMonitor.checkForQueueEntries()

        verify(exactly = 0) { torrentSearchEngineService.searchTorrents(any()) }
    }

    @Test
    fun shouldNotWaitForFinishedTorrentsWhichAreNeverDownloaded() {
        val queueItem = queueOneMovie()
        every { multifileHosterService.remoteTorrents } returns listOf(finishedTorrent("failed"), finishedTorrent("orphan"))
        every { transferService.getAll() } returns listOf(Transfer(remoteId = "failed", transferStatus = TransferStatus.UPLOAD_TO_DRIVE_FAILED))

        downloadMonitor.checkForQueueEntries()

        verify(exactly = 1) { torrentSearchEngineService.searchTorrents("Arapef 2009") }
        verify(exactly = 1) { queueService.remove(queueItem) }
    }

    @Test
    fun shouldWaitWithoutEnoughTraffic() {
        queueOneMovie()
        every { multifileHosterService.getRemainingTrafficInMB() } returns 1024.0

        downloadMonitor.checkForQueueEntries()

        verify(exactly = 0) { torrentSearchEngineService.searchTorrents(any()) }
    }
}
