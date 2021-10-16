package boat.multifileHoster

import boat.services.TransferService
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
}