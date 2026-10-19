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
        MediaItem("Twin Peaks", year = 1990, type = MediaType.Series, id = "{tvdb-70533}"),
        MediaItem("Dr. Phil", year = 2002, type = MediaType.Series, id = "{tvdb-71424}"),
        MediaItem("The Hitchhiker's Guide to the Galaxy", year = 1981, type = MediaType.Series, id = "{tvdb-78142}"),
        MediaItem("Tokyo Magnitude 8.0", year = 2009, type = MediaType.Series, id = "{tmdb-37866}"),
        MediaItem("Dragon Ball", year = 1986, type = MediaType.Series, id = "{tvdb-76666}"),
        MediaItem("Pride and Prejudice", year = 1995, type = MediaType.Series, id = "{tvdb-71691}"),
        MediaItem("Key & Peele", year = 2012, type = MediaType.Series, id = "{tvdb-255325}"),
        MediaItem("The Gilded Age", year = 2022, type = MediaType.Series, id = "{tvdb-364412}"),
        MediaItem("black-ish", year = 2014, type = MediaType.Series, id = "{tmdb-61381}"),
        MediaItem("No Game No Life", year = 2014, type = MediaType.Series, id = "{tvdb-278155}"),
        MediaItem("Kim Possible", year = 2002, type = MediaType.Series, id = "{tvdb-78259}"),
        MediaItem("Lost", year = 2004, type = MediaType.Series, id = "{tvdb-73739}"),
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
        val libraryEpisode = File(library, "Series-Shows/T/Twin Peaks {tvdb-70533}/Season 2/Twin Peaks.S02E01.mkv").apply {
            parentFile.mkdirs(); writeText("library episode")
        }
        file("T/Twin.Peaks.(1990)/Season 2/Twin.Peaks.S02E01.mkv")
        file("T/Twin.Peaks.(1990)/Featurettes/Season 2/The Log Lady/The Log Lady 01.mkv")
        file("T/Twin.Peaks.(1990)/Featurettes/Season 2/The Log Lady/The Log Lady 02.mkv")
        file("D/Dr.Phil.Show/Dr.Phil.Show.2013.10.01.Dangerous.and.Violent.Love.HDTV.x264-OWN.mp4")
        file("D/Dr.Phil.Show/Dr.Phil.Show.2013.10.02.Death.in.6.Seconds.HDTV.x264-OWN.mp4")
        val hitchhikers = "H/The Hitchhikers Guide To The Galaxy - Remastered Mini Series 1981 1080p [H264-mp4]/The Hitchhikers Guide To The Galaxy - Remastered Mini Series 1981 1080p"
        file("$hitchhikers/01 The Hitchhikers Guide To The Galaxy - Part 1 Of 6 Mini-Series 1981 1080p [H264-mp4].mp4")
        file("$hitchhikers/02 The Hitchhikers Guide To The Galaxy - Part 2 Of 6 Mini-Series 1981 1080p [H264-mp4].mp4")
        listOf("01", "08", "11").forEach {
            file("T/Tokyo_Magnitude_8.0_01-11_(x264,720p,ger.sub)/[subs4u+Lightmaker]_Tokyo_Magnitude_8.0_${it}_(x264,720p,ger.sub)[CF836FCE].mkv")
        }
        file("D/Dragon Ball {tvdb-76666}/Season 1/dragon ball.S01E01.mkv")
        file("D/Dragon Ball {tvdb-76666}/Season 1/dragon ball.S01E101.mkv")
        val pride = "P/Pride and Prejudice (BBC 1995) Jane Austen 1080p H.264 10GB (moviesbyrizzo) subs /Subtitles folder"
        file("$pride/Danish/Pride and Prejudice (BBC -1995) E1 1080p H.264 (moviesbyrizzo).srt")
        file("$pride/English/Pride and Prejudice (BBC -1995) E1 1080p H.264 (moviesbyrizzo).srt")
        val keyAndPeele = "K/Key.And.Peele/Key.And.Peele.S01.COMPLETE.BluRay.x264.AAC.2.0-LeRalouf [UNCENSORED]/subtitles"
        file("$keyAndPeele/english/Key.And.Peele.S01E01.Series.Premiere.BluRay.x264.AAC.2.0-LeRalouf.srt")
        file("$keyAndPeele/english_SDH/Key.And.Peele.S01E01.Series.Premiere.BluRay.x264.AAC.2.0-LeRalouf.srt")
        file("G/The.Gilded.Age.S01/Subs/The.Gilded.Age.S01E01.WEBRip.x264-ION10[eztv.re]/2_English.srt")
        file("G/The.Gilded.Age.S01/Subs/The.Gilded.Age.S01E01.WEBRip.x264-ION10[eztv.re]/3_Spanish.srt")
        file("B/Blackish/Blackish.S04E06.HDTV.x264-SVA[ettv].mkv")
        file("B/Blackish/Blackish.S04E06.HDTV.x264-SVA[eztv].mkv")
        file("N/No_Game_No_Life_English_Dub_720p_1-12_Complete/No Game No Life 10 Blue Rose.mp4")
        file("N/No_Game_No_Life_English_Dub_720p_1-12_Complete/No Game No Life 12 Rule Number 10.mp4")
        // segments and specials of an episode
        file("K/Kim Possible/Season 2/Kim Possible.S02E06.1080p.mp4")
        file("K/Kim Possible/Season 2/Kim Possible - S02 E06b - Adventures In Rufus-Sitting (1080p - AMZN Web-DL).mp4")
        file("L/Lost/Season 1/Lost.S01E20.mp4")
        file("L/Lost/Season 1/Lost S01E20X01 Lost The Journey.mp4")
        file("L/Lost/Season 4/Lost.S04E13.mkv")
        file("L/Lost/Season 4/Lost.S04E13E14 'There's No Place Like Home - Part 2'.mkv")

        every { mediaProxyService.search(any(), any<MediaType>()) } answers { lookup(firstArg()) }
        every { mediaProxyService.search(any(), any<Int>(), any<MediaType>()) } answers { lookup(firstArg()) }
        every { mediaProxyService.findByRemoteMediaId(any(), any(), any()) } answers { shows.firstOrNull { it.id == firstArg() } }

        fun contents() = tempDir.walk().filter { it.isFile && it.extension != "log" }.map { it.readText() }.sorted().toList()
        val contentsBefore = contents()

        contentRenamer.processFilesFromDirectory(incoming.absolutePath, isTestRun = false, basePath = library.absolutePath)

        // nothing got lost, every file is still there once, moved or not
        assertThat(contents()).isEqualTo(contentsBefore)
        assertThat(libraryEpisode.readText()).isEqualTo("library episode")

        // the library's folder names depend on the show's safe title and letter, the checks only look at their id
        val moved = library.walk().filter { it.isFile }.map { it.relativeTo(library).path }.toList()
        fun movedFiles(vararg parts: String) = moved.filter { path -> parts.all { path.contains(it) } }
        assertThat(movedFiles("{tvdb-70533}/Featurettes/Season 2 - The Log Lady - The Log Lady 01.mkv")).hasSize(1)
        assertThat(movedFiles("{tvdb-70533}/Featurettes/Season 2 - The Log Lady - The Log Lady 02.mkv")).hasSize(1)
        assertThat(movedFiles("{tvdb-71424}/Dr.Phil.Show.2013.10.01.Dangerous.and.Violent.Love.HDTV.x264-OWN.mp4")).hasSize(1)
        assertThat(movedFiles("{tvdb-71424}/Dr.Phil.Show.2013.10.02.Death.in.6.Seconds.HDTV.x264-OWN.mp4")).hasSize(1)
        assertThat(movedFiles("{tvdb-78142}/Season 1/", ".S01E01.")).hasSize(1)
        assertThat(movedFiles("{tvdb-78142}/Season 1/", ".S01E02.")).hasSize(1)
        listOf("01", "08", "11").forEach { assertThat(movedFiles("{tmdb-37866}/Season 1/", ".S01E$it.")).hasSize(1) }
        assertThat(movedFiles("{tvdb-76666}/Season 1/Dragon Ball.S01E01.mkv")).hasSize(1)
        assertThat(movedFiles("{tvdb-76666}/Season 1/Dragon Ball.S01E101.mkv")).hasSize(1)
        assertThat(movedFiles("{tvdb-71691}/Season 1/", ".S01E01.", ".da.srt")).hasSize(1)
        assertThat(movedFiles("{tvdb-71691}/Season 1/", ".S01E01.", ".en.srt")).hasSize(1)
        assertThat(movedFiles("{tvdb-255325}/Season 1/", ".S01E01.", ".en.srt")).hasSize(1)
        assertThat(movedFiles("{tvdb-255325}/Season 1/", ".S01E01.", ".en.sdh.srt")).hasSize(1)
        assertThat(movedFiles("{tvdb-364412}/Season 1/", ".S01E01.", ".en.srt")).hasSize(1)
        assertThat(movedFiles("{tvdb-364412}/Season 1/", ".S01E01.", ".es.srt")).hasSize(1)
        assertThat(movedFiles("{tvdb-278155}/Season 1/", ".S01E10.")).hasSize(1)
        assertThat(movedFiles("{tvdb-278155}/Season 1/", ".S01E12.")).hasSize(1)
        assertThat(movedFiles("{tvdb-78259}/Season 2/Kim Possible.S02E06.1080p.mp4")).hasSize(1)
        assertThat(movedFiles("{tvdb-78259}/Season 2/Kim Possible.S02E06b.1080p.mp4")).hasSize(1)
        assertThat(movedFiles("{tvdb-73739}/Season 1/Lost.S01E20.mp4")).hasSize(1)
        assertThat(movedFiles("{tvdb-73739}/Season 1/Lost S01E20X01 Lost The Journey.mp4")).hasSize(1)
        assertThat(movedFiles("{tvdb-73739}/Season 4/Lost.S04E13.mkv")).hasSize(1)
        assertThat(movedFiles("{tvdb-73739}/Season 4/Lost.S04E13-E14.mkv")).hasSize(1)
        // one release of the same episode is moved, the other one stays
        assertThat(movedFiles("{tmdb-61381}/Season 4/", ".S04E06.")).hasSize(1)
    }

    @Test
    fun testRunReportsCollisions(@TempDir tempDir: File) {
        val incoming = File(tempDir, "incoming/Series-Shows/B/Blackish").apply { mkdirs() }
        File(incoming, "Blackish.S04E06.HDTV.x264-SVA[ettv].mkv").writeText("ettv")
        File(incoming, "Blackish.S04E06.HDTV.x264-SVA[eztv].mkv").writeText("eztv")
        File(incoming, "_____padding_file_0_if you see this file, please update to BitComet 0.85 or above____").writeText("")
        File(incoming, "Blackish.S04E07.HDTV.x264-SVA[eztv].mkv.!qb").writeText("download in progress")
        every { mediaProxyService.search(any(), any<MediaType>()) } answers { lookup(firstArg()) }
        every { mediaProxyService.search(any(), any<Int>(), any<MediaType>()) } answers { lookup(firstArg()) }

        val log = File(tempDir, "extraction.log")
        contentRenamer.processFilesFromDirectory(incoming.parentFile.parentFile.absolutePath, log, isTestRun = true, basePath = File(tempDir, "PFDB").absolutePath)

        assertThat(log.readText()).contains("Not moved, target exists:").contains("skipped, padding file").contains("skipped, no media file")
        assertThat(incoming.list()).hasSize(4)
    }
}
