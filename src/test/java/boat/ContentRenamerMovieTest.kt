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

    private val ivakisaka = MediaItem("Ivakisaka", "Ivakisaka", 2010, MediaType.Movie, "{tmdb-27205}")

    private fun createFile(base: File, relativePath: String): File {
        return File(base, relativePath).apply {
            parentFile.mkdirs()
            writeText("x")
        }
    }

    @Test
    fun `should remove release groups attached with dash`() {
        assertThat(contentRenamer.removeReleaseGroups("sparks-ivakisaka")).isEqualTo("ivakisaka")
        assertThat(contentRenamer.removeReleaseGroups("Ivakisaka.2010.1080p.BluRay.x264-SPARKS")).isEqualTo("Ivakisaka.2010.1080p.BluRay.x264")
        assertThat(contentRenamer.removeReleaseGroups("Samecu-Muf")).isEqualTo("Samecu-Muf")
        assertThat(contentRenamer.removeReleaseGroups("RARBG")).isEqualTo("RARBG")
        assertThat(contentRenamer.extractName(File("~/Movies/I/Ivakisaka.2010.1080p.BluRay.x264-SPARKS/sparks-ivakisaka.mkv"))).isEqualToIgnoringCase("ivakisaka")
        // a file named like a release group falls back to the folder name
        assertThat(contentRenamer.extractName(File("~/Movies/I/Ivakisaka.2010.1080p.BluRay.x264-SPARKS/RARBG.txt"))).isEqualTo("Ivakisaka")
    }

    @Test
    fun `should strip remote media id from extracted name`() {
        assertThat(contentRenamer.extractName(File("~/Movies/I/Ivakisaka (2010) {tmdb-27205}.1080p.x264.mkv"))).isEqualTo("Ivakisaka")
    }

    @Test
    fun `should search movies with numbers in the title as movie in movies folder`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        createFile(base, "Movies/A/Apitig 13 (1995)/Apitig 13 (1995).mkv")
        val apollo = MediaItem("Apitig 13", "Apitig 13", 1995, MediaType.Movie, "{tmdb-568}")
        every { mediaProxyService.search("Apitig 13", 1995, MediaType.Movie) } returns listOf(apollo)

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        verify { mediaProxyService.search("Apitig 13", 1995, MediaType.Movie) }
        assertThat(File(base, "Movies/A/Apitig 13 (1995) {tmdb-568}.mkv")).exists()
    }

    @Test
    fun `should remove the folders the run emptied`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        createFile(base, "Movies/I/Ivakisaka (2010)/Ivakisaka (2010).mkv")
        File(base, "Movies/X").mkdirs()
        every { mediaProxyService.search("Ivakisaka", 2010, MediaType.Movie) } returns listOf(ivakisaka)
        val log = File(base, "rename.log")

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, log, isTestRun = false, basePath = base.absolutePath)

        assertThat(File(base, "Movies/I/Ivakisaka (2010) {tmdb-27205}.mkv")).exists()
        assertThat(File(base, "Movies/I/Ivakisaka (2010)")).doesNotExist()
        assertThat(log.readText()).contains("Removed empty folder")
        // a folder which was empty before stays
        assertThat(File(base, "Movies/X")).isDirectory()
    }

    @Test
    fun `should keep the folders in a test run`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        createFile(base, "Movies/I/Ivakisaka (2010)/Ivakisaka (2010).mkv")
        every { mediaProxyService.search("Ivakisaka", 2010, MediaType.Movie) } returns listOf(ivakisaka)

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = true, basePath = base.absolutePath)

        assertThat(File(base, "Movies/I/Ivakisaka (2010)/Ivakisaka (2010).mkv")).exists()
    }

    @Test
    fun `should use remote media id of already renamed files`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val renamed = createFile(base, "Movies/I/Ivakisaka (2010) {tmdb-27205}.1080p.x264.mkv")
        every { mediaProxyService.findByRemoteMediaId("{tmdb-27205}", any(), MediaType.Movie) } returns ivakisaka

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        verify(exactly = 0) { mediaProxyService.search(any(), any<Int>(), any()) }
        verify(exactly = 0) { mediaProxyService.search(any(), any<MediaType>()) }
        assertThat(renamed).exists()
    }

    @Test
    fun `should skip files with a remote media id without any lookup by default`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val renamed = createFile(base, "Movies/I/Ivakisaka (2010) {tmdb-27205}.1080p.x264.mkv")
        val log = File(base, "rename.log")

        contentRenamer.processFilesFromDirectory(
            File(base, "Movies").absolutePath, log, isTestRun = false, basePath = base.absolutePath
        )

        verify(exactly = 0) { mediaProxyService.findByRemoteMediaId(any(), any(), any()) }
        verify(exactly = 0) { mediaProxyService.search(any(), any<Int>(), any()) }
        assertThat(renamed).exists()
        assertThat(log.readText()).contains("skipped, already has {tmdb-27205}")
    }

    @Test
    fun `should only rename video and subtitle files of movies`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val folder = "Movies/I/Ivakisaka.2010.1080p.BluRay.x264-SPARKS"
        createFile(base, "$folder/sparks-ivakisaka.mkv")
        createFile(base, "$folder/Ivakisaka.2010.en.srt")
        val nfo = createFile(base, "$folder/RARBG.txt")
        every { mediaProxyService.search(any(), 2010, MediaType.Movie) } returns listOf(ivakisaka)

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(File(base, "Movies/I/Ivakisaka (2010) {tmdb-27205}.1080p.x264.mkv")).exists()
        assertThat(File(base, "Movies/I/Ivakisaka (2010) {tmdb-27205}.1080p.x264.en.srt")).exists()
        assertThat(nfo).exists()
    }

    @Test
    fun `should parse movie title and year from release names`() {
        val expected = mapOf(
            "127.Hceso.2010.720p.BluRay.x264" to Pair("127 Hceso", 2010),
            "300.2006.1080p.Bluray.x264" to Pair("300", 2006),
            "1408(2007)BDRip-F.HD" to Pair("1408", 2007),
            "101 Dalmatians" to Pair("101 Dalmatians", null),
            "1917.2019.2160p.UHD.BluRay.x265.10bit.HDR.DTS-HD.MA.TrueHD.7.1.Atmos-SAZOGER" to Pair("1917", 2019),
            "2001.A.Sebaz.Ocumaze.1968.REMASTERED.1080p.BluRay.x264.DTS-HD.MA.5.1-FGT" to Pair("2001 A Sebaz Ocumaze", 1968),
            "2012.2009.1080p.BluRay.x264" to Pair("2012", 2009),
            "10.Cenudocunec.Lane.2016.4K.UltraHD.BluRay.2160p.x264.DTS-HD.MA.TrueHD.7.1.Atmos.ACC-7.1-POOP" to Pair("10 Cenudocunec Lane", 2016),
            "Anetidi.BDRip1080p" to Pair("Anetidi", null),
            "Anetidi.DirCut.1984.720p.BluRay.x264" to Pair("Anetidi", 1984),
            "Anezo.Rukobak.2024.2160p.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-FLUX" to Pair("Anezo Rukobak", 2024),
            "12 Yvipu a Subud (2013) 1080p x264 ENG-ITA BluRay - 12 Asez Satipes" to Pair("12 Yvipu a Subud", 2013),
            "Gaca.Doledip.of.1935.(1935).95.min.Dudo.Pabivo.H264" to Pair("Gaca Doledip of 1935", 1935),
            "Zaliludam Point (Ateropule 1970) [HD x264 Ita Ac3 Eng-Esp-Deu Aac - Multisubs]" to Pair("Zaliludam Point", 1970),
            "Calokeki of Mor (BDrip 1080p ENG-ITA-FRA-GER-SPA - DTS) multisub x264 bluray (2006)" to Pair("Calokeki of Mor", null),
            "Wkigo Tize UNRATED (2003) [1080p]" to Pair("Wkigo Tize", 2003),
            "Agent.Rodasa.ruvuci.die.Welt.German.AC3.BDRip.XviD" to Pair("Agent Rodasa ruvuci die Welt", null),
            "13.Semester.German.2009.DVDRip.XviD" to Pair("13 Semester", 2009),
            "Cagukisuz's Wli 2006 1080p" to Pair("Cagukisuz's Wli", 2006),
            "Dasakafu Are Fogopoc 1080p DTS HighCode" to Pair("Dasakafu Are Fogopoc", null),
            "Kiru Fu Peduz 2 2011 1080p BluRay x264 Dual Audio [Hindi DD 5.1 - English DD 5.1] ESub [MW]" to Pair("Kiru Fu Peduz 2", 2011),
            "01. The BIG Boss (1971 DUAL Audio - 720p BluRay)" to Pair("The BIG Boss", 1971),
            "05a. GUPE of DAPIR (1978 - 720p BluRay)" to Pair("GUPE of DAPIR", 1978),
            "Ivakisaka (2010) {tmdb-27205}.1080p.x264" to Pair("Ivakisaka", 2010),
            "Das.Bibo.Directors.Cut.1981.1080p.x264" to Pair("Das Bibo", 1981),
            "Bozek.Hdof.Dura.-.Extended.Cut.2001.MULTi.2160p.UHD.Blu-ray" to Pair("Bozek Hdof Dura", 2001),
            "Ezonelus+2021+1080p+" to Pair("Ezonelus", 2021),
            "Asi-Asilo.emucu.Sutoc.aba-Fupu.Eroc.the.Sopi-1974--720p-EN.subs-x265-HEVC" to Pair("Asi-Asilo emucu Sutoc aba-Fupu Eroc the Sopi", 1974),
            "Samecu-Muf.2002.1080p" to Pair("Samecu-Muf", 2002),
            "Muf.Up.2015-HD" to Pair("Muf Up", 2015),
            "www.torrenting.com  - Sisob.Wnat.1983.REMASTERED.EXTRAS.REPACK.DVDRip" to Pair("Sisob Wnat", 1983),
            "Wnafaz.-.Budekiba!1990.WEB-DL.1080p" to Pair("Wnafaz - Budekiba", 1990),
            "Wvebom.Setogud.with.Extras.1972.Documentary.Vietnam.War.DVDRip.480p" to Pair("Wvebom Setogud", 1972),
            "Wdutab.Wzeki.1984.2020.HMAX.IMAX.2160p.HDR.WEB-DL" to Pair("Wdutab Wzeki 1984", 2020),
            "Bubes.3.Taralad.20041080p.BluRay.x264" to Pair("Bubes 3 Taralad", 2004),
            "3B54A22536685B87ED1697B20160C4DEBC191C3F?title=[limetorrents.cc]Buzeze.Of.The.Simek..2017..[1080p].[YTS.AG]" to Pair("Buzeze Of The Simek", 2017),
            "Eto.Safimu.Tak.1984.x264.BDRip-kinozal.tv" to Pair("Safimu Tak", 1984),
            "rep-hotelartemis.2018.1080p.bluray.x264" to Pair("hotelartemis", 2018),
        )
        expected.forEach { (rawName, titleAndYear) ->
            assertThat(contentRenamer.parseMovieName(rawName)).describedAs(rawName).isEqualTo(titleAndYear)
        }
    }

    @Test
    fun `should not take a leading year for the release year`() {
        assertThat(contentRenamer.extractYear("~/Movies/0-9/1917.2019.1080p.mkv")).isEqualTo(2019)
        assertThat(contentRenamer.extractYear("~/Movies/0-9/2001.A.Sebaz.Ocumaze.1968.1080p.mkv")).isEqualTo(1968)
        assertThat(contentRenamer.extractYear("~/Movies/0-9/2022.mp4")).isEqualTo(2022)
    }

    @Test
    fun `should name movies in a collection after their file`() {
        val collection = "~/Movies/B/BRUCE Lee (1971-1978) and BRANDON Lee (1986-1994) - The ULTIMATE Movies Collection - 720p x264/a. Bruce (1971-1978)"
        assertThat(contentRenamer.extractMovieNameAndYear(File("$collection/01. The BIG Boss (1971 DUAL Audio - 720p BluRay).mp4")))
            .isEqualTo(Pair("The BIG Boss", 1971))
        // files without a title are named after their folder
        assertThat(contentRenamer.extractMovieNameAndYear(File("~/Movies/I/Ivakisaka.2010.1080p.BluRay.x264-SPARKS/RARBG.txt")))
            .isEqualTo(Pair("Ivakisaka", 2010))
        assertThat(contentRenamer.extractMovieNameAndYear(File("~/Movies/0-9/00000.m2ts"))).isEqualTo(Pair("", null))
        // short titles are fine with a release year
        assertThat(contentRenamer.extractMovieNameAndYear(File("~/Movies/I/It (2017) [1080p] [YTS.AG].mp4"))).isEqualTo(Pair("It", 2017))
        assertThat(contentRenamer.extractMovieNameAndYear(File("~/Movies/0-9/48.Hne.1982.1080p.BluRay.x264-TENEIGHTY.mkv"))).isEqualTo(Pair("48 Hne", 1982))
        assertThat(contentRenamer.extractMovieNameAndYear(File("~/Movies/I/Idicunibi.with.the.Vosutad.1994.1080p.mp4"))).isEqualTo(Pair("Idicunibi with the Vosutad", 1994))
        assertThat(contentRenamer.extractMovieNameAndYear(File("~/Movies/H/H264.m4v"))).isEqualTo(Pair("", null))
        assertThat(contentRenamer.extractMovieNameAndYear(File("~/Movies/T/The Tanac Cesocike Migasuki Collection (1974-2017)/2017.Lakivamolem.mkv")))
            .isEqualTo(Pair("Lakivamolem", 2017))
        // extras are named after their movie
        assertThat(contentRenamer.extractMovieNameAndYear(File("~/Movies/U/Uveki Goba (2019)/Uveki Goba Trailer 2-trailer.mkv"))).isEqualTo(Pair("Uveki Goba", 2019))
        assertThat(contentRenamer.extractMovieNameAndYear(File("~/Movies/C/The.Fovufu.Civotogulo.1971.Bluray.1080p.DTS-HD.x264-Gugi/The.Fovufu.Civotogulo.Extras-Gugi/The.Pekonekuraca.Sesapan.2000.SD.NTSC.mkv")))
            .isEqualTo(Pair("The Fovufu Civotogulo", 1971))
    }

    @Test
    fun `should detect series in the movies folder`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        assertThat(contentRenamer.hasSeriesMarkers(createFile(base, "Movies/M/Mr.Inaloloce/Mr.Inaloloce.S02E01.1080p.mkv"))).isTrue()
        assertThat(contentRenamer.hasSeriesMarkers(createFile(base, "Movies/S/Suzo.Tabe.Danutikep.S01-05.ITA/Suzo.Tabe.Danutikep.5x10.La.Vita.mkv"))).isTrue()
        assertThat(contentRenamer.hasSeriesMarkers(createFile(base, "Movies/K/Kinola Supamu S01+02+OVA/Bidot Benedido Bosicacagin/Bidot Benedido Bosicacagin - 01.mkv"))).isTrue()
        (1..3).forEach { createFile(base, "Movies/L/Linabi Wmape/Tapaz.Of.Linabi.Wmape.EP$it.mkv") }
        assertThat(contentRenamer.hasSeriesMarkers(File(base, "Movies/L/Linabi Wmape/Tapaz.Of.Linabi.Wmape.EP1.mkv"))).isTrue()

        listOf("01", "02v2", "10").forEach { createFile(base, "Movies/G/[ARC] Govopatena Gofe (2003)/[ARC] Govopatena Gofe $it [BD 714p 10-bit x264 FLAC].mkv") }
        assertThat(contentRenamer.hasSeriesMarkers(File(base, "Movies/G/[ARC] Govopatena Gofe (2003)/[ARC] Govopatena Gofe 10 [BD 714p 10-bit x264 FLAC].mkv"))).isTrue()

        val gunslingerGirl10 = File(base, "Movies/G/[ARC] Govopatena Gofe (2003)/[ARC] Govopatena Gofe 10 [BD 714p 10-bit x264 FLAC].mkv")
        assertThat(contentRenamer.removeEpisodeNumber(contentRenamer.extractName(gunslingerGirl10), gunslingerGirl10)).isEqualTo("Govopatena Gofe")
        assertThat(contentRenamer.extractName(File("~/Movies/M/Mr. Buki - The Complete Collection (1990-2007)/S01 - E01 - Mr. Buki.mkv"))).isEqualTo("Mr Buki")

        // numbers in movie titles aren't episodes
        (11..13).forEach { createFile(base, "Movies/O/Opaves Tefipec/Oceans $it.mkv") }
        assertThat(contentRenamer.hasSeriesMarkers(File(base, "Movies/O/Opaves Tefipec/Opaves 11.mkv"))).isFalse()
        assertThat(contentRenamer.hasSeriesMarkers(createFile(base, "Movies/A/Apitig 13 (1995)/Apitig 13 (1995).mkv"))).isFalse()
        createFile(base, "Movies/S/Suzo.Wnat.Episode.2.Amamad.of.the.Conevi.2002.mkv")
        createFile(base, "Movies/S/Suzo.Wnat.Episode.3.Rubeled.of.the.Sofi.2005.mkv")
        assertThat(contentRenamer.hasSeriesMarkers(createFile(base, "Movies/S/Suzo.Wnat.Episode.1.The.Pavidiv.Mulato.1999.mkv"))).isFalse()
        assertThat(contentRenamer.hasSeriesMarkers(createFile(base, "Movies/O/Okub.Sekuri.2006.1080p/Okub.Sekuri.2006.1080p.mkv"))).isFalse()
        assertThat(contentRenamer.hasSeriesMarkers(createFile(base, "Movies/A/Arepe.Gocoke.Etovumu.999.(1981).(BDRip.1920x1080p.x265.FLACx2,.TrueHD.2.0x2+5.1).mkv"))).isFalse()
    }

    @Test
    fun `should search series in the movies folder as series`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        createFile(base, "Movies/M/Mr.Inaloloce/Mr.Inaloloce.S02E01.1080p.AMZN.WEB-DL.DDP5.1.H.264-KiNGS.mkv")
        val mrInbetween = MediaItem("Mr Inaloloce", "Mr Inaloloce", 2018, MediaType.Series, "{tvdb-345678}")
        every { mediaProxyService.search("Mr Inaloloce", MediaType.Series) } returns listOf(mrInbetween)

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        verify(exactly = 0) { mediaProxyService.search(any(), MediaType.Movie) }
        assertThat(File(base, "Series-Shows/M/Mr Inaloloce {tvdb-345678}/Season 2/Mr Inaloloce.S02E01.1080p.h264.mkv")).exists()
    }

    @Test
    fun `should skip disc structures, courses and files that aren't movies`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val stream = createFile(base, "Movies/M/Mecibo.2020.KOREAN.1080p.BluRay.AVC-FGT/BDMV/STREAM/00001.m2ts")
        val course = createFile(base, "Movies/C/CBT Nuggets - Amazon Web Services - AWS Foundations/01 - Getting the Most from this Series.mp4")
        val pdf = createFile(base, "Movies/I/Ivakisaka.2010.1080p/Ivakisaka.2010.pdf")

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        verify(exactly = 0) { mediaProxyService.search(any(), any<Int>(), any()) }
        verify(exactly = 0) { mediaProxyService.search(any(), any<MediaType>()) }
        assertThat(listOf(stream, course, pdf)).allMatch { it.exists() }
    }

    @Test
    fun `should skip extras folders but not packs with extras`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val featurette = createFile(base, "Movies/G/Gubu Otoce (2019) Season 1 S01 + Extras (1080p)/Featurettes/Bookshop Tour.mkv")
        val creditless = createFile(base, "Movies/K/Kinola Supamu S01+02+OVA+Extra/Bidot Benedido Bosicacagin/NC/Bidot Benedido Bosicacagin - NCOP.mkv")
        val sample = createFile(base, "Movies/I/Ivakisaka.2010.1080p/Sample/ivakisaka-sample.mkv")
        val episode = createFile(base, "Movies/G/Gubu Otoce (2019) Season 1 S01 + Extras (1080p)/Gubu Otoce (2019) - S01E01 - In the Beginning (1080p).mkv")
        every { mediaProxyService.search("Gubu Otoce", 2019, MediaType.Series) } returns listOf(MediaItem("Gubu Otoce", "Gubu Otoce", 2019, MediaType.Series, "{tvdb-359569}"))

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        verify(exactly = 0) { mediaProxyService.search(any(), any<Int>(), MediaType.Movie) }
        assertThat(listOf(featurette, creditless, sample)).allMatch { it.exists() }
        assertThat(episode).doesNotExist()
        assertThat(File(base, "Series-Shows/G/Gubu Otoce {tvdb-359569}/Season 1/Gubu Otoce.S01E01.1080p.mkv")).exists()
    }

    @Test
    fun `should not rename a movie to a namesake released years apart`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val air = createFile(base, "Movies/A/Ame.2015.1080p.BluRay.x264-[YTS.LT].mp4")
        every { mediaProxyService.search("Ame", 2015, MediaType.Movie) } returns emptyList()
        every { mediaProxyService.search("Ame", MediaType.Movie) } returns listOf(MediaItem("Ame", "Ame", 2023, MediaType.Movie, "{tmdb-964980}"))

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(air).exists()
    }

    @Test
    fun `should prefer the exact title released within a year`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        createFile(base, "Movies/F/Fomof.1995.RERIP.Remastered.Bluray.1080p.DTS-HD.x264-Gugi.mkv")
        every { mediaProxyService.search("Fomof", 1995, MediaType.Movie) } returns listOf(MediaItem("Dulogo: [1995] Live at Fomof, ND", null, 1995, MediaType.Movie, "{tmdb-1}"))
        every { mediaProxyService.search("Fomof", MediaType.Movie) } returns listOf(MediaItem("Fomof", "Fomof", 1996, MediaType.Movie, "{tmdb-275}"))

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(File(base, "Movies/F/Fomof (1996) {tmdb-275}.1080p.x264.mkv")).exists()
    }

    @Test
    fun `should skip namesakes released years apart`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        createFile(base, "Movies/M/Mitig.2009.1080p.BluRay.x264-LCHD.mkv")
        every { mediaProxyService.search("Mitig", 2009, MediaType.Movie) } returns listOf(
            MediaItem("Mitig", "Mitig", 1998, MediaType.Movie, "{tmdb-10674}"),
            MediaItem("Mitig: Rise of a Warrior", "花木兰", 2009, MediaType.Movie, "{tmdb-25707}")
        )

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(File(base, "Movies/M/Mitig Rise of a Warrior (2009) {tmdb-25707}.1080p.x264.mkv")).exists()
    }

    @Test
    fun `should not take audio channels after a resolution for an episode`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        assertThat(contentRenamer.extractEpisode(createFile(base, "Movies/A/Azilo.Kozicadu.1988.1080p.5.1.BDrip.mp4"))).isNull()
        assertThat(contentRenamer.extractEpisode(createFile(base, "Movies/A/Some.Movie.2010.x264.DD+ 7.1.mkv"))).isNull()
        assertThat(contentRenamer.hasSeriesMarkers(createFile(base, "Movies/A/Azilo.Kozicadu.1988.1080p.5.1.BDrip.mp4"))).isFalse()
    }

    @Test
    fun `should not rename a movie to a different title released two years apart`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val hbupet = createFile(base, "Movies/H/Hbupet+Qdatu+(2019)+.mkv")
        every { mediaProxyService.search("Hbupet Qdatu", 2019, MediaType.Movie) } returns emptyList()
        every { mediaProxyService.search("Hbupet Qdatu", MediaType.Movie) } returns listOf(MediaItem("Bogabo and Hbupet Qdatu", "Bogabo and Hbupet Qdatu", 2017, MediaType.Movie, "{tmdb-408648}"))

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(hbupet).exists()
    }

    @Test
    fun `should not take a title with a coincidental year for the movie`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val daca = createFile(base, "Movies/D/Daca.2000.Part.1.Bluray.1080p.DTS-HD.x264-Gugi.mkv")
        every { mediaProxyService.search("Daca", 2000, MediaType.Movie) } returns emptyList()
        every { mediaProxyService.search("Daca", MediaType.Movie) } returns emptyList()
        every { mediaProxyService.search("Daca 2000", MediaType.Movie) } returns listOf(MediaItem("Algérie 1988-2000 : Autopsie daca tragédie", null, 2005, MediaType.Movie, "{tmdb-285832}"))

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(daca).exists()
    }

    @Test
    fun `should find movies named with more than the title`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        createFile(base, "Movies/A/Azesu.Hsoc.Wpatu.Avupu.1977.DVDRip.xvid.avi")
        createFile(base, "Movies/L/Luro.aka.The.Polibokapuku.Directors.Cut.1994.BluRay.mkv")
        every { mediaProxyService.search("Azesu Hsoc", 1977, MediaType.Movie) } returns listOf(MediaItem("Azesu Hsoc", "Azesu Hsoc", 1977, MediaType.Movie, "{tmdb-703}"))
        every { mediaProxyService.search("The Polibokapuku", 1994, MediaType.Movie) } returns listOf(MediaItem("Lúro: The Polibokapuku", "Lúro", 1994, MediaType.Movie, "{tmdb-101}"))
        every { mediaProxyService.search("Luro", 1994, MediaType.Movie) } returns listOf(MediaItem("Lúro: The Polibokapuku", "Lúro", 1994, MediaType.Movie, "{tmdb-101}"))

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(File(base, "Movies/A").list()).anyMatch { it.contains("{tmdb-703}") }
        assertThat(File(base, "Movies/L").list()).anyMatch { it.contains("{tmdb-101}") }
    }

    @Test
    fun `should find movies with a typo in the title`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        createFile(base, "Movies/P/Piruzuc Of The Cuzlaazut.At.Wnicos.Ezi.2007.720p.BRRip.H264.mp4")
        every { mediaProxyService.search("Piruzuc Of The Cuzlaazut At Wnicos", 2007, MediaType.Movie) } returns listOf(
            MediaItem("Piruzuc of the Cuzalazut: At Wnico's Ezi", "Piruzuc of the Cuzalazut: At Wnico's Ezi", 2007, MediaType.Movie, "{tmdb-285}")
        )

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(File(base, "Movies/P").list()).anyMatch { it.contains("{tmdb-285}") }
    }

    @Test
    fun `should not shorten a title to a namesake`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val kozicadu = createFile(base, "Movies/A/Azilo.Kozicadu.1988.1080p.5.1.BDrip.mp4")
        every { mediaProxyService.search("Azilo", 1988, MediaType.Movie) } returns listOf(MediaItem("Azilo", "AZILO", 1988, MediaType.Movie, "{tmdb-149}"))

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(kozicadu).exists()
    }

    @Test
    fun `should prefer the movie of the year over a namesake with a hyphen`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        createFile(base, "Movies/A/Akan-Man (2018) IMAX 720p BDRip.mkv")
        every { mediaProxyService.search("Akan-Man", 2018, MediaType.Movie) } returns emptyList()
        every { mediaProxyService.search("Akan-Man", MediaType.Movie) } returns listOf(MediaItem("Akan Man", "变信人", 2020, MediaType.Movie, "{tmdb-800152}"))
        every { mediaProxyService.search("AkanMan", 2018, MediaType.Movie) } returns listOf(MediaItem("Akanman", "Akanman", 2018, MediaType.Movie, "{tmdb-297802}"))

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(File(base, "Movies/A").list()).anyMatch { it.contains("{tmdb-297802}") }
    }

    @Test
    fun `should not shorten a title inside a phrase`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val everest = createFile(base, "Movies/T/To.Live.or.Die.on.Everest.2020.DOCU.HDTV.x264.mkv")
        every { mediaProxyService.search("To Live", 2020, MediaType.Movie) } returns listOf(MediaItem("To Live", "زیستن", 2020, MediaType.Movie, "{tmdb-1411848}"))

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(everest).exists()
    }

    @Test
    fun `should not take the original title of another movie for a namesake released two years apart`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val tubidebabo = createFile(base, "Movies/T/Tubidebabo.2.1991.Jredofap.Dar.mp4")
        every { mediaProxyService.search("Tubidebabo 2", 1991, MediaType.Movie) } returns listOf(MediaItem("Sobepume Dobi", "Tubidebabo 2", 1989, MediaType.Movie, "{tmdb-1}"))

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(tubidebabo).exists()
    }

    @Test
    fun `should not take the first result for a movie without year`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val smiley = createFile(base, "Movies/S/Sebako.mp4")
        val elephant = createFile(base, "Movies/E/Ezedemid Muf.avi")
        every { mediaProxyService.search("Sebako", MediaType.Movie) } returns listOf(MediaItem("Rodiga Sebako: Fiso-iri", null, 2025, MediaType.Movie, "{tmdb-2}"))
        every { mediaProxyService.search("Ezedemid Muf", MediaType.Movie) } returns listOf(
            MediaItem("Ezedemid Muf", "Ezedemid Muf", null, MediaType.Movie, "{tmdb-1520516}"),
            MediaItem("The Ezedemid Muf", "The Ezedemid Muf", 1980, MediaType.Movie, "{tmdb-1955}")
        )

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(smiley).exists()
        assertThat(elephant).doesNotExist()
        assertThat(File(base, "Movies/E").list()).anyMatch { it.contains("{tmdb-1955}") }
    }

    @Test
    fun `should find movies with misspelled or differently written names`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        createFile(base, "Movies/G/Gaca.Fidatu.1964.1080p.Bluray.x264.mkv")
        createFile(base, "Movies/S/Segucab.Kasis.IT.1990.DVDRip.mp4")
        createFile(base, "Movies/R/Rabu.to.Pzeosufer.(2002).[1080p].mp4")
        every { mediaProxyService.search("Gacafidatu", 1964, MediaType.Movie) } returns listOf(MediaItem("Gacafidatu", "Gacafidatu", 1964, MediaType.Movie, "{tmdb-658}"))
        every { mediaProxyService.search("Segucab Kasi's IT", 1990, MediaType.Movie) } returns listOf(MediaItem("Segucab Kasi's It", "Segucab Kasi's It", 1990, MediaType.Movie, "{tmdb-1}"))
        every { mediaProxyService.search("Rabu to", 2002, MediaType.Movie) } returns listOf(MediaItem("Rabu to Pezosufer", "Rabu to Pezosufer", 2002, MediaType.Movie, "{tmdb-4147}"))

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(File(base, "Movies/G").list()).anyMatch { it.contains("{tmdb-658}") }
        assertThat(File(base, "Movies/S").list()).anyMatch { it.contains("{tmdb-1}") }
        assertThat(File(base, "Movies/R").list()).anyMatch { it.contains("{tmdb-4147}") }
    }

    @Test
    fun `should find movies without year behind noise and with superscript titles`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        createFile(base, "Movies/T/The.Hkotizim.Part.I.1080p.mkv")
        createFile(base, "Movies/L/Lureril From Ica Jbug KLAXXON.avi")
        createFile(base, "Movies/A/The.Accountant.2.1080p.WebRip.mkv")
        every { mediaProxyService.search("The Hkotizim", MediaType.Movie) } returns listOf(MediaItem("The Hkotizim", "The Hkotizim", 2009, MediaType.Movie, "{tmdb-18785}"))
        every { mediaProxyService.search("Lureril From Ica Jbug", MediaType.Movie) } returns listOf(MediaItem("Lureril from Ica Jbug", "硫黄島からの手紙", 2006, MediaType.Movie, "{tmdb-1251}"))
        every { mediaProxyService.search("The Accountant 2", MediaType.Movie) } returns listOf(MediaItem("The Accountant²", "The Accountant²", 2025, MediaType.Movie, "{tmdb-870028}"))

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(File(base, "Movies/H").list()).anyMatch { it.contains("{tmdb-18785}") }
        assertThat(File(base, "Movies/L").list()).anyMatch { it.contains("{tmdb-1251}") }
        assertThat(File(base, "Movies/A").list()).anyMatch { it.contains("{tmdb-870028}") }
    }

    @Test
    fun `should skip Plex extras behind a dash and decode html entities`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val audition = createFile(base, "Movies/U/Uveki.Goba/Apubef Logusu Abaconep Tico-other.mkv")
        assertThat(audition).exists()
        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)
        assertThat(audition).exists()
        assertThat(contentRenamer.parseMovieName(contentRenamer.decodeIfEncoded("Bove.Saleme&#039;s.Deseguz.(1992).mkv"))).isEqualTo(Pair("Bove Saleme's Deseguz", 1992))
        assertThat(contentRenamer.parseMovieName("Raticid.For.A.Diban.HDMux.1080p.x264")).isEqualTo(Pair("Raticid For A Diban", null))
    }

    @Test
    fun `should match a misspelled title with a roman numeral for the arabic one`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        createFile(base, "Movies/S/Suzo.Wnat.Episode.1.The.Favidi.Mulato.1999.4K.UltraHD.BluRay.2160p.x264.mkv")
        every { mediaProxyService.search("Suzo Wnat Episode", 1999, MediaType.Movie) } returns
                listOf(MediaItem("Suzo Wnat: Episode I - The Pavidiv Mulato", "Suzo Wnat: Episode I - The Pavidiv Mulato", 1999, MediaType.Movie, "{tmdb-1893}"))

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(File(base, "Movies/S").list()).anyMatch { it.contains("{tmdb-1893}") }
    }
}
