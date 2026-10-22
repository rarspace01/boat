package boat.rename

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

internal class DuplicateCleanerTest {

    private val cleaner = DuplicateCleaner()

    private fun file(base: File, relativePath: String, bytes: Int = 100): File =
        File(base, relativePath).apply {
            parentFile.mkdirs()
            writeBytes(ByteArray(bytes) { 'x'.code.toByte() })
        }

    private fun run(base: File, isTestRun: Boolean = false, logFile: File? = null) =
        cleaner.processFilesFromDirectory(base.absolutePath, logFile, isTestRun)

    // ---- movies

    @Test
    fun `should keep the version with the higher resolution`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val hd = file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.mkv", 100)
        val sd = file(base, "Movies/I/Inception (2010) {tmdb-27205}.720p.x264.mkv", 900)

        val groups = run(base)

        assertThat(groups).hasSize(1)
        assertThat(groups.single().keep.file).isEqualTo(hd)
        assertThat(hd).exists()
        assertThat(sd).doesNotExist()
    }

    @Test
    fun `should prefer the better codec at the same resolution and then the bigger file`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val x265 = file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x265.mkv", 100)
        val x264 = file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.mkv", 900)
        val xvid = file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.xvid.avi", 5000)
        val dune1 = file(base, "Movies/D/Dune (2021) {tmdb-438631}.1080p.x264.mkv", 100)
        val dune2 = file(base, "Movies/D/Dune (2021) {tmdb-438631}.1080p.x264.mp4", 300)

        run(base)

        assertThat(x265).exists()
        assertThat(x264).doesNotExist()
        assertThat(xvid).doesNotExist()
        assertThat(dune2).exists()
        assertThat(dune1).doesNotExist()
    }

    @Test
    fun `should keep exactly one of identical versions`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val first = file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.mkv", 100)
        val second = file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.mp4", 100)

        run(base)

        assertThat(listOf(first, second).count { it.exists() }).isEqualTo(1)
    }

    @Test
    fun `should not touch other movies parts extras files without id and files which are no video`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val files = listOf(
            file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.mkv"),
            file(base, "Movies/D/Dune (2021) {tmdb-438631}.720p.x264.mkv"),
            // two parts of one movie are two files
            file(base, "Movies/K/Kill Bill (2003) {tmdb-24}.1080p.x264 - pt1.mkv"),
            file(base, "Movies/K/Kill Bill (2003) {tmdb-24}.720p.x264 - pt2.mkv"),
            // not renamed, no id
            file(base, "Movies/M/Matrix.1999.1080p.mkv"),
            file(base, "Movies/M/Matrix.1999.720p.mkv"),
            // extras and samples keep their names
            file(base, "Movies/I/Inception (2010) {tmdb-27205}.sample.mkv"),
            // no video
            file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.nfo"),
            file(base, "Movies/I/Inception (2010) {tmdb-27205}.720p.x264.srt"),
        )

        val groups = run(base)

        assertThat(groups).isEmpty()
        assertThat(files).allMatch { it.exists() }
    }

    @Test
    fun `should find duplicates in different folders of the movies`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val a = file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.mkv")
        val b = file(base, "Movies/Old/Inception (2010) {tmdb-27205}.480p.xvid.avi")

        run(base)

        assertThat(a).exists()
        assertThat(b).doesNotExist()
    }

    // ---- series

    @Test
    fun `should keep the best version of every episode`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val show = "Series-Shows/D/Dark {tvdb-334824}/Season 1"
        val e1hd = file(base, "$show/Dark.S01E01.1080p.x265.mkv", 100)
        val e1sd = file(base, "$show/Dark.S01E01.720p.x264.mkv", 900)
        val e2 = file(base, "$show/Dark.S01E02.720p.x264.mkv", 100)
        val e3a = file(base, "$show/Dark.S01E03.1080p.x264.mkv", 100)
        val e3b = file(base, "$show/Dark.S01E03.1080p.x264.mp4", 500)

        val groups = run(base)

        assertThat(groups.map { it.key }).containsExactly("series {tvdb-334824} S01E01", "series {tvdb-334824} S01E03")
        assertThat(e1hd).exists()
        assertThat(e1sd).doesNotExist()
        assertThat(e2).exists()
        assertThat(e3b).exists()
        assertThat(e3a).doesNotExist()
    }

    @Test
    fun `should not mix up shows seasons double episodes and parts`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val files = listOf(
            file(base, "Series-Shows/D/Dark {tvdb-334824}/Season 1/Dark.S01E01.1080p.x264.mkv"),
            file(base, "Series-Shows/D/Dark {tvdb-334824}/Season 2/Dark.S02E01.720p.x264.mkv"),
            file(base, "Series-Shows/O/Other {tvdb-1}/Season 1/Other.S01E01.720p.x264.mkv"),
            file(base, "Series-Shows/L/Lost {tvdb-73739}/Season 4/Lost.S04E13.1080p.mkv"),
            file(base, "Series-Shows/L/Lost {tvdb-73739}/Season 4/Lost.S04E13-E14.720p.mkv"),
            file(base, "Series-Shows/K/Kim {tvdb-78259}/Season 2/Kim.S02E06.1080p.mp4"),
            file(base, "Series-Shows/K/Kim {tvdb-78259}/Season 2/Kim.S02E06b.720p.mp4"),
            // extras and files without a show id
            file(base, "Series-Shows/D/Dark {tvdb-334824}/Featurettes/Making of.1080p.mkv"),
            file(base, "Series-Shows/N/NoId/Season 1/NoId.S01E01.1080p.mkv"),
            file(base, "Series-Shows/N/NoId/Season 1/NoId.S01E01.720p.mkv"),
        )

        assertThat(run(base)).isEmpty()
        assertThat(files).allMatch { it.exists() }
    }

    // ---- test run, log and subtitles

    @Test
    fun `a test run only writes to the log`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val hd = file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.mkv")
        val sd = file(base, "Movies/I/Inception (2010) {tmdb-27205}.720p.x264.mkv")
        val subtitle = file(base, "Movies/I/Inception (2010) {tmdb-27205}.720p.x264.en.srt")
        val log = File(base, "duplicates.log")

        val groups = run(base, isTestRun = true, logFile = log)

        assertThat(groups).hasSize(1)
        assertThat(listOf(hd, sd, subtitle)).allMatch { it.exists() }
        val text = log.readText()
        assertThat(text).contains("KEEP '${hd.path}' (1080p x264")
        assertThat(text).contains("DELETE '${sd.path}' (720p x264")
        assertThat(text).contains("MOVE '${subtitle.path}' -> '${File(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.en.srt").path}'")
        assertThat(text).contains("1 duplicates found, 1 worse versions (test run, nothing was deleted)")
    }

    @Test
    fun `should move the subtitles of the deleted version to the one which stays and delete the ones it already has`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val hd = file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.mkv")
        val hdEnglish = file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.en.srt")
        val sd = file(base, "Movies/I/Inception (2010) {tmdb-27205}.720p.x264.mkv")
        val sdEnglish = file(base, "Movies/I/Inception (2010) {tmdb-27205}.720p.x264.en.srt")
        val sdGerman = file(base, "Movies/I/Inception (2010) {tmdb-27205}.720p.x264.de.srt")
        val sdNfo = file(base, "Movies/I/Inception (2010) {tmdb-27205}.720p.x264.nfo")

        run(base)

        assertThat(hd).exists()
        assertThat(hdEnglish).exists()
        assertThat(File(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.de.srt")).exists()
        assertThat(listOf(sd, sdEnglish, sdGerman, sdNfo)).noneMatch { it.exists() }
    }

    @Test
    fun `should not take the subtitles of the better version for those of a version with a shorter name`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val shortName = file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.mkv", 100)
        val longName = file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.mkv", 100)
        val longNameSubtitle = file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.en.srt")

        run(base)

        // the x264 version wins (codec), the file with the short name goes, the subtitles of the winner stay where they are
        assertThat(longName).exists()
        assertThat(shortName).doesNotExist()
        assertThat(longNameSubtitle).exists()
    }

    @Test
    fun `should remove folders which are empty afterwards but keep the library folders`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val hd = file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.mkv")
        val sd = file(base, "Movies/Old/Sub/Inception (2010) {tmdb-27205}.720p.x264.mkv")

        run(base)

        assertThat(hd).exists()
        assertThat(sd).doesNotExist()
        assertThat(File(base, "Movies/Old")).doesNotExist()
        assertThat(File(base, "Movies/I")).exists()
        assertThat(File(base, "Movies")).exists()
    }

    @Test
    fun `should rank resolutions and codecs`() {
        assertThat(DuplicateCleaner.heightOf("1080p")).isEqualTo(1080)
        assertThat(DuplicateCleaner.heightOf("1080i")).isEqualTo(1080)
        assertThat(DuplicateCleaner.heightOf("4k")).isEqualTo(2160)
        assertThat(DuplicateCleaner.heightOf(null)).isEqualTo(0)
        assertThat(DuplicateCleaner.codecRankOf("x265")).isGreaterThan(DuplicateCleaner.codecRankOf("x264"))
        assertThat(DuplicateCleaner.codecRankOf("x264")).isGreaterThan(DuplicateCleaner.codecRankOf("xvid"))
        assertThat(DuplicateCleaner.codecRankOf("xvid")).isGreaterThan(DuplicateCleaner.codecRankOf(null))
    }
}
