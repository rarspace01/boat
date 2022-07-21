package boat.torrent

import boat.multifileHoster.Alldebrid
import boat.multifileHoster.Premiumize
import boat.utilities.HttpHelper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class MultihosterTest {
    var premiumize: Premiumize? = null
    var alldebrid: Alldebrid? = null
    @BeforeEach
    fun beforeEach() {
        premiumize = Premiumize(HttpHelper())
        alldebrid = Alldebrid(HttpHelper())
    }

    // Given
    @get:Test
    val cacheStateOfTorrent: Unit
        // When
        // Then
        //assertThat(listOfTorrents).allMatch(torrent -> torrent.cached.size() > 0);
        get() {
            // Given
            val listOfTorrents: MutableList<Torrent> = ArrayList()
            val torrentToBeChecked = Torrent("Test")
            torrentToBeChecked.magnetUri = "magnet:?xt=urn:btih:e2467cbf021192c241367b892230dc1e05c0580e&dn=test"
            listOfTorrents.add(torrentToBeChecked)

            // When
            Assertions.assertDoesNotThrow { premiumize!!.enrichCacheStateOfTorrents(listOfTorrents) }
            // Then
            //assertThat(listOfTorrents).allMatch(torrent -> torrent.cached.size() > 0);
        }

    // Given
    @get:Test
    val cacheStateOfTorrents: Unit
        // When
        // Then
        //assertThat(listOfTorrents).allMatch(torrent -> torrent.cached.size() > 0);
        get() {
            // Given
            val listOfTorrents: MutableList<Torrent> = ArrayList()
            val torrentToBeChecked = Torrent("Test")
            torrentToBeChecked.magnetUri = "magnet:?xt=urn:btih:e2467cbf021192c241367b892230dc1e05c0580e&dn=test"
            for (i in 0..1) {
                listOfTorrents.add(torrentToBeChecked)
            }

            // When
            Assertions.assertDoesNotThrow { premiumize!!.enrichCacheStateOfTorrents(listOfTorrents) }
            // Then
            //assertThat(listOfTorrents).allMatch(torrent -> torrent.cached.size() > 0);
        }

    // Given
    @get:Test
    val cacheStateOfALotOfTorrents: Unit
        // When
        // Then
        //assertThat(listOfTorrents).allMatch(torrent -> torrent.cached.size() > 0);
        get() {
            // Given
            val listOfTorrents: MutableList<Torrent> = ArrayList()
            val torrentToBeChecked = Torrent("Test")
            torrentToBeChecked.magnetUri = "magnet:?xt=urn:btih:e2467cbf021192c241367b892230dc1e05c0580e&dn=test"
            for (i in 0..99) {
                listOfTorrents.add(torrentToBeChecked)
            }

            // When
            Assertions.assertDoesNotThrow { premiumize!!.enrichCacheStateOfTorrents(listOfTorrents) }
            // Then
            //assertThat(listOfTorrents).allMatch(torrent -> torrent.cached.size() > 0);
        }

    // Given
    @get:Test
    val cacheStateOfTorrentsWithEmptyDN: Unit
        // When
        // Then
        //assertTrue(listOfTorrents.get(0).cached.size() > 0);
        get() {
            // Given
            val listOfTorrents: MutableList<Torrent> = ArrayList()
            val torrentToBeChecked = Torrent("Test")
            torrentToBeChecked.magnetUri = "magnet:?xt=urn:btih:e2467cbf021192c241367b892230dc1e05c0580e&tracker=test"
            listOfTorrents.add(torrentToBeChecked)

            // When
            Assertions.assertDoesNotThrow { premiumize!!.enrichCacheStateOfTorrents(listOfTorrents) }
            // Then
            //assertTrue(listOfTorrents.get(0).cached.size() > 0);
        }

    // Given
    @get:Test
    val cacheStateOfTorrentsWithEmptyDNAllDebrid: Unit
        // When
        // Then
        //assertTrue(listOfTorrents.get(0).cached.size() > 0);
        get() {
            // Given
            val listOfTorrents: MutableList<Torrent> = ArrayList()
            val torrentToBeChecked = Torrent("Test")
            torrentToBeChecked.magnetUri = "magnet:?xt=urn:btih:e2467cbf021192c241367b892230dc1e05c0580e&tracker=test"
            listOfTorrents.add(torrentToBeChecked)

            // When
            Assertions.assertDoesNotThrow { alldebrid!!.enrichCacheStateOfTorrents(listOfTorrents) }
            // Then
            //assertTrue(listOfTorrents.get(0).cached.size() > 0);
        }
}