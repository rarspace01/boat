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
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/N/Nikulo/Nikulo Complete Collection/3.Nikulo Movies/1 - Noget Cakez in the Lufu of Seme.mkv"))).isTrue()
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/A/ASUG FABE (1991-1995) - Complete Animated TV Series and 2005 Movie - 720p x264/Movie (2005)/Asug Fabe - The Movie (2005 - 720p BluRay).mp4"))).isTrue()
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/T/Tatene/Tatene/Tatene Movie Badlands Rumble [B529030E].mkv"))).isTrue()

        // packs of episodes and movies don't make their episodes movies
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/E/Ecuf of the Edaz/Ecuf of the Edaz 1-11+Movie 1-2 Complete (BDRip 1080p AC3 10bit) [HEVC] Dual Audio/Ecuf of the Edaz 10 Who Killed Azilo Takizawa (BDRip 1080p AC3 10bit).mkv"))).isFalse()
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/H/Hbupet.Qdatu.(2019-2020).1.S01.And.Birds.Of.Prey.Movie.Web264/Season 1 (2019-20)/Hbupet Qdatu - S01 E01 - Til Death Do Us Part (1080p - DCU Web-DL).mp4"))).isFalse()
        // film versions of episodes, seasons, OVAs, extras and shows already in the library
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/A/The Adventures of Young Indiana Jones/The Adventures of Young Indiana Jones Film Versions 01 My First Adventure.avi"))).isFalse()
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/W/Wgolicaraba.(2021).Movie/WgoliCaraba.(2021).Movie.Season1.1080p.H264.AAC.2.0-IceJackal.mp4"))).isFalse()
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/R/Ruvem/Ruvem Movies & OVA/Ruvem - OVA 01.mkv"))).isFalse()
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/B/Bigade.Cenu.Sote.(2015)/Featurettes/Season 6/Training Videos/Movie Magic.mkv"))).isFalse()
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/O/The Osugol (US) {tvdb-73244}/Season 7/Tipici Lozuk Macatadu The Movie.mkv"))).isFalse()
        assertThat(contentRenamer.isMovieInSeriesFolder(File("~/Series-Shows/F/Fotidik/Fotidik Complete Series/00 Fotidik Sazisoba - Pilot Film 2002 Eng Subs 1080p [H264-mp4].mp4"))).isFalse()
    }

    @Test
    fun extractMovieTitlesWithoutTheirNumbering() {
        fun nameAndYear(path: String) = contentRenamer.extractMovieInSeriesNameAndYear(File(path))

        assertThat(nameAndYear("~/Series-Shows/R/Ruvem/Ruvem Movies & OVA/Ruvem?.[1992].-.Movie.02.-.Nubuc.My.Cebidagen.[1080p.BluRay.x264].[Jd].[DHD].mkv"))
            .isEqualTo(Pair("Ruvem Nubuc My Cebidagen", 1992))
        assertThat(nameAndYear("~/Series-Shows/H/Hcates no Kes/Movies/[Samir755] Hcates no Kes - Movie 2006 - The Lazeru of Ruri Death in Love.mkv"))
            .isEqualTo(Pair("Hcates no Kes The Lazeru of Ruri Death in Love", 2006))
        assertThat(nameAndYear("~/Series-Shows/N/Nikulo/Nikulo Complete Collection/3.Nikulo Movies/1 - Noget Cakez in the Lufu of Seme.mkv"))
            .isEqualTo(Pair("Noget Cakez in the Lufu of Seme", null))
        assertThat(nameAndYear("~/Series-Shows/K/KAD PELAFEDO (2002-2019) - Complete TV Series/g. LIVE-Action Film (2019)/Kad Pelafedo - The Movie (2019 - 1080p AMZN Web-DL).mp4"))
            .isEqualTo(Pair("Kad Pelafedo", 2019))
        assertThat(nameAndYear("~/Series-Shows/D/DOSO (1991-1999) - Complete Animated TV Series/Movie (1999)/DOGOm 1st Movie (1999 - 480p Web-DL).mp4"))
            .isEqualTo(Pair("DOGOm 1st Movie", 1999))
        assertThat(nameAndYear("~/Series-Shows/I/I Spy - 1965 to 1967 (Complete TV series in MP4 format)/I Spy - Movie - 2002 (Eddie Murphy - Owen Wilson).mp4"))
            .isEqualTo(Pair("I Spy", 2002))
    }

    @Test
    fun titlesDontMakeHiddenFiles() {
        val hack = MediaItem(".hack//G.U. Trilogy", year = 2007, type = MediaType.Movie, id = "{tmdb-26595}")
        assertThat(contentRenamer.getTargetFile(File("[GAX]dot_hack-G.U._Trilogy_-_Movie(ger.sub)(h264)[525944DF].mkv"), hack, "", null, null, "/PFDB"))
            .isEqualTo("/PFDB/Movies/H/hack G.U. Trilogy (2007) {tmdb-26595}.h264.mkv")
    }

    @Test
    fun moveMoviesFromSeriesFoldersToTheMovies(@TempDir tempDir: File) {
        val show = File(tempDir, "Series-Shows/B/BOGABO The Atavibik Sasase (1992-1999) - COMPLETE Season 1-6 and 2 Movies")
        val movies = File(show, "Movies (1993-98)").apply { mkdirs() }
        val theatrical = File(movies, "Bogabo - Mask of The Pupilemo, Theatrical Version (1993 - 1080p BluRay).mp4").apply { writeText("theatrical") }
        val openMatte = File(movies, "Bogabo - Mask of The Pupilemo, Open Matte Version (1993 - 1080p BluRay).mp4").apply { writeText("open matte") }
        val episode = File(File(show, "Season 1").apply { mkdirs() }, "Bogabo The Atavibik Sasase - S01E01 - On Leather Wings.mp4").apply { writeText("episode") }

        val pupilemo = MediaItem("Bogabo: Mask of the Pupilemo", year = 1993, type = MediaType.Movie, id = "{tmdb-14919}")
        val series = MediaItem("Bogabo: The Atavibik Sasase", year = 1992, type = MediaType.Series, id = "{tvdb-76168}")
        every { mediaProxyService.search(any(), any<Int>(), MediaType.Movie) } returns listOf(pupilemo)
        every { mediaProxyService.search(any(), MediaType.Movie) } returns listOf(pupilemo)
        every { mediaProxyService.search(any(), any<Int>(), MediaType.Series) } returns listOf(series)
        every { mediaProxyService.search(any(), MediaType.Series) } returns listOf(series)

        val library = File(tempDir, "PFDB").absolutePath
        contentRenamer.processFilesFromDirectory(File(tempDir, "Series-Shows").absolutePath, isTestRun = false, basePath = library)

        // the episode stays a series, one edition of the movie is moved, the other one isn't overwritten
        assertThat(File(library, "Series-Shows/B/Bogabo The Atavibik Sasase {tvdb-76168}/Season 1")).isDirectory()
        assertThat(episode).doesNotExist()
        val movieFile = File(library, "Movies/B/Bogabo Mask of the Pupilemo (1993) {tmdb-14919}.1080p.mp4")
        assertThat(movieFile).exists()
        assertThat(listOf(theatrical, openMatte).count { it.exists() }).isEqualTo(1)
        assertThat(movieFile.readText()).isIn("theatrical", "open matte")
    }
}
