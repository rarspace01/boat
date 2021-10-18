package boat.multifileHoster

import boat.model.TransferStatus
import boat.torrent.Torrent
import boat.utilities.HttpHelper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

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
}