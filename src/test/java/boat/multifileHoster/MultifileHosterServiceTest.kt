package boat.multifileHoster

import boat.model.Transfer
import boat.services.TransferService
import boat.torrent.Torrent
import boat.utilities.HttpHelper
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class MultifileHosterServiceTest {

    private val httpHelper: HttpHelper = mockk(relaxed = true)
    private val transferService: TransferService = mockk(relaxed = true)
    lateinit var multifileHosterService : MultifileHosterService

    @BeforeEach
    fun beforeEach(){
        multifileHosterService = MultifileHosterService(httpHelper, transferService)
    }

    @Test
    fun extractRemoteIdFromMessage() {
        // Given
        // When
        val remoteIdFromMessage =
            multifileHosterService.extractRemoteIdFromMessage("{\"status\":\"success\",\"id\":\"0jXfg462WZz2EkSpFKjU_g\",\"name\":\"magnet:?xt=urn:btih:123456789\",\"type\":\"torrent\"}")
        // Then
        assertThat(remoteIdFromMessage).isEqualTo("0jXfg462WZz2EkSpFKjU_g")
    }

    @Test
    fun extractOtherRemoteIdFromMessage() {
        // Given
        // When
        val remoteIdFromMessage =
            multifileHosterService.extractRemoteIdFromMessage("{\"status\":\"success\",\"id\":\"yxGo09Kq-RkayKbAxdDyFQ\",\"name\":\"magnet:?xt=urn:btih:12345678912316782351328765\",\"type\":\"torrent\"}")
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
        torrent.remoteId="remoteId"
        // When

        val matched =
            multifileHosterService.transferMatchedTorrentByName(transfer, torrent)
        // Then
        assertThat(matched).isTrue
    }
}