package boat

import boat.info.CloudFileService
import boat.info.CloudService
import boat.info.MediaItem
import boat.info.MediaProxyService
import boat.info.MediaType
import boat.info.QueueService
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
        every { cloudService.isCloudTokenValid } returns true
        every { configurationService.isSearchMode() } returns true
        every { multifileHosterService.remoteTorrents } returns emptyList()
        every { multifileHosterService.getRemainingTrafficInMB() } returns 200_000.0
    }

    @Test
    fun shouldSearchMovieAndIncludeYearAndRemoteMediaId() {
        val queueItem = MediaItem(title = "Avatar", year = 2009)
        val resolvedMedia = MediaItem(title = "Avatar", year = 2009, type = MediaType.Movie, id = "{tmdb-19995}")
        val torrent = Torrent("Avatar 2009 1080p")

        every { queueService.getQueue() } returns listOf(queueItem)
        every { mediaProxyService.search("Avatar", 2009) } returns listOf(resolvedMedia)
        every { cloudService.findExistingFiles("Avatar") } returns emptyList()
        every { cloudService.findExistingFiles("{tmdb-19995}") } returns emptyList()
        every { torrentSearchEngineService.searchTorrents("Avatar 2009") } returns listOf(torrent)

        downloadMonitor.checkForQueueEntries()

        verify(exactly = 1) { torrentSearchEngineService.searchTorrents("Avatar 2009") }
        verify(exactly = 1) { multifileHosterService.addTorrentToTransfer(torrent, "{tmdb-19995}") }
        verify(exactly = 1) { queueService.remove(queueItem) }
        verify(exactly = 1) { queueService.saveQueue() }
    }

    @Test
    fun shouldSearchSeriesWithoutYearAndIncludeRemoteMediaId() {
        val queueItem = MediaItem(title = "Breaking Bad: Season 1", year = 2008)
        val resolvedMedia = MediaItem(title = "Breaking Bad: Season 1", year = 2008, type = MediaType.Series, id = "{tvdb-81189}")
        val torrent = Torrent("Breaking Bad S01 1080p")

        every { queueService.getQueue() } returns listOf(queueItem)
        every { mediaProxyService.search("Breaking Bad: Season 1", 2008) } returns listOf(resolvedMedia)
        every { cloudService.findExistingFiles("Breaking Bad Season 1") } returns emptyList()
        every { cloudService.findExistingFiles("{tvdb-81189}") } returns emptyList()
        every { torrentSearchEngineService.searchTorrents("Breaking Bad Season 1") } returns listOf(torrent)

        downloadMonitor.checkForQueueEntries()

        verify(exactly = 1) { torrentSearchEngineService.searchTorrents("Breaking Bad Season 1") }
        verify(exactly = 1) { multifileHosterService.addTorrentToTransfer(torrent, "{tvdb-81189}") }
        verify(exactly = 1) { queueService.remove(queueItem) }
        verify(exactly = 1) { queueService.saveQueue() }
    }

    @Test
    fun shouldSkipDownloadIfFilesAlreadyExistByRemoteMediaId() {
        val queueItem = MediaItem(title = "Avatar", year = 2009)
        val resolvedMedia = MediaItem(title = "Avatar", year = 2009, type = MediaType.Movie, id = "{tmdb-19995}")

        every { queueService.getQueue() } returns listOf(queueItem)
        every { mediaProxyService.search("Avatar", 2009) } returns listOf(resolvedMedia)
        every { cloudService.findExistingFiles("Avatar") } returns emptyList()
        every { cloudService.findExistingFiles("{tmdb-19995}") } returns listOf("Avatar (2009) {tmdb-19995}.mkv")

        downloadMonitor.checkForQueueEntries()

        verify(exactly = 0) { torrentSearchEngineService.searchTorrents(any()) }
        verify(exactly = 0) { multifileHosterService.addTorrentToTransfer(any(), any()) }
        verify(exactly = 1) { queueService.remove(queueItem) }
        verify(exactly = 1) { queueService.saveQueue() }
    }

    @Test
    fun shouldFallbackWhenMediaProxyReturnsNoResults() {
        val queueItem = MediaItem(title = "Custom Show!", year = 2021, id = "{custom-id}")
        val torrent = Torrent("Custom Show 2021")

        every { queueService.getQueue() } returns listOf(queueItem)
        every { mediaProxyService.search("Custom Show!", 2021) } returns emptyList()
        every { cloudService.findExistingFiles("Custom Show") } returns emptyList()
        every { cloudService.findExistingFiles("{custom-id}") } returns emptyList()
        every { torrentSearchEngineService.searchTorrents("Custom Show 2021") } returns listOf(torrent)

        downloadMonitor.checkForQueueEntries()

        verify(exactly = 1) { torrentSearchEngineService.searchTorrents("Custom Show 2021") }
        verify(exactly = 1) { multifileHosterService.addTorrentToTransfer(torrent, "{custom-id}") }
        verify(exactly = 1) { queueService.remove(queueItem) }
        verify(exactly = 1) { queueService.saveQueue() }
    }
}