package boat.multifileHoster

import boat.info.CloudFileService
import boat.info.CloudService
import boat.info.MediaProxyService
import boat.model.Transfer
import boat.model.TransferStatus
import boat.services.TransferService
import boat.torrent.Torrent
import boat.utilities.HttpHelper
import boat.utilities.PropertiesHelper
import boat.utilities.RcloneUploader
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.cache.CacheManager

/** Premiumize and AllDebrid together: each transfer stays with its own hoster and its traffic. */
internal class MultifileHosterServiceTwoHostersTest {

    private val httpHelper: HttpHelper = mockk(relaxed = true)
    private val transferService: TransferService = mockk(relaxed = true)
    private val cloudService: CloudService = mockk(relaxed = true)
    private lateinit var multifileHosterService: MultifileHosterService

    private val hash = "10e93389d579ba4538c68b8d297268a81620fae2"
    private val magnet = "magnet:?xt=urn:btih:$hash&dn=Ivakisaka.2010.1080p"

    @BeforeEach
    fun setUp() {
        mockkObject(PropertiesHelper)
        every { PropertiesHelper.getProperty("ALLDEBRID_APIKEY") } returns "key"
        every { cloudService.freeSpaceInBytes } returns null
        premiumizeTrafficLeftInGB(500.0)
        every { httpHelper.getPage(match { it.contains("premiumize.me/account?") }) } returns "<html><body></body></html>"
        alldebrid("v4/user", """{"status":"success","data":{"user":{"isPremium":true,"isTrial":false}}}""")
        multifileHosterService = MultifileHosterService(
            httpHelper, transferService, cloudService, mockk<CacheManager>(relaxed = true), mockk<CloudFileService>(relaxed = true),
            mockk<MediaProxyService>(relaxed = true), mockk(relaxed = true), mockk<RcloneUploader>(relaxed = true)
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(PropertiesHelper)
    }

    private fun premiumizeTrafficLeftInGB(gb: Double) {
        every { httpHelper.getPage(match { it.contains("account/info") }) } returns "{\"limit_used\": ${1.0 - gb / 1024.0}}"
    }

    private fun alldebrid(endpoint: String, json: String) {
        every { httpHelper.getPage("https://api.alldebrid.com/$endpoint", any<String>(), any()) } returns json
    }

    private fun stubTransfers(vararg transfers: Transfer) {
        every { transferService.getAll() } returns transfers.toList()
        every { transferService.getById(any()) } answers { transfers.firstOrNull { it.id == firstArg<String>() } }
    }

    /** Premiumize still has the same torrent, finished, e.g. from an older transfer. */
    private fun premiumizeHasTheTorrentToo() {
        every { httpHelper.getPage(match { it.contains("transfer/list") }) } returns
                "{\"status\":\"success\",\"transfers\":[{\"id\":\"premiumize-1\",\"name\":\"Ivakisaka.2010.1080p\",\"status\":\"finished\"," +
                "\"folder_id\":\"folder-1\",\"file_id\":null,\"src\":\"$magnet\",\"message\":\"\",\"progress\":1}]}"
    }

    private fun alldebridHasTheTorrent(statusCode: Int) {
        alldebrid(
            "v4.1/magnet/status",
            """{"status":"success","data":{"magnets":[{"id":777,"filename":"Ivakisaka.2010.1080p","hash":"$hash","size":1000,
               "status":"x","statusCode":$statusCode,"downloaded":500,"downloadSpeed":10}]}}"""
        )
    }

    private fun gb(gb: Long) = gb * 1024 * 1024 * 1024

    @Test
    fun shouldMatchATransferOnlyWithATorrentAtItsOwnHoster() {
        premiumizeHasTheTorrentToo()
        // just added, AllDebrid doesn't list it yet
        alldebrid("v4.1/magnet/status", """{"status":"success","data":{"magnets":[]}}""")
        val transfer = Transfer(name = "Ivakisaka.2010.1080p", source = "Alldebrid", uri = magnet, remoteId = "777", transferStatus = TransferStatus.ADDED_TO_MULTIHOSTER)
        stubTransfers(transfer)

        multifileHosterService.updateTransferStatus()

        assertThat(transfer.remoteId).isEqualTo("777")
        assertThat(transfer.transferStatus).isEqualTo(TransferStatus.ADDED_TO_MULTIHOSTER)

        alldebridHasTheTorrent(statusCode = 1)
        multifileHosterService.updateTransferStatus()
        assertThat(transfer.remoteId).isEqualTo("777")
        assertThat(transfer.transferStatus).isNotEqualTo(TransferStatus.READY_TO_BE_DOWNLOADED)
    }

    @Test
    fun shouldDownloadATransferFromItsOwnHoster() {
        premiumizeHasTheTorrentToo()
        alldebridHasTheTorrent(statusCode = 4)
        val transfer = Transfer(name = "Ivakisaka.2010.1080p", source = "Alldebrid", uri = magnet, remoteId = "777", transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED)
        stubTransfers(transfer)

        multifileHosterService.checkForDownloadableTorrentsAndDownloadTheFirst()

        verify { httpHelper.getPage("https://api.alldebrid.com/v4/magnet/files", "id%5B%5D=777", any()) }
        verify(exactly = 0) { httpHelper.getPage(match { it.contains("folder/list") || it.contains("item/details") }) }
    }

    @Test
    fun shouldCheckADownloadAgainstTheTrafficOfItsOwnHoster() {
        premiumizeTrafficLeftInGB(10.0)
        val atPremiumize = Transfer(name = "P", source = "Premiumize", transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED, sizeInBytes = gb(20))
        val atAlldebrid = Transfer(name = "A", source = "Alldebrid", transferStatus = TransferStatus.READY_TO_BE_DOWNLOADED, sizeInBytes = gb(20))

        stubTransfers(atPremiumize)
        // AllDebrid's traffic doesn't help a transfer at Premiumize
        assertThat(multifileHosterService.getTransferToBeDownloaded()).isNull()
        stubTransfers(atPremiumize, atAlldebrid)
        assertThat(multifileHosterService.getTransferToBeDownloaded()).isEqualTo(atAlldebrid)
    }

    @Test
    fun shouldTellTheTrafficOfEachHoster() {
        premiumizeTrafficLeftInGB(10.0)

        val traffic = multifileHosterService.getRemainingTrafficInMBByHoster()

        assertThat(traffic.keys).containsExactly("Premiumize", "Alldebrid")
        assertThat(traffic["Premiumize"]).isEqualTo(10.0 * 1024)
        assertThat(traffic["Alldebrid"]).isEqualTo(Alldebrid.UNLIMITED_TRAFFIC_MB)
        assertThat(multifileHosterService.getRemainingTrafficInMB()).isEqualTo(10.0 * 1024 + Alldebrid.UNLIMITED_TRAFFIC_MB)
    }

    @Test
    fun shouldAddATorrentToTheFirstHosterWithEnoughTrafficForIt() {
        premiumizeTrafficLeftInGB(50.0)
        val saved = slot<Transfer>()
        every { transferService.save(capture(saved)) } returns Unit

        multifileHosterService.addTorrentToTransfer(Torrent.of(magnet).apply { sizeInMB = 10.0 * 1024 })
        assertThat(saved.captured.source).isEqualTo("Premiumize")

        multifileHosterService.addTorrentToTransfer(Torrent.of(magnet).apply { sizeInMB = 60.0 * 1024 })
        assertThat(saved.captured.source).isEqualTo("Alldebrid")
    }
}
