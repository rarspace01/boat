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

/**
 * Files which overwrote each other in a live run: extras numbered like episodes, date named episodes, parts of a mini
 * series, absolute episode numbers, subtitles of several languages and releases of the same episode.
 */
class ContentRenamerCollisionTest {

    private val mediaProxyService: MediaProxyService = mockk(relaxed = true)
    private val contentRenamer = ContentRenamer(mediaProxyService)

    private val shows = listOf(
        MediaItem("Turo Pimes", year = 1990, type = MediaType.Series, id = "{tvdb-70533}"),
        MediaItem("Dr. Pabe", year = 2002, type = MediaType.Series, id = "{tvdb-71424}"),
        MediaItem("The Hlucovidic's Guzuf to the Gocoke", year = 1981, type = MediaType.Series, id = "{tvdb-78142}"),
        MediaItem("Tomeb Misovikuc 8.0", year = 2009, type = MediaType.Series, id = "{tmdb-37866}"),
        MediaItem("Demadi Bemu", year = 1986, type = MediaType.Series, id = "{tvdb-76666}"),
        MediaItem("Pumek and Pipirebak", year = 1995, type = MediaType.Series, id = "{tvdb-71691}"),
        MediaItem("Kug & Pepov", year = 2012, type = MediaType.Series, id = "{tvdb-255325}"),
        MediaItem("The Gecodu Alo", year = 2022, type = MediaType.Series, id = "{tvdb-364412}"),
        MediaItem("bozek-iri", year = 2014, type = MediaType.Series, id = "{tmdb-61381}"),
        MediaItem("No Gupe No Lova", year = 2014, type = MediaType.Series, id = "{tvdb-278155}"),
        MediaItem("Kad Pelafedo", year = 2002, type = MediaType.Series, id = "{tvdb-78259}"),
        MediaItem("Lecu", year = 2004, type = MediaType.Series, id = "{tvdb-73739}"),
    )

    private fun normalize(title: String) = title.lowercase().replace("&", "and").replace(Regex("[^a-z0-9]"), "")

    private fun lookup(name: String?): List<MediaItem> {
        val normalizedName = normalize(name ?: return emptyList())
        return shows.filter { show -> normalize(show.title).let { normalizedName.startsWith(it) || it.startsWith(normalizedName) } }
    }

    @Test
    fun liveRunDoesNotOverwriteAnything(@TempDir tempDir: File) {
        val incoming = File(tempDir, "incoming/Series-Shows")
        val library = File(tempDir, "PFDB")
        fun file(path: String) = File(incoming, path).apply { parentFile.mkdirs(); writeText(path) }

        // an episode already in the library
        val libraryEpisode = File(library, "Series-Shows/T/Turo Pimes {tvdb-70533}/Season 2/Turo Pimes.S02E01.mkv").apply {
            parentFile.mkdirs(); writeText("library episode")
        }
        file("T/Turo.Pimes.(1990)/Season 2/Turo.Pimes.S02E01.mkv")
        file("T/Turo.Pimes.(1990)/Featurettes/Season 2/The Lif Luza/The Lif Luza 01.mkv")
        file("T/Turo.Pimes.(1990)/Featurettes/Season 2/The Lif Luza/The Lif Luza 02.mkv")
        file("D/Dr.Pabe.Show/Dr.Pabe.Show.2013.10.01.Didudugaf.and.Vubiliz.Lema.HDTV.x264-OWN.mp4")
        file("D/Dr.Pabe.Show/Dr.Pabe.Show.2013.10.02.Death.in.6.Seconds.HDTV.x264-OWN.mp4")
        val hlucovidics = "H/The Hlucovidics Guzuf To The Gocoke - Remastered Mini Series 1981 1080p [H264-mp4]/The Hlucovidics Guzuf To The Gocoke - Remastered Mini Series 1981 1080p"
        file("$hlucovidics/01 The Hlucovidics Guzuf To The Gocoke - Part 1 Of 6 Mini-Series 1981 1080p [H264-mp4].mp4")
        file("$hlucovidics/02 The Hlucovidics Guzuf To The Gocoke - Part 2 Of 6 Mini-Series 1981 1080p [H264-mp4].mp4")
        listOf("01", "08", "11").forEach {
            file("T/Tomeb_Misovikuc_8.0_01-11_(x264,720p,ger.sub)/[subs4u+Lightmaker]_Tomeb_Misovikuc_8.0_${it}_(x264,720p,ger.sub)[CF836FCE].mkv")
        }
        file("D/Demadi Bemu {tvdb-76666}/Season 1/demadi bemu.S01E01.mkv")
        file("D/Demadi Bemu {tvdb-76666}/Season 1/demadi bemu.S01E101.mkv")
        val pride = "P/Pumek and Pipirebak (BBC 1995) Jane Austen 1080p H.264 10GB (moviesbyrizzo) subs /Subtitles folder"
        file("$pride/Danish/Pumek and Pipirebak (BBC -1995) E1 1080p H.264 (moviesbyrizzo).srt")
        file("$pride/English/Pumek and Pipirebak (BBC -1995) E1 1080p H.264 (moviesbyrizzo).srt")
        val keyAndPeele = "K/Kug.And.Pepov/Kug.And.Pepov.S01.COMPLETE.BluRay.x264.AAC.2.0-LuCakoga [UNCENSORED]/subtitles"
        file("$keyAndPeele/english/Kug.And.Pepov.S01E01.Series.Premiere.BluRay.x264.AAC.2.0-LuCakoga.srt")
        file("$keyAndPeele/english_SDH/Kug.And.Pepov.S01E01.Series.Premiere.BluRay.x264.AAC.2.0-LuCakoga.srt")
        file("G/The.Gecodu.Alo.S01/Subs/The.Gecodu.Alo.S01E01.WEBRip.x264-ION10[eztv.re]/2_English.srt")
        file("G/The.Gecodu.Alo.S01/Subs/The.Gecodu.Alo.S01E01.WEBRip.x264-ION10[eztv.re]/3_Spanish.srt")
        file("B/Bozekiri/Bozekiri.S04E06.HDTV.x264-SVA[ettv].mkv")
        file("B/Bozekiri/Bozekiri.S04E06.HDTV.x264-SVA[eztv].mkv")
        file("N/No_Gupe_No_Lova_English_Dub_720p_1-12_Complete/No Gupe No Lova 10 Blue Rose.mp4")
        file("N/No_Gupe_No_Lova_English_Dub_720p_1-12_Complete/No Gupe No Lova 12 Rule Number 10.mp4")
        // segments and specials of an episode
        file("K/Kad Pelafedo/Season 2/Kad Pelafedo.S02E06.1080p.mp4")
        file("K/Kad Pelafedo/Season 2/Kad Pelafedo - S02 E06b - Adventures In Rufus-Sitting (1080p - AMZN Web-DL).mp4")
        file("L/Lecu/Season 1/Lecu.S01E20.mp4")
        file("L/Lecu/Season 1/Lecu S01E20X01 Lecu The Jvunono.mp4")
        file("L/Lecu/Season 4/Lecu.S04E13.mkv")
        file("L/Lecu/Season 4/Lecu.S04E13E14 'There's No Pecer Lugu Hzul - Part 2'.mkv")

        every { mediaProxyService.search(any(), any<MediaType>()) } answers { lookup(firstArg()) }
        every { mediaProxyService.search(any(), any<Int>(), any<MediaType>()) } answers { lookup(firstArg()) }
        every { mediaProxyService.findByRemoteMediaId(any(), any(), any()) } answers { shows.firstOrNull { it.id == firstArg() } }

        fun contents() = tempDir.walk().filter { it.isFile && it.extension != "log" }.map { it.readText() }.sorted().toList()
        val contentsBefore = contents()

        contentRenamer.processFilesFromDirectory(incoming.absolutePath, isTestRun = false, basePath = library.absolutePath, skipFilesWithRemoteId = false)

        // nothing got lost, every file is still there once, moved or not
        assertThat(contents()).isEqualTo(contentsBefore)
        assertThat(libraryEpisode.readText()).isEqualTo("library episode")

        // the library's folder names depend on the show's safe title and letter, the checks only look at their id
        val moved = library.walk().filter { it.isFile }.map { it.relativeTo(library).path }.toList()
        fun movedFiles(vararg parts: String) = moved.filter { path -> parts.all { path.contains(it) } }
        assertThat(movedFiles("{tvdb-70533}/Featurettes/Season 2 - The Lif Luza - The Lif Luza 01.mkv")).hasSize(1)
        assertThat(movedFiles("{tvdb-70533}/Featurettes/Season 2 - The Lif Luza - The Lif Luza 02.mkv")).hasSize(1)
        assertThat(movedFiles("{tvdb-71424}/Dr.Pabe.Show.2013.10.01.Didudugaf.and.Vubiliz.Lema.HDTV.x264-OWN.mp4")).hasSize(1)
        assertThat(movedFiles("{tvdb-71424}/Dr.Pabe.Show.2013.10.02.Death.in.6.Seconds.HDTV.x264-OWN.mp4")).hasSize(1)
        assertThat(movedFiles("{tvdb-78142}/Season 1/", ".S01E01.")).hasSize(1)
        assertThat(movedFiles("{tvdb-78142}/Season 1/", ".S01E02.")).hasSize(1)
        listOf("01", "08", "11").forEach { assertThat(movedFiles("{tmdb-37866}/Season 1/", ".S01E$it.")).hasSize(1) }
        assertThat(movedFiles("{tvdb-76666}/Season 1/Demadi Bemu.S01E01.mkv")).hasSize(1)
        assertThat(movedFiles("{tvdb-76666}/Season 1/Demadi Bemu.S01E101.mkv")).hasSize(1)
        assertThat(movedFiles("{tvdb-71691}/Season 1/", ".S01E01.", ".da.srt")).hasSize(1)
        assertThat(movedFiles("{tvdb-71691}/Season 1/", ".S01E01.", ".en.srt")).hasSize(1)
        assertThat(movedFiles("{tvdb-255325}/Season 1/", ".S01E01.", ".en.srt")).hasSize(1)
        assertThat(movedFiles("{tvdb-255325}/Season 1/", ".S01E01.", ".en.sdh.srt")).hasSize(1)
        assertThat(movedFiles("{tvdb-364412}/Season 1/", ".S01E01.", ".en.srt")).hasSize(1)
        assertThat(movedFiles("{tvdb-364412}/Season 1/", ".S01E01.", ".es.srt")).hasSize(1)
        assertThat(movedFiles("{tvdb-278155}/Season 1/", ".S01E10.")).hasSize(1)
        assertThat(movedFiles("{tvdb-278155}/Season 1/", ".S01E12.")).hasSize(1)
        assertThat(movedFiles("{tvdb-78259}/Season 2/Kad Pelafedo.S02E06.1080p.mp4")).hasSize(1)
        assertThat(movedFiles("{tvdb-78259}/Season 2/Kad Pelafedo.S02E06b.1080p.mp4")).hasSize(1)
        assertThat(movedFiles("{tvdb-73739}/Season 1/Lecu.S01E20.mp4")).hasSize(1)
        assertThat(movedFiles("{tvdb-73739}/Season 1/Lecu S01E20X01 Lecu The Jvunono.mp4")).hasSize(1)
        assertThat(movedFiles("{tvdb-73739}/Season 4/Lecu.S04E13.mkv")).hasSize(1)
        assertThat(movedFiles("{tvdb-73739}/Season 4/Lecu.S04E13-E14.mkv")).hasSize(1)
        // one release of the same episode is moved, the other one stays
        assertThat(movedFiles("{tmdb-61381}/Season 4/", ".S04E06.")).hasSize(1)
    }

    @Test
    fun repeatedRunsKeepTheNames(@TempDir tempDir: File) {
        // incoming files next to the library, a run visits the files it moved again
        val library = File(tempDir, "PFDB")
        val incoming = File(library, "Series-Shows/H/HAK 5 all Seasons (Youtube Channel Clone) 1080p").apply { mkdirs() }
        File(incoming, "hak5--1718--free-virtualization-clusters-and-breadboards--hd720p30.h264.mp4").writeText("episode")
        val hak5 = MediaItem("Hak5", year = 2005, type = MediaType.Series, id = "{tvdb-79008}")
        every { mediaProxyService.search(any(), any<MediaType>()) } returns listOf(hak5)
        every { mediaProxyService.search(any(), any<Int>(), any<MediaType>()) } returns listOf(hak5)
        every { mediaProxyService.findByRemoteMediaId(any(), any(), any()) } returns hak5

        fun files() = library.walk().filter { it.isFile }.map { it.relativeTo(library).path }.sorted().toList()
        repeat(2) { contentRenamer.processFilesFromDirectory(File(library, "Series-Shows").absolutePath, isTestRun = false, basePath = library.absolutePath, skipFilesWithRemoteId = false) }
        val afterTwoRuns = files()
        contentRenamer.processFilesFromDirectory(File(library, "Series-Shows").absolutePath, isTestRun = false, basePath = library.absolutePath, skipFilesWithRemoteId = false)

        // "hak5" counts as junk, the show's title is used instead of an empty one which made it a hidden file
        assertThat(afterTwoRuns).containsExactly("Series-Shows/H/Hak5 {tvdb-79008}/Season 17/Hak5.S17E18.720p.h264.mp4")
        assertThat(files()).isEqualTo(afterTwoRuns)
    }

    @Test
    fun testRunReportsCollisions(@TempDir tempDir: File) {
        val incoming = File(tempDir, "incoming/Series-Shows/B/Bozekiri").apply { mkdirs() }
        File(incoming, "Bozekiri.S04E06.HDTV.x264-SVA[ettv].mkv").writeText("ettv")
        File(incoming, "Bozekiri.S04E06.HDTV.x264-SVA[eztv].mkv").writeText("eztv")
        File(incoming, "_____padding_file_0_if you see this file, please update to BitComet 0.85 or above____").writeText("")
        File(incoming, "Bozekiri.S04E07.HDTV.x264-SVA[eztv].mkv.!qb").writeText("download in progress")
        every { mediaProxyService.search(any(), any<MediaType>()) } answers { lookup(firstArg()) }
        every { mediaProxyService.search(any(), any<Int>(), any<MediaType>()) } answers { lookup(firstArg()) }

        val log = File(tempDir, "extraction.log")
        contentRenamer.processFilesFromDirectory(incoming.parentFile.parentFile.absolutePath, log, isTestRun = true, basePath = File(tempDir, "PFDB").absolutePath)

        assertThat(log.readText()).contains("Not moved, target exists:").contains("skipped, padding file").contains("skipped, no media file")
        assertThat(incoming.list()).hasSize(4)
    }
}
