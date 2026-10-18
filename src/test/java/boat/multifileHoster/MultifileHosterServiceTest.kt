package boat.multifileHoster

import boat.ContentRenamer
import boat.info.CloudFileService
import boat.info.CloudService
import boat.info.MediaItem
import boat.info.MediaProxyService
import boat.info.MediaType
import boat.model.Transfer
import boat.model.TransferStatus
import boat.services.TransferService
import boat.torrent.Torrent
import boat.torrent.TorrentFile
import boat.utilities.HttpHelper
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.cache.CacheManager

internal class MultifileHosterServiceTest {

    private val httpHelper: HttpHelper = mockk(relaxed = true)
    private val transferService: TransferService = mockk(relaxed = true)
    private val cloudService: CloudService = mockk(relaxed = true)
    private val cacheManager: CacheManager = mockk(relaxed = true)
    private val cloudFileService: CloudFileService = mockk(relaxed = true)
    private val mediaProxyService: MediaProxyService = mockk(relaxed = true)
    private val contentRenamer: ContentRenamer = mockk(relaxed = true)
    lateinit var multifileHosterService: MultifileHosterService

    @BeforeEach
    fun beforeEach() {
        every { httpHelper.getPage(match { it.contains("account/info") }) } returns "{\"limit_used\": 0.1}"
        every { httpHelper.getPage(match { it.contains("premiumize.me/account?") }) } returns "<html><body></body></html>"
        multifileHosterService = MultifileHosterService(
            httpHelper,
            transferService,
            cloudService,
            cacheManager,
            cloudFileService,
            mediaProxyService,
            contentRenamer
        )
    }

    @Test
    fun extractRemoteIdFromMessage() {
        // Given
        val transferFeedbackMessage =
            "{\"status\":\"success\",\"id\":\"0jXfg462WZz2EkSpFKjU_g\",\"name\":\"magnet:?xt=urn:btih:123456789\",\"type\":\"torrent\"}"
        val transfer = Transfer(feedbackMessage = transferFeedbackMessage)
        // When
        val remoteIdFromMessage =
            multifileHosterService.extractRemoteIdFromMessage(transfer)
        // Then
        assertThat(remoteIdFromMessage).isEqualTo("0jXfg462WZz2EkSpFKjU_g")
    }

    @Test
    fun extractOtherRemoteIdFromMessage() {
        // Given
        val transferFeedbackMessage =
            "{\"status\":\"success\",\"id\":\"yxGo09Kq-RkayKbAxdDyFQ\",\"name\":\"magnet:?xt=urn:btih:12345678912316782351328765\",\"type\":\"torrent\"}"
        val transfer = Transfer(feedbackMessage = transferFeedbackMessage)
        // When
        val remoteIdFromMessage =
            multifileHosterService.extractRemoteIdFromMessage(transfer)
        // Then
        assertThat(remoteIdFromMessage).isEqualTo("yxGo09Kq-RkayKbAxdDyFQ")
    }

    @Test
    fun transferMatchedTorrentByName() {
        // Given
        val transfer = Transfer()
        transfer.name = "the adventures of buckaroo banzai across the 8th dimension (1984) (1080p bluray .."
        val torrent = Torrent("Test")
        torrent.name = "The Adventures of Buckaroo Banzai Across the 8th Dimension (1984) (1080p BluRay x265 HEVC 10bit AAC 5.1 Tigole)"
        torrent.remoteId = "remoteId"
        // When

        val matched =
            multifileHosterService.transferMatchedTorrentByName(transfer, torrent, 2)
        // Then
        assertThat(matched).isTrue
    }

    @Test
    fun shouldGetETABeforeFirstFile() {
        // Given
        val torrent = Torrent("Test")
        torrent.sizeInMB = 1000.0
        val torrentFile = TorrentFile(
            id = "", name = "",
            filesize = (1024 * 1024 * 500).toLong(),
            url = ""
        )
        // When
        val uploadStatusString = multifileHosterService.getUploadStatusString(torrent, listOf(torrentFile), 0, null)
        // Then
        assertThat(uploadStatusString).doesNotMatch("Uploading: 0/1 done ETA: 00:00:00")
    }

    @Test
    fun shouldBuildProperFilenames() {
        // When
        val filename = multifileHosterService
            .buildFilename("www.url.lol - Movie Title 2018", "www.movie-url.lol-movie_title_2018.mkv")
        // Then
        assertThat(filename).isEqualTo("Movie.Title.2018.mkv")
    }

    @Test
    fun shouldBuildProperFilenamesWithoutQuotesAndTorrentInName() {
        // When
        val filename = multifileHosterService
            .buildFilename("\"www.url.lol - Movie Title 2018.torrent\"", "www.movie-url.lol-movie_title_2018.mkv")
        // Then
        assertThat(filename).isEqualTo("Movie.Title.2018.mkv")
    }

    @Test
    fun shouldBuildProperFilenameFromFileIfEmptyName() {
        // When
        val filename = multifileHosterService
            .buildFilename("", "www.movie-url.lol-movie.title.2018.mkv")
        // Then
        assertThat(filename).isEqualTo("movie.title.2018.mkv")
    }

    @Test
    fun shouldBuildProperFilenameFromFileIfNullName() {
        // When
        val filename = multifileHosterService
            .buildFilename(null, "www.movie-url.lol-movie.title.2018.mkv")
        // Then
        assertThat(filename).isEqualTo("movie.title.2018.mkv")
    }

    @Disabled
    @Test
    fun getTorrentToBeDownloaded() {
        // Given
        // When
        val torrentToBeDownloaded = multifileHosterService.getTorrentToBeDownloaded()
        // Then
        Assertions.assertNotNull(torrentToBeDownloaded)
    }

    @Test
    fun shouldNotUseMagnetUriAsTransferNameWhenAddingTorrent() {
        // Given
        val savedTransfer = slot<Transfer>()
        every { transferService.save(capture(savedTransfer)) } just Runs
        val torrent = Torrent.of("magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&tr=udp%3A%2F%2Ftracker.org%3A6969&dn=Some.Movie.2020")
        // When
        multifileHosterService.addTorrentToTransfer(torrent)
        // Then
        assertThat(savedTransfer.captured.name).isEqualTo("Some.Movie.2020")
        assertThat(savedTransfer.captured.uri).isEqualTo(torrent.magnetUri)
    }

    @Test
    fun shouldNotUseMagnetUriAsTransferNameWhenAddingTorrentWithoutDn() {
        // Given
        val savedTransfer = slot<Transfer>()
        every { transferService.save(capture(savedTransfer)) } just Runs
        val torrent = Torrent.of("magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2")
        // When
        multifileHosterService.addTorrentToTransfer(torrent)
        // Then
        assertThat(savedTransfer.captured.name).doesNotContain("magnet:")
    }

    @Test
    fun shouldGetTransferToBeDownloaded() {
        val transfer1 = Transfer(name = "T1", transferStatus = TransferStatus.ADDED)
        val transfer2 = Transfer(name = "T2", transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED, sizeInBytes = 1000L)
        val transfer3 = Transfer(name = "T3", transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED, sizeInBytes = 2000L)

        every { transferService.getAll() } returns listOf(transfer1, transfer2, transfer3)

        val result = multifileHosterService.getTransferToBeDownloaded()
        assertThat(result).isEqualTo(transfer2)
    }

    @Test
    fun shouldDownloadSingleFileTransferUsingMediaProxyAndContentRenamer() {
        val transfer = Transfer(
            name = "Inception.2010.1080p.mkv",
            transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED,
            remoteId = "remote-123",
            remoteMediaId = "{tmdb-27205}",
            source = "Premiumize"
        )
        val mediaItem = MediaItem(title = "Inception", year = 2010, type = MediaType.Movie, id = "{tmdb-27205}")

        every { transferService.getAll() } returns listOf(transfer)
        every { mediaProxyService.findByRemoteMediaId("{tmdb-27205}") } returns mediaItem
        every { contentRenamer.getSafeTitle(mediaItem) } returns "Inception"
        every { contentRenamer.getTargetFile(any(), mediaItem, "Inception", null, null, any()) } returns "/destination/Movies/I/Inception {tmdb-27205}.mkv"

        val torrentFile = TorrentFile(name = "Inception.mkv", url = "http://premiumize.me/dl/inception.mkv", filesize = 1000L)
        val folderResponse = "{\"status\":\"success\",\"content\":[{\"id\":\"f1\",\"name\":\"Inception.mkv\",\"type\":\"file\",\"size\":1000,\"link\":\"http://premiumize.me/dl/inception.mkv\"}]}"
        every { httpHelper.getPage(match { it.contains("folder/list") }) } returns folderResponse

        multifileHosterService.checkForDownloadableTorrentsAndDownloadTheFirst()

        io.mockk.verify { transferService.delete(transfer) }
    }

    @Test
    fun shouldDownloadMultiFileSeriesTransferUsingMediaProxyAndContentRenamer() {
        val transfer = Transfer(
            name = "Breaking.Bad.S01",
            transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED,
            remoteId = "remote-456",
            remoteMediaId = "{tvdb-81189}",
            source = "Premiumize"
        )
        val mediaItem = MediaItem(title = "Breaking Bad", year = 2008, type = MediaType.Series, id = "{tvdb-81189}")

        every { transferService.getAll() } returns listOf(transfer)
        every { mediaProxyService.findByRemoteMediaId("{tvdb-81189}") } returns mediaItem
        every { contentRenamer.getSafeTitle(mediaItem) } returns "Breaking Bad"
        every { contentRenamer.extractSeason(any()) } returns 1
        every { contentRenamer.extractEpisode(any(), mediaItem) } returns 1
        every { contentRenamer.getTargetFile(any(), mediaItem, "Breaking Bad", 1, 1, any()) } returns "/destination/Series-Shows/B/Breaking Bad {tvdb-81189}/Season 1/Breaking Bad.S01E01.mkv"

        val folderResponse = "{\"status\":\"success\",\"content\":[" +
                "{\"id\":\"f1\",\"name\":\"Breaking.Bad.S01E01.mkv\",\"type\":\"file\",\"size\":1000,\"link\":\"http://premiumize.me/dl/bb01.mkv\"}," +
                "{\"id\":\"f2\",\"name\":\"Breaking.Bad.S01E02.mkv\",\"type\":\"file\",\"size\":1000,\"link\":\"http://premiumize.me/dl/bb02.mkv\"}" +
                "]}"
        every { httpHelper.getPage(match { it.contains("folder/list") }) } returns folderResponse

        multifileHosterService.checkForDownloadableTorrentsAndDownloadTheFirst()

        io.mockk.verify { transferService.delete(transfer) }
    }
}
