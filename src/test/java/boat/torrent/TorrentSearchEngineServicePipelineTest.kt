package boat.torrent

import boat.multifileHoster.MultifileHosterService
import boat.utilities.HttpHelper
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TorrentSearchEngineServicePipelineTest {

    private val multifileHosterService: MultifileHosterService = mockk()
    private val torrentInfoService: TorrentInfoService = mockk()

    private fun hash(index: Int) = String.format("%040x", index.toLong() + 1)

    private fun torrent(index: Int, name: String = "Dune.2021.$index.1080p.BluRay.x264", seeders: Int = 10, sizeInMB: Double = 4000.0) =
        Torrent("E").apply {
            this.name = name
            magnetUri = "magnet:?xt=urn:btih:" + hash(index)
            seeder = seeders
            leecher = 1
            this.sizeInMB = sizeInMB
            size = "4 GB"
        }

    private fun engine(results: () -> List<Torrent>) = object : TorrentSearchEngine {
        override fun searchTorrents(searchName: String): List<Torrent> = results()
        override fun getBaseUrl(): String = "http://engine.test"
    }

    private fun service(vararg engines: TorrentSearchEngine): TorrentSearchEngineService {
        every { torrentInfoService.refreshTorrentStats(any()) } just Runs
        every { multifileHosterService.getCachedStateOfTorrents(any()) } answers { firstArg() }
        val service = TorrentSearchEngineService(HttpHelper(), multifileHosterService, torrentInfoService, engines.toList())
        return service
    }

    @Test
    fun shouldReturnAtMost100ResultsAndCutOnlyAfterRatingAndSorting() {
        // 300 torrents with different seeders and sizes
        val torrents = (1..300).map { torrent(it, seeders = (it * 7) % 61 + 1, sizeInMB = 700.0 + (it * 13) % 9000) }
        val service = service(engine { torrents })

        val results = service.searchTorrents("Dune 2021")

        assertThat(results).hasSize(TorrentSearchEngineService.MAX_SEARCH_RESULTS)
        // sorted by the final rating, and what was cut has a rating which is not better than anything that is shown
        assertThat(results.map { it.searchRating }).isSortedAccordingTo(Comparator.reverseOrder())
        val cut = torrents.filter { it !in results }
        assertThat(cut).hasSize(200)
        assertThat(cut.maxOf { it.searchRating }).isLessThanOrEqualTo(results.last().searchRating)
    }

    @Test
    fun shouldAskForTheCacheAndTrackerStatsOfEverythingBeforeTheCut() {
        val torrents = (1..250).map { torrent(it) }
        val service = service(engine { torrents })
        val checked = slot<List<Torrent>>()
        every { multifileHosterService.getCachedStateOfTorrents(capture(checked)) } answers { firstArg() }

        service.searchTorrents("Dune 2021")

        assertThat(checked.captured).hasSize(250)
    }

    @Test
    fun shouldDropBadQualityExplicitAndEmptyTorrentsBeforeTheLookups() {
        val good = torrent(1)
        val cam = torrent(2, name = "Dune.2021.HDCAM.x264")
        val noMagnet = torrent(3).apply { magnetUri = "" }
        val noSize = torrent(4, sizeInMB = 0.0)
        val explicit = torrent(5, name = "Dune.2021.porn.1080p")
        val checked = slot<List<Torrent>>()
        val service = service(engine { listOf(good, cam, noMagnet, noSize, explicit) })
        every { multifileHosterService.getCachedStateOfTorrents(capture(checked)) } answers { firstArg() }

        val results = service.searchTorrents("Dune 2021")

        assertThat(checked.captured).containsExactly(good)
        assertThat(results).containsExactly(good)
    }

    @Test
    fun shouldDropTorrentsWithoutSeedersAtTheEndButKeepCachedOnes() {
        val dead = torrent(1, seeders = 0)
        val alive = torrent(2, seeders = 5)
        val cachedWithoutSeeders = torrent(3, seeders = 0).apply { cached.add("Premiumize") }
        val service = service(engine { listOf(dead, alive, cachedWithoutSeeders) })
        every { multifileHosterService.getCachedStateOfTorrents(any()) } answers { firstArg() }

        val results = service.searchTorrents("Dune 2021")

        assertThat(results).containsExactlyInAnyOrder(alive, cachedWithoutSeeders)
    }

    @Test
    fun shouldKeepTheBetterRatedOfDuplicatesFromSeveralEngines() {
        val weak = torrent(1, name = "Dune.2021.1080p").apply { searchRating = 1.0 }
        val strong = torrent(1, name = "Dune.2021.1080p.BluRay.x264").apply { searchRating = 9.0 }

        assertThat(TorrentSearchEngineService(HttpHelper(), multifileHosterService, torrentInfoService, emptyList())
            .cleanDuplicates(listOf(weak, strong))).containsExactly(strong)
    }

    @Test
    fun shouldSurviveAnEngineWhichFails() {
        val service = service(engine { throw IllegalStateException("down") }, engine { listOf(torrent(1)) })

        assertThat(service.searchTorrents("Dune 2021")).hasSize(1)
    }

    @Test
    fun shouldOnlyKeepTheSeasonWhichWasSearchedBeforeTheLookupsAndTheCut() {
        val season1 = torrent(1, name = "The.Office.US.S01.1080p.BluRay.x264")
        val season2 = torrent(2, name = "The.Office.US.S02E05.1080p.WEB-DL.x264")
        val season2pack = torrent(3, name = "The Office US Season 2 Complete 720p")
        val series = torrent(4, name = "The Office US Complete Series 1080p")
        val noSeason = torrent(5, name = "The Office US 2005 1080p WEB-DL")
        val checked = slot<List<Torrent>>()
        val service = service(engine { listOf(season1, season2, season2pack, series, noSeason) })
        every { multifileHosterService.getCachedStateOfTorrents(capture(checked)) } answers { firstArg() }

        val results = service.searchTorrents("The Office S02", season = 2)

        assertThat(checked.captured).containsExactlyInAnyOrder(season2, season2pack, series)
        assertThat(results).containsExactlyInAnyOrder(season2, season2pack, series)
    }

    @Test
    fun shouldKeepEverythingWithoutASeason() {
        val season1 = torrent(1, name = "The.Office.US.S01.1080p.BluRay.x264")
        val season2 = torrent(2, name = "The.Office.US.S02E05.1080p.WEB-DL.x264")
        val service = service(engine { listOf(season1, season2) })

        assertThat(service.searchTorrents("The Office")).containsExactlyInAnyOrder(season1, season2)
    }
}
