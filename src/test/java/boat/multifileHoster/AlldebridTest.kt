package boat.multifileHoster

import boat.torrent.Torrent
import boat.utilities.HttpHelper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled
internal class AlldebridTest {
    private lateinit var alldebrid: Alldebrid

    @BeforeEach
    fun beforeEach() {
        alldebrid = Alldebrid(HttpHelper())
    }

    @Test
    fun getRemoteTorrents() {
        // Given
        // When
        // Then
        assertDoesNotThrow { alldebrid.getRemoteTorrents() }
    }


    @Test
    fun filesFromTorrent() {
        // Given
        val remoteTorrents: List<Torrent?> = alldebrid.getRemoteTorrents()
        // When
        val torrent = remoteTorrents.stream().findFirst().orElse(null)
        if (torrent != null) {
            val filesFromTorrent = alldebrid.getFilesFromTorrent(torrent)
            Assertions.assertNotNull(filesFromTorrent)
        }
        Assertions.assertTrue(true)
    }

    @Test
    fun remainingTrafficInMB() {
        // Given
        // When
        // Then
        assertDoesNotThrow { alldebrid.getRemainingTrafficInMB() }
    }
}

