package boat

import boat.info.MediaItem
import boat.info.MediaProxyService
import boat.info.MediaType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ContentRenamerMovieTest {

    private val mediaProxyService: MediaProxyService = mockk(relaxed = true)
    private val contentRenamer = ContentRenamer(mediaProxyService)

    private val inception = MediaItem("Inception", "Inception", 2010, MediaType.Movie, "{tmdb-27205}")

    private fun createFile(base: File, relativePath: String): File {
        return File(base, relativePath).apply {
            parentFile.mkdirs()
            writeText("x")
        }
    }

    @Test
    fun `should remove release groups attached with dash`() {
        assertThat(contentRenamer.removeReleaseGroups("sparks-inception")).isEqualTo("inception")
        assertThat(contentRenamer.removeReleaseGroups("Inception.2010.1080p.BluRay.x264-SPARKS")).isEqualTo("Inception.2010.1080p.BluRay.x264")
        assertThat(contentRenamer.removeReleaseGroups("Spider-Man")).isEqualTo("Spider-Man")
        assertThat(contentRenamer.removeReleaseGroups("RARBG")).isEqualTo("RARBG")
        assertThat(contentRenamer.extractName(File("~/Movies/I/Inception.2010.1080p.BluRay.x264-SPARKS/sparks-inception.mkv"))).isEqualToIgnoringCase("inception")
        // a file named like a release group falls back to the folder name
        assertThat(contentRenamer.extractName(File("~/Movies/I/Inception.2010.1080p.BluRay.x264-SPARKS/RARBG.txt"))).isEqualTo("Inception")
    }

    @Test
    fun `should strip remote media id from extracted name`() {
        assertThat(contentRenamer.extractName(File("~/Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.mkv"))).isEqualTo("Inception")
    }

    @Test
    fun `should search movies with numbers in the title as movie in movies folder`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        createFile(base, "Movies/A/Apollo 13 (1995)/Apollo 13 (1995).mkv")
        val apollo = MediaItem("Apollo 13", "Apollo 13", 1995, MediaType.Movie, "{tmdb-568}")
        every { mediaProxyService.search("Apollo 13", 1995, MediaType.Movie) } returns listOf(apollo)

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        verify { mediaProxyService.search("Apollo 13", 1995, MediaType.Movie) }
        assertThat(File(base, "Movies/A/Apollo 13 (1995) {tmdb-568}.mkv")).exists()
    }

    @Test
    fun `should use remote media id of already renamed files`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val renamed = createFile(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.mkv")
        every { mediaProxyService.findByRemoteMediaId("{tmdb-27205}", any(), MediaType.Movie) } returns inception

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        verify(exactly = 0) { mediaProxyService.search(any(), any<Int>(), any()) }
        verify(exactly = 0) { mediaProxyService.search(any(), any<MediaType>()) }
        assertThat(renamed).exists()
    }

    @Test
    fun `should only rename video and subtitle files of movies`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val folder = "Movies/I/Inception.2010.1080p.BluRay.x264-SPARKS"
        createFile(base, "$folder/sparks-inception.mkv")
        createFile(base, "$folder/Inception.2010.en.srt")
        val nfo = createFile(base, "$folder/RARBG.txt")
        every { mediaProxyService.search(any(), 2010, MediaType.Movie) } returns listOf(inception)

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(File(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.mkv")).exists()
        assertThat(File(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.en.srt")).exists()
        assertThat(nfo).exists()
    }

    @Test
    fun `should parse movie title and year from release names`() {
        val expected = mapOf(
            "127.Hours.2010.720p.BluRay.x264" to Pair("127 Hours", 2010),
            "300.2006.1080p.Bluray.x264" to Pair("300", 2006),
            "1408(2007)BDRip-F.HD" to Pair("1408", 2007),
            "101 Dalmatians" to Pair("101 Dalmatians", null),
            "1917.2019.2160p.UHD.BluRay.x265.10bit.HDR.DTS-HD.MA.TrueHD.7.1.Atmos-SWTYBLZ" to Pair("1917", 2019),
            "2001.A.Space.Odyssey.1968.REMASTERED.1080p.BluRay.x264.DTS-HD.MA.5.1-FGT" to Pair("2001 A Space Odyssey", 1968),
            "2012.2009.1080p.BluRay.x264" to Pair("2012", 2009),
            "10.Cloverfield.Lane.2016.4K.UltraHD.BluRay.2160p.x264.DTS-HD.MA.TrueHD.7.1.Atmos.ACC-7.1-POOP" to Pair("10 Cloverfield Lane", 2016),
            "Alien.Romulus.2024.2160p.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-FLUX" to Pair("Alien Romulus", 2024),
            "12 Years a Slave (2013) 1080p x264 ENG-ITA BluRay - 12 Anni Schiavo" to Pair("12 Years a Slave", 2013),
            "Gold.Diggers.of.1935.(1935).95.min.Dick.Powell.H264" to Pair("Gold Diggers of 1935", 1935),
            "Zabriskie Point (Antonioni 1970) [HD x264 Ita Ac3 Eng-Esp-Deu Aac - Multisubs]" to Pair("Zabriskie Point", 1970),
            "Children of Men (BDrip 1080p ENG-ITA-FRA-GER-SPA - DTS) multisub x264 bluray (2006)" to Pair("Children of Men", null),
            "Wrong Turn UNRATED (2003) [1080p]" to Pair("Wrong Turn", 2003),
            "Agent.Ranjid.rettet.die.Welt.German.AC3.BDRip.XviD" to Pair("Agent Ranjid rettet die Welt", null),
            "13.Semester.German.2009.DVDRip.XviD" to Pair("13 Semester", 2009),
            "Charlotte's Web 2006 1080p" to Pair("Charlotte's Web", 2006),
            "Diamonds Are Forever 1080p DTS HighCode" to Pair("Diamonds Are Forever", null),
            "Kung Fu Panda 2 2011 1080p BluRay x264 Dual Audio [Hindi DD 5.1 - English DD 5.1] ESub [MW]" to Pair("Kung Fu Panda 2", 2011),
            "01. The BIG Boss (1971 DUAL Audio - 720p BluRay)" to Pair("The BIG Boss", 1971),
            "05a. GAME of DEATH (1978 - 720p BluRay)" to Pair("GAME of DEATH", 1978),
            "Inception (2010) {tmdb-27205}.1080p.x264" to Pair("Inception", 2010),
            "Das.Boot.Directors.Cut.1981.1080p.x264" to Pair("Das Boot", 1981),
            "Black.Hawk.Down.-.Extended.Cut.2001.MULTi.2160p.UHD.Blu-ray" to Pair("Black Hawk Down", 2001),
            "Eternals+2021+1080p+" to Pair("Eternals", 2021),
            "Ali-Angst.essen.Seele.auf-Fear.Eats.the.Soul-1974--720p-EN.subs-x265-HEVC" to Pair("Ali-Angst essen Seele auf-Fear Eats the Soul", 1974),
            "Spider-Man.2002.1080p" to Pair("Spider-Man", 2002),
            "Man.Up.2015-HD" to Pair("Man Up", 2015),
            "www.torrenting.com  - Style.Wars.1983.REMASTERED.EXTRAS.REPACK.DVDRip" to Pair("Style Wars", 1983),
            "Werner.-.Beinhart!1990.WEB-DL.1080p" to Pair("Werner - Beinhart", 1990),
            "Winter.Soldier.with.Extras.1972.Documentary.Vietnam.War.DVDRip.480p" to Pair("Winter Soldier", 1972),
            "Wonder.Woman.1984.2020.HMAX.IMAX.2160p.HDR.WEB-DL" to Pair("Wonder Woman 1984", 2020),
        )
        expected.forEach { (rawName, titleAndYear) ->
            assertThat(contentRenamer.parseMovieName(rawName)).describedAs(rawName).isEqualTo(titleAndYear)
        }
    }

    @Test
    fun `should not take a leading year for the release year`() {
        assertThat(contentRenamer.extractYear("~/Movies/0-9/1917.2019.1080p.mkv")).isEqualTo(2019)
        assertThat(contentRenamer.extractYear("~/Movies/0-9/2001.A.Space.Odyssey.1968.1080p.mkv")).isEqualTo(1968)
        assertThat(contentRenamer.extractYear("~/Movies/0-9/2022.mp4")).isEqualTo(2022)
    }

    @Test
    fun `should name movies in a collection after their file`() {
        val collection = "~/Movies/B/BRUCE Lee (1971-1978) and BRANDON Lee (1986-1994) - The ULTIMATE Movies Collection - 720p x264/a. Bruce (1971-1978)"
        assertThat(contentRenamer.extractMovieNameAndYear(File("$collection/01. The BIG Boss (1971 DUAL Audio - 720p BluRay).mp4")))
            .isEqualTo(Pair("The BIG Boss", 1971))
        // files without a title are named after their folder
        assertThat(contentRenamer.extractMovieNameAndYear(File("~/Movies/I/Inception.2010.1080p.BluRay.x264-SPARKS/RARBG.txt")))
            .isEqualTo(Pair("Inception", 2010))
        assertThat(contentRenamer.extractMovieNameAndYear(File("~/Movies/0-9/00000.m2ts"))).isEqualTo(Pair("", null))
        // short titles are fine with a release year
        assertThat(contentRenamer.extractMovieNameAndYear(File("~/Movies/I/It (2017) [1080p] [YTS.AG].mp4"))).isEqualTo(Pair("It", 2017))
        assertThat(contentRenamer.extractMovieNameAndYear(File("~/Movies/0-9/48.Hrs.1982.1080p.BluRay.x264-TENEIGHTY.mkv"))).isEqualTo(Pair("48 Hrs", 1982))
        assertThat(contentRenamer.extractMovieNameAndYear(File("~/Movies/I/Interview.with.the.Vampire.1994.1080p.mp4"))).isEqualTo(Pair("Interview with the Vampire", 1994))
        assertThat(contentRenamer.extractMovieNameAndYear(File("~/Movies/H/H264.m4v"))).isEqualTo(Pair("", null))
        assertThat(contentRenamer.extractMovieNameAndYear(File("~/Movies/T/The Texas Chainsaw Massacre Collection (1974-2017)/2017.Leatherface.mkv")))
            .isEqualTo(Pair("Leatherface", 2017))
        // extras are named after their movie
        assertThat(contentRenamer.extractMovieNameAndYear(File("~/Movies/U/Uncut Gems (2019)/Uncut Gems Trailer 2-trailer.mkv"))).isEqualTo(Pair("Uncut Gems", 2019))
        assertThat(contentRenamer.extractMovieNameAndYear(File("~/Movies/C/The.French.Connection.1971.Bluray.1080p.DTS-HD.x264-Grym/The.French.Connection.Extras-Grym/The.Poughkeepsie.Shuffle.2000.SD.NTSC.mkv")))
            .isEqualTo(Pair("The French Connection", 1971))
    }

    @Test
    fun `should detect series in the movies folder`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        assertThat(contentRenamer.hasSeriesMarkers(createFile(base, "Movies/M/Mr.Inbetween/Mr.Inbetween.S02E01.1080p.mkv"))).isTrue()
        assertThat(contentRenamer.hasSeriesMarkers(createFile(base, "Movies/S/Star.Trek.Discovery.S01-05.ITA/Star.Trek.Discovery.5x10.La.Vita.mkv"))).isTrue()
        assertThat(contentRenamer.hasSeriesMarkers(createFile(base, "Movies/K/Kekkai Sensen S01+02+OVA/Blood Blockade Battlefront/Blood Blockade Battlefront - 01.mkv"))).isTrue()
        (1..3).forEach { createFile(base, "Movies/L/Little Women/Tales.Of.Little.Women.EP$it.mkv") }
        assertThat(contentRenamer.hasSeriesMarkers(File(base, "Movies/L/Little Women/Tales.Of.Little.Women.EP1.mkv"))).isTrue()

        listOf("01", "02v2", "10").forEach { createFile(base, "Movies/G/[ARC] Gunslinger Girl (2003)/[ARC] Gunslinger Girl $it [BD 714p 10-bit x264 FLAC].mkv") }
        assertThat(contentRenamer.hasSeriesMarkers(File(base, "Movies/G/[ARC] Gunslinger Girl (2003)/[ARC] Gunslinger Girl 10 [BD 714p 10-bit x264 FLAC].mkv"))).isTrue()

        val gunslingerGirl10 = File(base, "Movies/G/[ARC] Gunslinger Girl (2003)/[ARC] Gunslinger Girl 10 [BD 714p 10-bit x264 FLAC].mkv")
        assertThat(contentRenamer.removeEpisodeNumber(contentRenamer.extractName(gunslingerGirl10), gunslingerGirl10)).isEqualTo("Gunslinger Girl")
        assertThat(contentRenamer.extractName(File("~/Movies/M/Mr. Bean - The Complete Collection (1990-2007)/S01 - E01 - Mr. Bean.mkv"))).isEqualTo("Mr Bean")

        // numbers in movie titles aren't episodes
        (11..13).forEach { createFile(base, "Movies/O/Oceans Trilogy/Oceans $it.mkv") }
        assertThat(contentRenamer.hasSeriesMarkers(File(base, "Movies/O/Oceans Trilogy/Oceans 11.mkv"))).isFalse()
        assertThat(contentRenamer.hasSeriesMarkers(createFile(base, "Movies/A/Apollo 13 (1995)/Apollo 13 (1995).mkv"))).isFalse()
        createFile(base, "Movies/S/Star.Wars.Episode.2.Attack.of.the.Clones.2002.mkv")
        createFile(base, "Movies/S/Star.Wars.Episode.3.Revenge.of.the.Sith.2005.mkv")
        assertThat(contentRenamer.hasSeriesMarkers(createFile(base, "Movies/S/Star.Wars.Episode.1.The.Phantom.Menace.1999.mkv"))).isFalse()
        assertThat(contentRenamer.hasSeriesMarkers(createFile(base, "Movies/O/Open.Season.2006.1080p/Open.Season.2006.1080p.mkv"))).isFalse()
        assertThat(contentRenamer.hasSeriesMarkers(createFile(base, "Movies/A/Adieu.Galaxy.Express.999.(1981).(BDRip.1920x1080p.x265.FLACx2,.TrueHD.2.0x2+5.1).mkv"))).isFalse()
    }

    @Test
    fun `should search series in the movies folder as series`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        createFile(base, "Movies/M/Mr.Inbetween/Mr.Inbetween.S02E01.1080p.AMZN.WEB-DL.DDP5.1.H.264-KiNGS.mkv")
        val mrInbetween = MediaItem("Mr Inbetween", "Mr Inbetween", 2018, MediaType.Series, "{tvdb-345678}")
        every { mediaProxyService.search("Mr Inbetween", MediaType.Series) } returns listOf(mrInbetween)

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        verify(exactly = 0) { mediaProxyService.search(any(), MediaType.Movie) }
        assertThat(File(base, "Series-Shows/M/Mr Inbetween {tvdb-345678}/Season 2/Mr Inbetween.S02E01.1080p.h264.mkv")).exists()
    }

    @Test
    fun `should skip disc structures, courses and files that aren't movies`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val stream = createFile(base, "Movies/M/Minari.2020.KOREAN.1080p.BluRay.AVC-FGT/BDMV/STREAM/00001.m2ts")
        val course = createFile(base, "Movies/C/CBT Nuggets - Amazon Web Services - AWS Foundations/01 - Getting the Most from this Series.mp4")
        val pdf = createFile(base, "Movies/I/Inception.2010.1080p/Inception.2010.pdf")

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        verify(exactly = 0) { mediaProxyService.search(any(), any<Int>(), any()) }
        verify(exactly = 0) { mediaProxyService.search(any(), any<MediaType>()) }
        assertThat(listOf(stream, course, pdf)).allMatch { it.exists() }
    }

    @Test
    fun `should not rename a movie to a namesake released years apart`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val air = createFile(base, "Movies/A/Air.2015.1080p.BluRay.x264-[YTS.LT].mp4")
        every { mediaProxyService.search("Air", 2015, MediaType.Movie) } returns emptyList()
        every { mediaProxyService.search("Air", MediaType.Movie) } returns listOf(MediaItem("Air", "Air", 2023, MediaType.Movie, "{tmdb-964980}"))

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(air).exists()
    }

    @Test
    fun `should prefer the exact title released within a year`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        createFile(base, "Movies/F/Fargo.1995.RERIP.Remastered.Bluray.1080p.DTS-HD.x264-Grym.mkv")
        every { mediaProxyService.search("Fargo", 1995, MediaType.Movie) } returns listOf(MediaItem("Danzig: [1995] Live at Fargo, ND", null, 1995, MediaType.Movie, "{tmdb-1}"))
        every { mediaProxyService.search("Fargo", MediaType.Movie) } returns listOf(MediaItem("Fargo", "Fargo", 1996, MediaType.Movie, "{tmdb-275}"))

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(File(base, "Movies/F/Fargo (1996) {tmdb-275}.1080p.x264.mkv")).exists()
    }

    @Test
    fun `should skip namesakes released years apart`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        createFile(base, "Movies/M/Mulan.2009.1080p.BluRay.x264-LCHD.mkv")
        every { mediaProxyService.search("Mulan", 2009, MediaType.Movie) } returns listOf(
            MediaItem("Mulan", "Mulan", 1998, MediaType.Movie, "{tmdb-10674}"),
            MediaItem("Mulan: Rise of a Warrior", "花木兰", 2009, MediaType.Movie, "{tmdb-25707}")
        )

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(File(base, "Movies/M/Mulan Rise of a Warrior (2009) {tmdb-25707}.1080p.x264.mkv")).exists()
    }
}
