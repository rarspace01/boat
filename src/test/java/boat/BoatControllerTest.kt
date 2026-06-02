package boat

import boat.info.CloudFileService
import boat.info.CloudService
import boat.info.QueueService
import boat.info.TheFilmDataBaseService
import boat.multifileHoster.MultifileHosterService
import boat.services.ConfigurationService
import boat.services.TransferService
import boat.torrent.TorrentSearchEngineService
import boat.utilities.HttpHelper
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class BoatControllerTest {

    private val httpHelper: HttpHelper = mockk()
    private val torrentSearchEngineService: TorrentSearchEngineService = mockk()
    private val cloudService: CloudService = mockk()
    private val theFilmDataBaseService: TheFilmDataBaseService = mockk()
    private val multifileHosterService: MultifileHosterService = mockk()
    private val queueService: QueueService = mockk()
    private val cloudFileService: CloudFileService = mockk()
    private val transferService: TransferService = mockk()
    private val configurationService: ConfigurationService = mockk()

    private val boatController = BoatController(
        httpHelper,
        torrentSearchEngineService,
        cloudService,
        theFilmDataBaseService,
        multifileHosterService,
        queueService,
        cloudFileService,
        transferService,
        configurationService
    )

    @Test
    fun `encodePath should encode segments correctly`() {
        // Given
        val path = "/PFDB/some folder/file name with spaces.txt"
        
        // When
        val encodedPath = boatController.encodePath(path)
        
        // Then
        assertThat(encodedPath).isEqualTo("/PFDB/some%20folder/file%20name%20with%20spaces.txt")
    }

    @Test
    fun `encodePath should handle special characters in segments`() {
        // Given
        val path = "/test/a&b/c?d/e#f"
        
        // When
        val encodedPath = boatController.encodePath(path)
        
        // Then
        assertThat(encodedPath).isEqualTo("/test/a%26b/c%3Fd/e%23f")
    }

    @Test
    fun `escapeXml should escape special XML characters`() {
        // Given
        val input = "Title with & < > \" '"
        
        // When
        val escaped = boatController.escapeXml(input)
        
        // Then
        assertThat(escaped).isEqualTo("Title with &amp; &lt; &gt; &quot; &apos;")
    }

    @Test
    fun `escapeXml should return same string if no special characters`() {
        // Given
        val input = "Simple Title 123"
        
        // When
        val escaped = boatController.escapeXml(input)
        
        // Then
        assertThat(escaped).isEqualTo("Simple Title 123")
    }
}
