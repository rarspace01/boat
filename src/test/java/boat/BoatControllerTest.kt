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
import io.mockk.verify
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
    fun shouldParseTrailingYearOfSearchListEntry() {
        assertThat(BoatController.parseSearchListEntry("Dune 1984")).isEqualTo(MediaItem("Dune", "Dune", 1984, MediaType.Other))
        assertThat(BoatController.parseSearchListEntry("Dune (2021)\r")?.let { it.title to it.year }).isEqualTo("Dune" to 2021)
        assertThat(BoatController.parseSearchListEntry("Inception.2010")?.let { it.title to it.year }).isEqualTo("Inception" to 2010)
    }

    @Test
    fun shouldKeepTitleWithoutPlausibleYear() {
        assertThat(BoatController.parseSearchListEntry("Blade Runner 2049")?.let { it.title to it.year }).isEqualTo("Blade Runner 2049" to null)
        assertThat(BoatController.parseSearchListEntry("1917")?.let { it.title to it.year }).isEqualTo("1917" to null)
        assertThat(BoatController.parseSearchListEntry("Inception")?.let { it.title to it.year }).isEqualTo("Inception" to null)
        assertThat(BoatController.parseSearchListEntry("   ")).isNull()
    }

    @Test
    fun shouldShowRestartAndDeleteControlsForTransfer() {
        val transfer = Transfer(id = "id-1", name = "Inception", transferStatus = TransferStatus.UPLOAD_TO_DRIVE_FAILED)

        val controls = BoatController.transferControls(transfer)

        assertThat(controls).contains("action=\"./transferRestart\"", "action=\"./transferDelete\"", "value=\"id-1\"", "method=\"post\"")
    }

    @Test
    fun shouldNotShowControlsWhileUploadingToDrive() {
        val transfer = Transfer(id = "id-1", name = "Inception", transferStatus = TransferStatus.UPLOADING_TO_DRIVE)

        assertThat(BoatController.transferControls(transfer)).isEmpty()
    }

    @Test
    fun shouldRedirectToDebugPageWithMessageAfterDelete() {
        every { multifileHosterService.deleteTransfer("id-1") } returns "deleted Inception & co"

        val response = boatController.deleteTransfer("id-1")

        assertThat(response.statusCode).isEqualTo(HttpStatus.SEE_OTHER)
        assertThat(response.headers.getFirst(HttpHeaders.LOCATION)).isEqualTo("./debug?message=deleted+Inception+%26+co")
    }

    @Test
    fun shouldRedirectToDebugPageWithMessageAfterRestart() {
        every { multifileHosterService.restartTransfer("id-1") } returns "restarted Inception"

        val response = boatController.restartTransfer("id-1")

        assertThat(response.statusCode).isEqualTo(HttpStatus.SEE_OTHER)
        assertThat(response.headers.getFirst(HttpHeaders.LOCATION)).isEqualTo("./debug?message=restarted+Inception")
    }

    @Test
    fun shouldQueueMatchedEntriesOfTheSearchList() {
        val matched = MediaItem("Dune", "Dune", 1984, MediaType.Movie, "{tmdb-841}")
        every { mediaProxyService.findBestMatch("Dune", 1984) } returns matched
        every { cloudService.findExistingFiles(any()) } returns emptyList()
        every { queueService.addAll(any()) } returns Unit
        every { queueService.saveQueue() } returns Unit

        val response = boatController.queueList("Dune 1984\n")

        verify(exactly = 1) { queueService.addAll(listOf(matched)) }
        assertThat(response).contains("{tmdb-841}")
    }

    @Test
    fun shouldRejectEmptySearchList() {
        assertThat(boatController.queueList("")).contains("nothing to search")
        assertThat(boatController.queueList(null)).contains("nothing to search")
    }
}
