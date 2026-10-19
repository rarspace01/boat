package boat

import boat.info.MediaItem
import boat.info.MediaProxyService
import boat.info.MediaType
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ContentRenamerMovieInSeriesTest {

    private val mediaProxyService: MediaProxyService = mockk(relaxed = true)
    private val contentRenamer = ContentRenamer(mediaProxyService)

    @Test
    fun detectMoviesInSeriesFolders() {
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/N/Naruto/Naruto Complete Collection/3.Naruto Movies/1 - Ninja Clash in the Land of Snow.mkv"))).isTrue()
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/A/AEON FLUX (1991-1995) - Complete Animated TV Series and 2005 Movie - 720p x264/Movie (2005)/Aeon Flux - The Movie (2005 - 720p BluRay).mp4"))).isTrue()
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/T/Trigun/Trigun/Trigun Movie Badlands Rumble [B529030E].mkv"))).isTrue()

        // packs of episodes and movies don't make their episodes movies
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/E/Eden of the East/Eden of the East 1-11+Movie 1-2 Complete (BDRip 1080p AC3 10bit) [HEVC] Dual Audio/Eden of the East 10 Who Killed Akira Takizawa (BDRip 1080p AC3 10bit).mkv"))).isFalse()
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/H/Harley.Quinn.(2019-2020).1.S01.And.Birds.Of.Prey.Movie.Web264/Season 1 (2019-20)/Harley Quinn - S01 E01 - Til Death Do Us Part (1080p - DCU Web-DL).mp4"))).isFalse()
        // film versions of episodes, seasons, OVAs, extras and shows already in the library
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/A/The Adventures of Young Indiana Jones/The Adventures of Young Indiana Jones Film Versions 01 My First Adventure.avi"))).isFalse()
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/W/Wandavision.(2021).Movie/WandaVision.(2021).Movie.Season1.1080p.H264.AAC.2.0-IceJackal.mp4"))).isFalse()
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/R/Ranma/Ranma Movies & OVA/Ranma - OVA 01.mkv"))).isFalse()
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/B/Better.Call.Saul.(2015)/Featurettes/Season 6/Training Videos/Movie Magic.mkv"))).isFalse()
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/O/The Office (US) {tvdb-73244}/Season 7/Threat Level Midnight The Movie.mkv"))).isFalse()
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/F/Firefly/Firefly Complete Series/00 Firefly Serenity - Pilot Film 2002 Eng Subs 1080p [H264-mp4].mp4"))).isFalse()
    }

    @Test
    fun extractMovieTitlesWithoutTheirNumbering() {
        fun nameAndYear(path: String) = contentRenamer.extractMovieInSeriesNameAndYear(File(path))

        assertThat(nameAndYear("~/Series-Shows/R/Ranma/Ranma Movies & OVA/Ranma?.[1992].-.Movie.02.-.Nihao.My.Concubine.[1080p.BluRay.x264].[Jd].[DHD].mkv"))
            .isEqualTo(Pair("Ranma Nihao My Concubine", 1992))
        assertThat(nameAndYear("~/Series-Shows/H/Hokuto no Ken/Movies/[Samir755] Hokuto no Ken - Movie 2006 - The Legend of Raoh Death in Love.mkv"))
            .isEqualTo(Pair("Hokuto no Ken The Legend of Raoh Death in Love", 2006))
        assertThat(nameAndYear("~/Series-Shows/N/Naruto/Naruto Complete Collection/3.Naruto Movies/1 - Ninja Clash in the Land of Snow.mkv"))
            .isEqualTo(Pair("Ninja Clash in the Land of Snow", null))
        assertThat(nameAndYear("~/Series-Shows/K/KIM POSSIBLE (2002-2019) - Complete TV Series/g. LIVE-Action Film (2019)/Kim Possible - The Movie (2019 - 1080p AMZN Web-DL).mp4"))
            .isEqualTo(Pair("Kim Possible", 2019))
        assertThat(nameAndYear("~/Series-Shows/D/DOUG (1991-1999) - Complete Animated TV Series/Movie (1999)/DOUGs 1st Movie (1999 - 480p Web-DL).mp4"))
            .isEqualTo(Pair("DOUGs 1st Movie", 1999))
        assertThat(nameAndYear("~/Series-Shows/I/I Spy - 1965 to 1967 (Complete TV series in MP4 format)/I Spy - Movie - 2002 (Eddie Murphy - Owen Wilson).mp4"))
            .isEqualTo(Pair("I Spy", 2002))
    }

    @Test
    fun moveMoviesFromSeriesFoldersToTheMovies(@TempDir tempDir: File) {
        val show = File(tempDir, "Series-Shows/B/BATMAN The Animated Series (1992-1999) - COMPLETE Season 1-6 and 2 Movies")
        val movies = File(show, "Movies (1993-98)").apply { mkdirs() }
        val theatrical = File(movies, "Batman - Mask of The Phantasm, Theatrical Version (1993 - 1080p BluRay).mp4").apply { writeText("theatrical") }
        val openMatte = File(movies, "Batman - Mask of The Phantasm, Open Matte Version (1993 - 1080p BluRay).mp4").apply { writeText("open matte") }
        val episode = File(File(show, "Season 1").apply { mkdirs() }, "Batman The Animated Series - S01E01 - On Leather Wings.mp4").apply { writeText("episode") }

        val phantasm = MediaItem("Batman: Mask of the Phantasm", year = 1993, type = MediaType.Movie, id = "{tmdb-14919}")
        val series = MediaItem("Batman: The Animated Series", year = 1992, type = MediaType.Series, id = "{tvdb-76168}")
        every { mediaProxyService.search(any(), any<Int>(), MediaType.Movie) } returns listOf(phantasm)
        every { mediaProxyService.search(any(), MediaType.Movie) } returns listOf(phantasm)
        every { mediaProxyService.search(any(), any<Int>(), MediaType.Series) } returns listOf(series)
        every { mediaProxyService.search(any(), MediaType.Series) } returns listOf(series)

        val library = File(tempDir, "PFDB").absolutePath
        contentRenamer.processFilesFromDirectory(File(tempDir, "Series-Shows").absolutePath, isTestRun = false, basePath = library)

        // the episode stays a series, one edition of the movie is moved, the other one isn't overwritten
        assertThat(File(library, "Series-Shows/B/Batman The Animated Series {tvdb-76168}/Season 1")).isDirectory()
        assertThat(episode).doesNotExist()
        val movieFile = File(library, "Movies/B/Batman Mask of the Phantasm (1993) {tmdb-14919}.1080p.mp4")
        assertThat(movieFile).exists()
        assertThat(listOf(theatrical, openMatte).count { it.exists() }).isEqualTo(1)
        assertThat(movieFile.readText()).isIn("theatrical", "open matte")
    }
}
