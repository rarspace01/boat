package boat.multifileHoster

import boat.model.TransferStatus
import boat.torrent.Torrent
import boat.utilities.HttpHelper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class PremiumizeTest {

    private val premiumize: Premiumize = Premiumize(HttpHelper())

    @Test
    fun getRemainingTrafficInMB() {
        // Given
        // When
        // Then
        assertDoesNotThrow { premiumize.getRemainingTrafficInMB() }
    }

    @Test
    fun getRemoteTorrents() {
        // Given
        // When
        // Then
        assertDoesNotThrow { premiumize.getRemoteTorrents() }
    }

    @Test
    fun filesFromTorrent() {
        // Given
        val remoteTorrents: List<Torrent?> = premiumize.getRemoteTorrents()
        // When
        val torrent = remoteTorrents.firstOrNull { torrent -> torrent?.remoteTransferStatus?.equals(TransferStatus.READY_TO_BE_DOWNLOADED) ?: false }
        if (torrent != null) {
            val filesFromTorrent = premiumize.getFilesFromTorrent(torrent)
            Assertions.assertNotNull(filesFromTorrent)
        }
        Assertions.assertTrue(true)
    }

    @Test
    fun shouldUrlEncodeMagnetUriWhenAddingTransfer() {
        // Given
        val magnetUri = "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&dn=Some.Movie.2020&tr=udp%3A%2F%2Ftracker.org%3A6969"
        // When
        val url = premiumize.buildAddTransferUrl("key", magnetUri)
        // Then
        val parameters = url.substringAfter("?").split("&").associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), StandardCharsets.UTF_8) }
        Assertions.assertEquals(setOf("apikey", "type", "src"), parameters.keys)
        Assertions.assertEquals(magnetUri, parameters["src"])
    }
}
