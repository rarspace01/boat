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
import boat.torrent.TorrentType
import boat.utilities.HttpHelper
import boat.utilities.RcloneUploader
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
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
    private val rcloneUploader: RcloneUploader = mockk()
    lateinit var multifileHosterService: MultifileHosterService

    @BeforeEach
    fun beforeEach() {
        every { httpHelper.getPage(match { it.contains("account/info") }) } returns "{\"limit_used\": 0.1}"
        every { httpHelper.getPage(match { it.contains("premiumize.me/account?") }) } returns "<html><body></body></html>"
        multifileHosterService = spyk(
            MultifileHosterService(
                httpHelper,
                transferService,
                cloudService,
                cacheManager,
                cloudFileService,
                mediaProxyService,
                contentRenamer,
                rcloneUploader
            )
        )
        every { rcloneUploader.copyUrl(any(), any()) } returns true
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
    fun shouldPrefixTransferNameWithMediaTitleWhenRemoteMediaIdIsPresent() {
        // Given
        val savedTransfer = slot<Transfer>()
        every { transferService.save(capture(savedTransfer)) } just Runs
        every { mediaProxyService.findByRemoteMediaId("{tmdb-27205}", "Inception.2010.1080p", null) } returns
                MediaItem(title = "Inception", year = 2010, type = MediaType.Movie, id = "{tmdb-27205}")
        val torrent = Torrent.of("magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&dn=Inception.2010.1080p")
        // When
        multifileHosterService.addTorrentToTransfer(torrent, "{tmdb-27205}")
        // Then
        assertThat(savedTransfer.captured.name).isEqualTo("Inception | Inception.2010.1080p")
        assertThat(savedTransfer.captured.hosterName()).isEqualTo("Inception.2010.1080p")
        assertThat(savedTransfer.captured.remoteMediaId).isEqualTo("{tmdb-27205}")
        assertThat(savedTransfer.captured.mediaType).isEqualTo(MediaType.Movie)
    }

    @Test
    fun shouldUseGivenMediaTypeForRemoteMediaIdLookupWhenAddingTorrent() {
        // Given
        val savedTransfer = slot<Transfer>()
        every { transferService.save(capture(savedTransfer)) } just Runs
        every { mediaProxyService.findByRemoteMediaId("{tmdb-1396}", any(), MediaType.Series) } returns
                MediaItem(title = "Breaking Bad", year = 2008, type = MediaType.Series, id = "{tmdb-1396}")
        val torrent = Torrent.of("magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&dn=Breaking.Bad.S01")
        // When
        multifileHosterService.addTorrentToTransfer(torrent, "{tmdb-1396}", MediaType.Series)
        // Then
        assertThat(savedTransfer.captured.name).isEqualTo("Breaking Bad | Breaking.Bad.S01")
        assertThat(savedTransfer.captured.mediaType).isEqualTo(MediaType.Series)
    }

    @Test
    fun shouldKeepGivenMediaTypeWhenMediaItemForRemoteMediaIdIsNotFound() {
        val savedTransfer = slot<Transfer>()
        every { transferService.save(capture(savedTransfer)) } just Runs
        every { mediaProxyService.findByRemoteMediaId(any(), any(), any()) } returns null
        val torrent = Torrent.of("magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&dn=Breaking.Bad.S01")

        multifileHosterService.addTorrentToTransfer(torrent, "{tmdb-1396}", MediaType.Series)

        assertThat(savedTransfer.captured.mediaType).isEqualTo(MediaType.Series)
    }

    @Test
    fun shouldKeepHosterNameWhenMediaItemForRemoteMediaIdIsNotFound() {
        // Given
        val savedTransfer = slot<Transfer>()
        every { transferService.save(capture(savedTransfer)) } just Runs
        every { mediaProxyService.findByRemoteMediaId(any(), any(), any()) } returns null
        val torrent = Torrent.of("magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&dn=Inception.2010.1080p")
        // When
        multifileHosterService.addTorrentToTransfer(torrent, "{tmdb-27205}")
        // Then
        assertThat(savedTransfer.captured.name).isEqualTo("Inception.2010.1080p")
    }

    @Test
    fun shouldMatchTorrentByNameIgnoringMediaTitlePrefix() {
        val transfer = Transfer(name = "Inception | Inception.2010.1080p", remoteMediaId = "{tmdb-27205}")
        val torrent = Torrent("Premiumize").apply { name = "Inception.2010.1080p" }

        assertThat(multifileHosterService.transferMatchedTorrentByName(transfer, torrent)).isTrue()
    }

    @Test
    fun shouldKeepMediaTitlePrefixWhenHosterNameIsUpdated() {
        val transfer = Transfer(name = "Inception | Inception.2010.1080p", remoteMediaId = "{tmdb-27205}")

        transfer.updateHosterName("Inception.2010.1080p.BluRay")

        assertThat(transfer.name).isEqualTo("Inception | Inception.2010.1080p.BluRay")
        assertThat(transfer.hosterName()).isEqualTo("Inception.2010.1080p.BluRay")
    }

    @Test
    fun shouldNotStripSeparatorFromTransferWithoutRemoteMediaId() {
        val transfer = Transfer(name = "Some | Name")

        assertThat(transfer.hosterName()).isEqualTo("Some | Name")
        transfer.updateHosterName("Other")
        assertThat(transfer.name).isEqualTo("Other")
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
        every { mediaProxyService.findByRemoteMediaId("{tmdb-27205}", any(), any()) } returns mediaItem
        every { contentRenamer.getSafeTitle(mediaItem) } returns "Inception"
        every { contentRenamer.extractSeasonAndEpisode(any(), mediaItem) } returns Pair(null, null)
        every { contentRenamer.getTargetFile(any(), mediaItem, "Inception", null, null, any()) } returns "/destination/Movies/I/Inception {tmdb-27205}.mkv"

        val torrentFile = TorrentFile(name = "Inception.mkv", url = "http://premiumize.me/dl/inception.mkv", filesize = 1000L)
        val folderResponse = "{\"status\":\"success\",\"content\":[{\"id\":\"f1\",\"name\":\"Inception.mkv\",\"type\":\"file\",\"size\":1000,\"link\":\"http://premiumize.me/dl/inception.mkv\"}]}"
        every { httpHelper.getPage(match { it.contains("folder/list") }) } returns folderResponse

        multifileHosterService.checkForDownloadableTorrentsAndDownloadTheFirst()

        io.mockk.verify { transferService.delete(transfer) }
    }

    @Test
    fun shouldUseStoredMediaTypeForRemoteMediaIdLookup() {
        val transfer = Transfer(
            name = "Inception | Inception.2010.1080p",
            transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED,
            remoteId = "remote-123",
            remoteMediaId = "{tmdb-27205}",
            mediaType = MediaType.Movie,
            source = "Premiumize"
        )
        val mediaItem = MediaItem(title = "Inception", year = 2010, type = MediaType.Movie, id = "{tmdb-27205}")

        every { transferService.getAll() } returns listOf(transfer)
        every { mediaProxyService.findByRemoteMediaId("{tmdb-27205}", "Inception.2010.1080p", MediaType.Movie) } returns mediaItem
        every { contentRenamer.getSafeTitle(mediaItem) } returns "Inception"
        every { contentRenamer.extractSeasonAndEpisode(any(), mediaItem) } returns Pair(null, null)
        every { contentRenamer.getTargetFile(any(), mediaItem, "Inception", null, null, any()) } returns "/destination/Movies/I/Inception {tmdb-27205}.mkv"
        val folderResponse = "{\"status\":\"success\",\"content\":[{\"id\":\"f1\",\"name\":\"Inception.mkv\",\"type\":\"file\",\"size\":1000,\"link\":\"http://premiumize.me/dl/inception.mkv\"}]}"
        every { httpHelper.getPage(match { it.contains("folder/list") }) } returns folderResponse

        multifileHosterService.checkForDownloadableTorrentsAndDownloadTheFirst()

        io.mockk.verify { mediaProxyService.findByRemoteMediaId("{tmdb-27205}", "Inception.2010.1080p", MediaType.Movie) }
        io.mockk.verify { contentRenamer.getTargetFile(any(), mediaItem, "Inception", null, null, any()) }
    }

    @Test
    fun shouldUseSeasonAndEpisodeFromContentRenamerForSeries() {
        val transfer = Transfer(
            name = "Breaking.Bad.E05",
            transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED,
            remoteId = "remote-456",
            remoteMediaId = "{tvdb-81189}",
            source = "Premiumize"
        )
        val mediaItem = MediaItem(title = "Breaking Bad", year = 2008, type = MediaType.Series, id = "{tvdb-81189}")

        every { transferService.getAll() } returns listOf(transfer)
        every { mediaProxyService.findByRemoteMediaId("{tvdb-81189}", any(), any()) } returns mediaItem
        every { contentRenamer.getSafeTitle(mediaItem) } returns "Breaking Bad"
        every { contentRenamer.extractSeasonAndEpisode(any(), mediaItem) } returns Pair(1, 5)
        every { contentRenamer.getTargetFile(any(), mediaItem, "Breaking Bad", 1, 5, any()) } returns "/destination/Series-Shows/B/Breaking Bad {tvdb-81189}/Season 1/Breaking Bad.S01E05.mkv"
        val folderResponse = "{\"status\":\"success\",\"content\":[{\"id\":\"f1\",\"name\":\"Breaking.Bad.E05.mkv\",\"type\":\"file\",\"size\":1000,\"link\":\"http://premiumize.me/dl/bb05.mkv\"}]}"
        every { httpHelper.getPage(match { it.contains("folder/list") }) } returns folderResponse

        multifileHosterService.checkForDownloadableTorrentsAndDownloadTheFirst()

        io.mockk.verify { contentRenamer.getTargetFile(any(), mediaItem, "Breaking Bad", 1, 5, any()) }
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
        every { mediaProxyService.findByRemoteMediaId("{tvdb-81189}", any(), any()) } returns mediaItem
        every { contentRenamer.getSafeTitle(mediaItem) } returns "Breaking Bad"
        every { contentRenamer.extractSeasonAndEpisode(any(), mediaItem) } returns Pair(1, 1)
        every { contentRenamer.getTargetFile(any(), mediaItem, "Breaking Bad", 1, 1, any()) } returns "/destination/Series-Shows/B/Breaking Bad {tvdb-81189}/Season 1/Breaking Bad.S01E01.mkv"

        val folderResponse = "{\"status\":\"success\",\"content\":[" +
                "{\"id\":\"f1\",\"name\":\"Breaking.Bad.S01E01.mkv\",\"type\":\"file\",\"size\":1000,\"link\":\"http://premiumize.me/dl/bb01.mkv\"}," +
                "{\"id\":\"f2\",\"name\":\"Breaking.Bad.S01E02.mkv\",\"type\":\"file\",\"size\":1000,\"link\":\"http://premiumize.me/dl/bb02.mkv\"}" +
                "]}"
        every { httpHelper.getPage(match { it.contains("folder/list") }) } returns folderResponse

        multifileHosterService.checkForDownloadableTorrentsAndDownloadTheFirst()

        io.mockk.verify { transferService.delete(transfer) }
    }

    @Test
    fun shouldKeepTransferAndRemoteTorrentWhenSingleFileDownloadFails() {
        val transfer = Transfer(
            name = "Inception.2010.1080p.mkv",
            transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED,
            remoteId = "remote-123",
            source = "Premiumize"
        )
        every { transferService.getAll() } returns listOf(transfer)
        every { cloudService.buildDestinationPath(any(), any()) } returns Pair("I", "/destination/Movies/I/")
        every { httpHelper.getPage(match { it.contains("folder/list") }) } returns
                "{\"status\":\"success\",\"content\":[{\"id\":\"f1\",\"name\":\"Inception.mkv\",\"type\":\"file\",\"size\":1000,\"link\":\"http://premiumize.me/dl/inception.mkv\"}]}"
        every { rcloneUploader.copyUrl(any(), any()) } returns false

        multifileHosterService.checkForDownloadableTorrentsAndDownloadTheFirst()

        verify(exactly = 0) { transferService.delete(any()) }
        verify(exactly = 0) { httpHelper.getPage(match { it.contains("transfer/delete") }) }
        assertThat(transfer.transferStatus).isEqualTo(TransferStatus.READY_TO_BE_DOWNLOADED)
    }

    @Test
    fun shouldKeepTransferAndRemoteTorrentWhenOneFileOfMultiFileDownloadFails() {
        val transfer = Transfer(
            name = "Breaking.Bad.S01",
            transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED,
            remoteId = "remote-456",
            source = "Premiumize"
        )
        every { transferService.getAll() } returns listOf(transfer)
        every { cloudService.buildDestinationPath(any(), any()) } returns Pair("B", "/destination/Series-Shows/B/Breaking.Bad/")
        every { httpHelper.getPage(match { it.contains("folder/list") }) } returns "{\"status\":\"success\",\"content\":[" +
                "{\"id\":\"f1\",\"name\":\"Breaking.Bad.S01E01.mkv\",\"type\":\"file\",\"size\":1000,\"link\":\"http://premiumize.me/dl/bb01.mkv\"}," +
                "{\"id\":\"f2\",\"name\":\"Breaking.Bad.S01E02.mkv\",\"type\":\"file\",\"size\":1000,\"link\":\"http://premiumize.me/dl/bb02.mkv\"}" +
                "]}"
        every { rcloneUploader.copyUrl("http://premiumize.me/dl/bb02.mkv", any()) } returns false

        multifileHosterService.checkForDownloadableTorrentsAndDownloadTheFirst()

        verify(exactly = 0) { transferService.delete(any()) }
        verify(exactly = 0) { httpHelper.getPage(match { it.contains("transfer/delete") }) }
        assertThat(transfer.transferStatus).isEqualTo(TransferStatus.READY_TO_BE_DOWNLOADED)
    }

    @Test
    fun shouldDeleteTransferAndRemoteTorrentAfterSuccessfulDownload() {
        val transfer = Transfer(
            name = "Inception.2010.1080p.mkv",
            transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED,
            remoteId = "remote-123",
            source = "Premiumize"
        )
        every { transferService.getAll() } returns listOf(transfer)
        every { cloudService.buildDestinationPath(any(), any()) } returns Pair("I", "/destination/Movies/I/")
        every { httpHelper.getPage(match { it.contains("folder/list") }) } returns
                "{\"status\":\"success\",\"content\":[{\"id\":\"f1\",\"name\":\"Inception.mkv\",\"type\":\"file\",\"size\":1000,\"link\":\"http://premiumize.me/dl/inception.mkv\"}]}"

        multifileHosterService.checkForDownloadableTorrentsAndDownloadTheFirst()

        verify { transferService.delete(transfer) }
        verify { httpHelper.getPage(match { it.contains("transfer/delete") && it.contains("remote-123") }) }
    }

    @Test
    fun shouldUpdateIndexOfEachGivenLetter() {
        // When
        multifileHosterService.updateIndexForLetters(setOf("A", "0-9", "?", ""))
        // Then
        val refreshedLetters = mutableListOf<String>()
        verify { cloudService.buildDestinationPathWithTypeOfMediaWithoutSubFolders(capture(refreshedLetters), any()) }
        assertThat(refreshedLetters.distinct()).containsExactlyInAnyOrder("A", "0-9", "?")
        verify(exactly = 3 * TorrentType.entries.size) { cloudFileService.getFilesInPath(any()) }
    }

    @Test
    fun shouldUpdateIndexOfLetterAfterSuccessfulDownload() {
        val transfer = Transfer(
            name = "Inception.2010.1080p.mkv",
            transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED,
            remoteId = "remote-123",
            source = "Premiumize"
        )
        every { transferService.getAll() } returns listOf(transfer)
        every { cloudService.buildDestinationPath(any(), any()) } returns Pair("I", "/destination/Movies/I/")
        every { httpHelper.getPage(match { it.contains("folder/list") }) } returns
                "{\"status\":\"success\",\"content\":[{\"id\":\"f1\",\"name\":\"Inception.mkv\",\"type\":\"file\",\"size\":1000,\"link\":\"http://premiumize.me/dl/inception.mkv\"}]}"

        multifileHosterService.checkForDownloadableTorrentsAndDownloadTheFirst()

        verify { multifileHosterService.updateIndexForLetters(setOf("I")) }
    }

    @Test
    fun shouldOnlyUpdateUploadStatusOfDownloadedTransfer() {
        // a direct download url has no info hash, it must not match other transfers
        val otherTransfer = Transfer(
            name = "Other",
            uri = "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2",
            transferStatus = TransferStatus.DOWNLOADING_TO_MULTIHOSTER,
            source = "Premiumize"
        )
        val transfer = Transfer(
            name = "movie.mkv",
            uri = "https://example.com/movie.mkv",
            transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED,
            transferType = boat.model.TransferType.URL,
            source = "Premiumize"
        )
        every { transferService.getAll() } returns listOf(otherTransfer, transfer)
        every { cloudService.buildDestinationPath(any(), any()) } returns Pair("M", "/destination/transfer/M/")
        every { httpHelper.getPage(match { it.contains("folder/list") }) } returns
                "{\"status\":\"success\",\"content\":[{\"id\":\"f1\",\"name\":\"movie.mkv\",\"type\":\"file\",\"size\":1000,\"link\":\"http://premiumize.me/dl/movie.mkv\"}]}"

        multifileHosterService.checkForDownloadableTorrentsAndDownloadTheFirst()

        assertThat(otherTransfer.transferStatus).isEqualTo(TransferStatus.DOWNLOADING_TO_MULTIHOSTER)
        verify { transferService.delete(transfer) }
    }

    @Test
    fun shouldReuseRemainingTrafficForAShortTime() {
        multifileHosterService.getRemainingTrafficInMB()
        multifileHosterService.getRemainingTrafficInMB()
        multifileHosterService.getRemainingTrafficInMB()

        verify(exactly = 1) { httpHelper.getPage(match { it.contains("account/info") }) }
    }

    @Test
    fun shouldGiveUpWhenTheHosterKeepsReturningNoFiles() {
        val transfer = Transfer(
            name = "Inception.2010.1080p.mkv",
            transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED,
            remoteId = "remote-123",
            source = "Premiumize"
        )
        every { transferService.getAll() } returns listOf(transfer)
        every { httpHelper.getPage(match { it.contains("folder/list") }) } returns "{\"status\":\"success\",\"content\":[]}"

        repeat(MultifileHosterService.MAX_UPLOAD_TO_DRIVE_RETRIES + 1) {
            multifileHosterService.checkForDownloadableTorrentsAndDownloadTheFirst()
        }

        assertThat(transfer.transferStatus).isEqualTo(TransferStatus.UPLOAD_TO_DRIVE_FAILED)
        assertThat(transfer.feedbackMessage).contains("no files found")
        verify(exactly = 0) { rcloneUploader.copyUrl(any(), any()) }
        verify(exactly = 0) { transferService.delete(any()) }
    }

    @Test
    fun shouldCountFailedUploadsToDriveAndRetry() {
        val transfer = Transfer(
            name = "Inception.2010.1080p.mkv",
            transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED,
            remoteId = "remote-123",
            source = "Premiumize"
        )
        every { transferService.getAll() } returns listOf(transfer)
        every { cloudService.buildDestinationPath(any(), any()) } returns Pair("I", "/destination/Movies/I/")
        every { httpHelper.getPage(match { it.contains("folder/list") }) } returns
                "{\"status\":\"success\",\"content\":[{\"id\":\"f1\",\"name\":\"Inception.mkv\",\"type\":\"file\",\"size\":1000,\"link\":\"http://premiumize.me/dl/inception.mkv\"}]}"
        every { rcloneUploader.copyUrl(any(), any()) } returns false

        multifileHosterService.checkForDownloadableTorrentsAndDownloadTheFirst()
        assertThat(transfer.failedUploadsToDrive).isEqualTo(1)
        assertThat(transfer.transferStatus).isEqualTo(TransferStatus.READY_TO_BE_DOWNLOADED)

        multifileHosterService.checkForDownloadableTorrentsAndDownloadTheFirst()
        assertThat(transfer.failedUploadsToDrive).isEqualTo(2)
        assertThat(transfer.transferStatus).isEqualTo(TransferStatus.READY_TO_BE_DOWNLOADED)
    }

    @Test
    fun shouldGiveUpAfterMaximumRetriesAndKeepFilesOnHoster() {
        val transfer = Transfer(
            name = "Inception.2010.1080p.mkv",
            transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED,
            remoteId = "remote-123",
            source = "Premiumize",
            failedUploadsToDrive = MultifileHosterService.MAX_UPLOAD_TO_DRIVE_RETRIES
        )
        every { transferService.getAll() } returns listOf(transfer)
        every { cloudService.buildDestinationPath(any(), any()) } returns Pair("I", "/destination/Movies/I/")
        every { httpHelper.getPage(match { it.contains("folder/list") }) } returns
                "{\"status\":\"success\",\"content\":[{\"id\":\"f1\",\"name\":\"Inception.mkv\",\"type\":\"file\",\"size\":1000,\"link\":\"http://premiumize.me/dl/inception.mkv\"}]}"
        every { rcloneUploader.copyUrl(any(), any()) } returns false

        multifileHosterService.checkForDownloadableTorrentsAndDownloadTheFirst()

        assertThat(transfer.transferStatus).isEqualTo(TransferStatus.UPLOAD_TO_DRIVE_FAILED)
        verify(exactly = 0) { transferService.delete(any()) }
        verify(exactly = 0) { httpHelper.getPage(match { it.contains("transfer/delete") }) }

        // a failed transfer is not picked again
        assertThat(multifileHosterService.getTransferToBeDownloaded()).isNull()
    }

    @Test
    fun shouldNotResetFailedTransferToReadyFromHosterStatus() {
        val transfer = Transfer(
            name = "Inception.2010.1080p.mkv",
            transferStatus = TransferStatus.UPLOAD_TO_DRIVE_FAILED,
            remoteId = "remote-123",
            uri = "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2",
            source = "Premiumize"
        )
        every { transferService.getAll() } returns listOf(transfer)
        every { httpHelper.getPage(match { it.contains("transfer/list") }) } returns
                "{\"status\":\"success\",\"transfers\":[{\"id\":\"remote-123\",\"name\":\"Inception.2010.1080p.mkv\",\"status\":\"finished\"," +
                "\"folder_id\":\"folder-1\",\"file_id\":null,\"src\":\"magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2\",\"message\":\"\",\"progress\":1}]}"

        multifileHosterService.updateTransferStatus()

        assertThat(transfer.transferStatus).isEqualTo(TransferStatus.UPLOAD_TO_DRIVE_FAILED)
    }

    private val readyTransferList = "{\"status\":\"success\",\"transfers\":[{\"id\":\"remote-123\",\"name\":\"Inception.2010.1080p.mkv\",\"status\":\"finished\"," +
            "\"folder_id\":\"folder-1\",\"file_id\":null,\"src\":\"magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2\",\"message\":\"\",\"progress\":1}]}"

    private fun failedTransfer(remoteId: String? = "remote-123") = Transfer(
        name = "Inception.2010.1080p.mkv",
        transferStatus = TransferStatus.UPLOAD_TO_DRIVE_FAILED,
        remoteId = remoteId,
        uri = "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2",
        source = "Premiumize",
        failedUploadsToDrive = 3
    )

    @Test
    fun shouldDeleteTransferAndItsRemoteTorrent() {
        val transfer = failedTransfer()
        every { transferService.getAll() } returns listOf(transfer)
        every { httpHelper.getPage(match { it.contains("transfer/list") }) } returns readyTransferList

        val message = multifileHosterService.deleteTransfer(transfer.id)

        assertThat(message).contains("deleted")
        verify { transferService.delete(transfer) }
        verify { httpHelper.getPage(match { it.contains("transfer/delete") && it.contains("remote-123") }) }
    }

    @Test
    fun shouldFindRemoteTorrentByInfoHashWithoutRemoteId() {
        val transfer = failedTransfer(remoteId = null)
        every { transferService.getAll() } returns listOf(transfer)
        every { httpHelper.getPage(match { it.contains("transfer/list") }) } returns readyTransferList

        multifileHosterService.deleteTransfer(transfer.id)

        verify { httpHelper.getPage(match { it.contains("transfer/delete") && it.contains("remote-123") }) }
    }

    @Test
    fun shouldNotDeleteOrRestartTransferUploadingToDrive() {
        val transfer = failedTransfer().apply { transferStatus = TransferStatus.UPLOADING_TO_DRIVE }
        every { transferService.getAll() } returns listOf(transfer)
        every { httpHelper.getPage(match { it.contains("transfer/list") }) } returns readyTransferList

        assertThat(multifileHosterService.deleteTransfer(transfer.id)).contains("uploading")
        assertThat(multifileHosterService.restartTransfer(transfer.id)).contains("uploading")

        verify(exactly = 0) { transferService.delete(any()) }
        verify(exactly = 0) { httpHelper.getPage(match { it.contains("transfer/delete") }) }
        assertThat(transfer.transferStatus).isEqualTo(TransferStatus.UPLOADING_TO_DRIVE)
    }

    @Test
    fun shouldReportUnknownTransfer() {
        every { transferService.getAll() } returns emptyList()

        assertThat(multifileHosterService.deleteTransfer("unknown")).isEqualTo("transfer not found")
        assertThat(multifileHosterService.restartTransfer("unknown")).isEqualTo("transfer not found")
    }

    @Test
    fun shouldRestartUploadToDriveIfFilesAreReadyAtHoster() {
        val transfer = failedTransfer()
        every { transferService.getAll() } returns listOf(transfer)
        every { httpHelper.getPage(match { it.contains("transfer/list") }) } returns readyTransferList

        multifileHosterService.restartTransfer(transfer.id)

        assertThat(transfer.transferStatus).isEqualTo(TransferStatus.READY_TO_BE_DOWNLOADED)
        assertThat(transfer.failedUploadsToDrive).isEqualTo(0)
        assertThat(transfer.remoteId).isEqualTo("remote-123")
        verify(exactly = 0) { httpHelper.getPage(match { it.contains("transfer/delete") }) }
        verify { transferService.save(transfer) }
    }

    @Test
    fun shouldAddTransferAgainIfHosterHasNoReadyFiles() {
        val transfer = failedTransfer().apply { transferStatus = TransferStatus.ERROR }
        every { transferService.getAll() } returns listOf(transfer)
        every { httpHelper.getPage(match { it.contains("transfer/list") }) } returns readyTransferList.replace("finished", "error")

        multifileHosterService.restartTransfer(transfer.id)

        assertThat(transfer.transferStatus).isEqualTo(TransferStatus.ADDED)
        assertThat(transfer.remoteId).isNull()
        assertThat(transfer.failedUploadsToDrive).isEqualTo(0)
        verify { httpHelper.getPage(match { it.contains("transfer/delete") && it.contains("remote-123") }) }
        verify { transferService.save(transfer) }
    }
}
