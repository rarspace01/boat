package boat.torrent

import boat.multifileHoster.MultifileHosterService
import boat.utilities.HttpHelper
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

internal class TorrentSearchEngineServiceTest {
    private val torrentInfoService: TorrentInfoService = mockk()
    private val multifileHosterService: MultifileHosterService = mockk()

    @Test
    fun cleanDuplicates() {
        // Given
        val tse = TorrentSearchEngineService(HttpHelper(), multifileHosterService, torrentInfoService)
        val s1 = Torrent("S1")
        val s2 = Torrent("S2")
        s1.magnetUri = "btih:ABC&"
        s2.magnetUri = "btih:ABC&"
        s1.searchRating = 1.0
        s2.searchRating = 2.0
        // When
        val torrentList = tse.cleanDuplicates(listOf(s1, s2))
        // Then
        Assertions.assertThat(torrentList[0]).isEqualTo(s2)
        Assertions.assertThat(torrentList.size).isEqualTo(1)
    }

    private fun torrent(name: String, seeder: Int, leecher: Int, cachedAt: String? = null) = Torrent(name).apply {
        this.seeder = seeder
        this.leecher = leecher
        size = "1.5 GB"
        magnetUri = "magnet:?xt=urn:btih:" + String.format("%040x", name.hashCode().toLong() and 0xffffffffL)
        cachedAt?.let { cached.add(it) }
    }

    @Test
    fun cachedTorrentsKeepTheirResultAndTheirRealSeeders() {
        every { torrentInfoService.refreshTorrentStats(any()) } just Runs
        every { multifileHosterService.canTakeCached(any()) } returns true
        val tse = TorrentSearchEngineService(HttpHelper(), multifileHosterService, torrentInfoService)
        val noStats = torrent("Daca.2021.1080p", -1, -1, "Premiumize")
        val noSeeders = torrent("Daca.2021.2160p", 0, 3, "Premiumize")
        val reported = torrent("Daca.2021.720p", 40, 5, "Premiumize")

        val results = tse.rateResults(mutableListOf(noStats, noSeeders, reported), "Daca 2021")

        Assertions.assertThat(results).containsExactlyInAnyOrder(noStats, noSeeders, reported)
        // no made up seeders, a hoster without the cache needs the real numbers
        Assertions.assertThat(noStats.seeder).isEqualTo(-1)
        Assertions.assertThat(noSeeders.seeder).isEqualTo(0)
        Assertions.assertThat(reported.seeder).isEqualTo(40)
    }

    @Test
    fun aCachedTorrentIsOnlyRatedAsInstantIfItsHosterCanTakeIt() {
        every { torrentInfoService.refreshTorrentStats(any()) } just Runs
        val tse = TorrentSearchEngineService(HttpHelper(), multifileHosterService, torrentInfoService)
        val cachedWithoutSeeders = Torrent().apply {
            name = "Daca.2021.1080p.BluRay"; sizeInMB = 8000.0; seeder = 0; leecher = 0
            magnetUri = "magnet:?xt=urn:btih:" + "a".repeat(40); cached.add("Premiumize")
        }
        val alive = Torrent().apply {
            name = "Daca.2021.1080p.WEB"; sizeInMB = 8000.0; seeder = 50; leecher = 2
            magnetUri = "magnet:?xt=urn:btih:" + "b".repeat(40)
        }

        every { multifileHosterService.canTakeCached(any()) } returns true
        Assertions.assertThat(tse.rateResults(mutableListOf(alive, cachedWithoutSeeders), "Daca 2021").first()).isEqualTo(cachedWithoutSeeders)

        every { multifileHosterService.canTakeCached(any()) } returns false
        Assertions.assertThat(tse.rateResults(mutableListOf(alive, cachedWithoutSeeders), "Daca 2021").first()).isEqualTo(alive)
    }

    @Test
    fun uncachedTorrentsWithoutSeedersAreDropped() {
        every { torrentInfoService.refreshTorrentStats(any()) } just Runs
        val tse = TorrentSearchEngineService(HttpHelper(), multifileHosterService, torrentInfoService)
        val dead = torrent("Daca.2021.1080p", 0, 0)
        val unknown = torrent("Daca.2021.720p", -1, -1)
        val alive = torrent("Daca.2021.2160p", 12, 2)

        Assertions.assertThat(tse.rateResults(mutableListOf(dead, unknown, alive), "Daca 2021")).containsExactly(alive)
    }

    @Test
    fun cachedTorrentsAreAskedForTheirStatsToo() {
        every { multifileHosterService.canTakeCached(any()) } returns true
        val tse = TorrentSearchEngineService(HttpHelper(), multifileHosterService, torrentInfoService)
        every { torrentInfoService.refreshTorrentStats(any()) } answers { firstArg<List<Torrent>>().forEach { it.seeder = 7 } }
        val cached = torrent("Daca.2021.1080p", -1, -1, "Premiumize")
        val uncached = torrent("Daca.2021.720p", -1, -1)

        tse.rateResults(mutableListOf(cached, uncached), "Daca 2021")

        Assertions.assertThat(uncached.seeder).isEqualTo(7)
        Assertions.assertThat(cached.seeder).isEqualTo(7)
    }
}
