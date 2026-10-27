package boat

import boat.info.MediaProxyService
import boat.info.CloudFileService
import boat.info.CloudService
import boat.info.QueueService
import boat.multifileHoster.MultifileHosterService
import boat.services.ConfigurationService
import boat.services.TransferService
import boat.torrent.TorrentSearchEngineService
import boat.utilities.HttpHelper
import boat.info.MediaItem
import boat.info.MediaType
import boat.model.Transfer
import boat.model.TransferStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Base64

internal class BoatControllerTest {

    private val httpHelper: HttpHelper = mockk()
    private val torrentSearchEngineService: TorrentSearchEngineService = mockk()
    private val cloudService: CloudService = mockk()
    private val mediaProxyService: MediaProxyService = mockk()
    private val multifileHosterService: MultifileHosterService = mockk()
    private val queueService: QueueService = mockk()
    private val cloudFileService: CloudFileService = mockk()
    private val transferService: TransferService = mockk()
    private val configurationService: ConfigurationService = mockk()

    private val boatController = BoatController(
        httpHelper,
        torrentSearchEngineService,
        cloudService,
        mediaProxyService,
        multifileHosterService,
        queueService,
        cloudFileService,
        transferService,
        configurationService
    )

    @Test
    fun shouldOnlyAllowTheShutdownWithTheConfiguredToken() {
        assertThat(BoatController.isShutdownAllowed("s3cret-token", "s3cret-token")).isTrue()
        assertThat(BoatController.isShutdownAllowed("s3cret-token", "s3cret-tokeN")).isFalse()
        assertThat(BoatController.isShutdownAllowed("s3cret-token", "s3cret")).isFalse()
        assertThat(BoatController.isShutdownAllowed("s3cret-token", null)).isFalse()
    }

    @Test
    fun shouldNotAllowTheShutdownWithoutAConfiguredToken() {
        assertThat(BoatController.isShutdownAllowed(null, "")).isFalse()
        assertThat(BoatController.isShutdownAllowed("", "")).isFalse()
        assertThat(BoatController.isShutdownAllowed("  ", "  ")).isFalse()
    }

    @Test
    fun shouldRefuseTheShutdownWithoutToken() {
        // the offline tests have no SHUTDOWN_TOKEN, so the controller must not stop the test run
        val response = boatController.shutdownServer(null)

        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun shouldParseTrailingYearOfSearchListEntry() {
        assertThat(BoatController.splitSearchListEntry("Daca 1984")).isEqualTo("Daca" to 1984)
        assertThat(BoatController.splitSearchListEntry("Daca (2021)\r")).isEqualTo("Daca" to 2021)
        assertThat(BoatController.splitSearchListEntry("Ivakisaka.2010")).isEqualTo("Ivakisaka" to 2010)
    }

    @Test
    fun shouldKeepTitleWithoutPlausibleYear() {
        assertThat(BoatController.splitSearchListEntry("Bubes Rutepe 2049")).isEqualTo("Bubes Rutepe 2049" to null)
        assertThat(BoatController.splitSearchListEntry("1917")).isEqualTo("1917" to null)
        assertThat(BoatController.splitSearchListEntry("Ivakisaka")).isEqualTo("Ivakisaka" to null)
        assertThat(BoatController.splitSearchListEntry("   ")).isNull()
    }

    @Test
    fun shouldAnswerABadRequestForADownloadWhichIsNotBase64() {
        val response = boatController.downloadTorrentToMultifileHoster("not base64!", null, null, null)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        verify(exactly = 0) { multifileHosterService.addTorrentToTransfer(any(), any(), any()) }
    }

    @Test
    fun shouldTrimTheDirectDownloadUrlsAndSkipBlankOnes() {
        val added = mutableListOf<boat.torrent.Torrent>()
        every { multifileHosterService.addTorrentToTransfer(capture(added), any(), any()) } returns true
        every { torrentSearchEngineService.refreshCacheStateAndStats(any()) } returns Unit
        every { multifileHosterService.addTransfersToDownloadQueue() } returns Unit

        val response = boatController.downloadTorrentToMultifileHoster(null, " http://a.example/1.mkv , ,http://a.example/2.mkv,", null, null)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(added.map { it.magnetUri }).containsExactly("http://a.example/1.mkv", "http://a.example/2.mkv")
    }

    @Test
    fun shouldQueueTheTitleIfTheTorrentCanNotBeAdded() {
        val queued = slot<MediaItem>()
        every { torrentSearchEngineService.refreshCacheStateAndStats(any()) } returns Unit
        every { multifileHosterService.addTorrentToTransfer(any(), any(), any()) } returns false
        every { multifileHosterService.addTransfersToDownloadQueue() } returns Unit
        every { queueService.add(capture(queued)) } returns Unit
        val magnet = "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&dn=Daca.2021.1080p.WEB-DL"

        val response = boatController.downloadTorrentToMultifileHoster(
            Base64.getUrlEncoder().encodeToString(magnet.toByteArray()), null, null, null
        )

        assertThat(queued.captured.title).isEqualTo("Daca")
        assertThat(queued.captured.year).isEqualTo(2021)
        assertThat(response.body).contains("queued instead")
        verify { torrentSearchEngineService.refreshCacheStateAndStats(match { it.single().magnetUri == magnet }) }
    }

    @Test
    fun shouldEscapeTheQueueOnTheInfoPage() {
        val html = BoatController.queueHtml(listOf(boat.info.MediaItem("<script>alert(1)</script>", year = 2000)))

        assertThat(html).doesNotContain("<script>").contains("Queue [1]", "&lt;script&gt;")
    }

    @Test
    fun shouldShowRestartAndDeleteControlsForTransfer() {
        val transfer = Transfer(id = "id-1", name = "Ivakisaka", transferStatus = TransferStatus.UPLOAD_TO_DRIVE_FAILED)

        val controls = BoatController.transferControls(transfer)

        assertThat(controls).contains("action=\"./transferRestart\"", "action=\"./transferDelete\"", "value=\"id-1\"", "method=\"post\"")
    }

    @Test
    fun shouldNotShowControlsWhileUploadingToDrive() {
        val transfer = Transfer(id = "id-1", name = "Ivakisaka", transferStatus = TransferStatus.UPLOADING_TO_DRIVE)

        assertThat(BoatController.transferControls(transfer)).isEmpty()
    }

    @Test
    fun shouldRedirectToInfoPageWithMessageAfterDelete() {
        every { multifileHosterService.deleteTransfer("id-1") } returns "deleted Ivakisaka & co"

        val response = boatController.deleteTransfer("id-1")

        assertThat(response.statusCode).isEqualTo(HttpStatus.SEE_OTHER)
        assertThat(response.headers.getFirst(HttpHeaders.LOCATION)).isEqualTo("./info?message=deleted+Ivakisaka+%26+co")
    }

    @Test
    fun shouldRedirectToInfoPageWithMessageAfterRestart() {
        every { multifileHosterService.restartTransfer("id-1") } returns "restarted Ivakisaka"

        val response = boatController.restartTransfer("id-1")

        assertThat(response.statusCode).isEqualTo(HttpStatus.SEE_OTHER)
        assertThat(response.headers.getFirst(HttpHeaders.LOCATION)).isEqualTo("./info?message=restarted+Ivakisaka")
    }

    @Test
    fun shouldQueuePlainEntriesOfTheSearchListWithoutLookups() {
        every { queueService.addAll(any()) } returns Unit

        val response = boatController.queueList("Daca 1984\nIvakisaka\n\n")

        verify(exactly = 1) {
            queueService.addAll(listOf(MediaItem("Daca", "Daca", 1984, MediaType.Other), MediaItem("Ivakisaka", "Ivakisaka", null, MediaType.Other)))
        }
        verify(exactly = 0) { mediaProxyService.findBestMatch(any(), any()) }
        verify(exactly = 0) { cloudService.findExistingFiles(any()) }
        assertThat(response).contains("Daca")
    }

    @Test
    fun shouldRejectEmptySearchList() {
        assertThat(boatController.queueList("")).contains("nothing to search")
        assertThat(boatController.queueList(null)).contains("nothing to search")
    }

    @Test
    fun shouldRedirectTheFormerDebugPageToTheInfoPage() {
        val response = boatController.debugRedirect(null)

        assertThat(response.statusCode).isEqualTo(HttpStatus.MOVED_PERMANENTLY)
        assertThat(response.headers.getFirst(HttpHeaders.LOCATION)).isEqualTo("./info")
        assertThat(boatController.debugRedirect("deleted A & B").headers.getFirst(HttpHeaders.LOCATION))
            .isEqualTo("./info?message=deleted+A+%26+B")
    }
}
