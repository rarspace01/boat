package boat

import boat.info.CloudFileService
import boat.info.CloudService
import boat.info.MediaItem
import boat.info.MediaProxyService
import boat.info.MediaType
import boat.info.QueueService
import boat.multifileHoster.MultifileHosterService
import boat.services.ConfigurationService
import boat.services.TransferService
import boat.torrent.TorrentSearchEngineService
import boat.utilities.HttpHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class BoatControllerSearchTest {

    private val torrentSearchEngineService: TorrentSearchEngineService = mockk()
    private val cloudService: CloudService = mockk()
    private val mediaProxyService: MediaProxyService = mockk()

    private val controller = BoatController(
        mockk<HttpHelper>(), torrentSearchEngineService, cloudService, mediaProxyService, mockk<MultifileHosterService>(),
        mockk<QueueService>(), mockk<CloudFileService>(), mockk<TransferService>(), mockk<ConfigurationService>()
    )

    private val dune = MediaItem("Dune", "Dune", 2021, MediaType.Movie, "{tmdb-438631}")
    private val dark = MediaItem("Dark", "Dark", 2017, MediaType.Series, "{tvdb-334824}")
    private val office = MediaItem("The Office", "The Office", 2005, MediaType.Series, "{tmdb-2316}")

    private fun search(items: List<MediaItem>, seasons: Map<MediaItem, Int?> = emptyMap()): String {
        every { mediaProxyService.searchWithTrailingYear("x") } returns items
        every { mediaProxyService.seasonCountOf(any()) } answers { seasons[firstArg()] }
        return controller.searchTorrents("x", null, null, null, null, null, null)
    }

    @Test
    fun shouldLinkTheTitlesToTmdbAndTvdbToCheckThem() {
        val html = search(listOf(dune, dark, office))

        assertThat(html).contains("href=\"https://www.themoviedb.org/movie/438631\"")
        assertThat(html).contains("href=\"https://thetvdb.com/dereferrer/series/334824\"")
        assertThat(html).contains("href=\"https://www.themoviedb.org/tv/2316\"")
        // after the title, which still starts a normal search
        assertThat(html).contains("<a href=\"?t=Dune&y=2021&mid=%7Btmdb-438631%7D&mt=Movie\">Dune (2021) [Movie] {tmdb-438631}</a> <a href=\"https://www.themoviedb.org/movie/438631\"")
    }

    @Test
    fun shouldOfferTheSeasonsOfASeries() {
        val html = search(listOf(dark, dune), mapOf(dark to 3))

        assertThat(html).contains("Season: <a href=\"?t=Dark&mid=%7Btvdb-334824%7D&mt=Series&s=1\">S1</a> <a href=\"?t=Dark&mid=%7Btvdb-334824%7D&mt=Series&s=2\">S2</a> <a href=\"?t=Dark&mid=%7Btvdb-334824%7D&mt=Series&s=3\">S3</a>")
        assertThat(html).doesNotContain("s=4")
        // the title itself searches the whole series, movies have no seasons
        assertThat(html).contains("<a href=\"?t=Dark&mid=%7Btvdb-334824%7D&mt=Series\">Dark (2017) [Series] {tvdb-334824}</a>")
        assertThat(html.substringAfter("Dune").substringBefore("<br/>")).doesNotContain("Season:")
    }

    @Test
    fun shouldNotOfferSeasonsIfTheNumberIsNotKnown() {
        val html = search(listOf(dark), mapOf(dark to null))

        assertThat(html).doesNotContain("Season:")
    }

    @Test
    fun shouldEscapeTitlesAndLinks() {
        val evil = MediaItem("<script>alert(1)</script>", year = 2000, type = MediaType.Series, id = "{tmdb-1}\"onmouseover=\"x")

        val html = search(listOf(evil), mapOf(evil to 2))

        assertThat(html).doesNotContain("<script>")
        assertThat(html).doesNotContain("\"onmouseover=\"")
    }

    @Test
    fun shouldSearchTheTorrentsOfASeasonWithTheSeasonInTheSearchString() {
        every { cloudService.findExistingFiles(any()) } returns emptyList()
        every { torrentSearchEngineService.searchTorrents("Dark S02", 2) } returns emptyList()

        val html = controller.searchTorrents(null, "Dark", null, null, "{tvdb-334824}", "Series", 2)

        verify(exactly = 1) { torrentSearchEngineService.searchTorrents("Dark S02", 2) }
        assertThat(html).contains("Season 2 only")
    }

    @Test
    fun shouldSearchTheWholeSeriesWithoutASeason() {
        every { cloudService.findExistingFiles(any()) } returns emptyList()
        every { torrentSearchEngineService.searchTorrents("Dark", null) } returns emptyList()

        controller.searchTorrents(null, "Dark", null, null, "{tvdb-334824}", "Series", null)

        verify(exactly = 1) { torrentSearchEngineService.searchTorrents("Dark", null) }
    }

    @Test
    fun shouldKeepTheSeasonWhenSearchingAgainDespiteExistingFiles() {
        every { cloudService.findExistingFiles(any()) } returns listOf("remote:Series-Shows/D/Dark/Season 1/Dark.S01E01.mkv")

        val html = controller.searchTorrents(null, "Dark", null, null, "{tvdb-334824}", "Series", 3)

        assertThat(html).contains("?q=Dark+S03&mid=%7Btvdb-334824%7D&mt=Series&s=3")
    }

    @Test
    fun shouldBuildTheDatabasePageOfATitle() {
        assertThat(BoatController.mediaDatabaseUrl(MediaItem("A", type = MediaType.Movie, id = "{TMDB-12}"))).isEqualTo("https://www.themoviedb.org/movie/12")
        assertThat(BoatController.mediaDatabaseUrl(MediaItem("A", type = MediaType.Series, id = "{tmdb-12}"))).isEqualTo("https://www.themoviedb.org/tv/12")
        assertThat(BoatController.mediaDatabaseUrl(MediaItem("A", type = MediaType.Series, id = "{tvdb-34}"))).isEqualTo("https://thetvdb.com/dereferrer/series/34")
        assertThat(BoatController.mediaDatabaseUrl(MediaItem("A", type = MediaType.Series, id = "custom"))).isNull()
        assertThat(BoatController.mediaDatabaseUrl(MediaItem("A", type = MediaType.Series))).isNull()
    }
}
