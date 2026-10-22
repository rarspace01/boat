package boat.rename

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

internal class DuplicateCleanerTest {

    /** What the fake ffprobe says about a file, by the start of its name. */
    private val infos = mutableMapOf<String, VideoInfo?>()

    private val cleaner = DuplicateCleaner({ file -> infos.entries.firstOrNull { file.name.startsWith(it.key) }?.value ?: infos[file.name] }, probeThreads = 2, ranking = DuplicateCleaner.Ranking.BITRATE_FIRST)

    private fun video(codec: String = "h264", height: Int = 1080, width: Int = height * 16 / 9, bitrate: Long = 5_000_000, seconds: Double = 1500.0) =
        VideoInfo(codec, width, height, bitrate, seconds)

    /** A file, the probe knows it under its whole name. */
    private fun file(base: File, relativePath: String, info: VideoInfo? = video(), bytes: Int = 100): File =
        File(base, relativePath).apply {
            parentFile.mkdirs()
            writeBytes(ByteArray(bytes) { 'x'.code.toByte() })
            infos[name] = info
        }

    private fun run(base: File, isTestRun: Boolean = false, logFile: File? = null) =
        cleaner.processFilesFromDirectory(base.absolutePath, logFile, isTestRun)

    // ---- ranking by what the video really is

    @Test
    fun `should decide by the real resolution and not by the name`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        // no resolution in the names, the file with the bigger size is the one with the lower resolution
        val hd = file(base, "Series-Shows/D/Dark {tvdb-334824}/Season 1/Dark.S01E01.mkv", video(height = 1080), bytes = 100)
        val sd = file(base, "Series-Shows/D/Dark {tvdb-334824}/Season 1/Dark.S01E01.mp4", video(height = 480), bytes = 900)

        val result = run(base)

        assertThat(result.groups).hasSize(1)
        assertThat(result.groups.single().keep.file).isEqualTo(hd)
        assertThat(hd).exists()
        assertThat(sd).doesNotExist()
    }

    @Test
    fun `should not trust a resolution in the name that the video does not have`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val fake1080 = file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x265.mkv", video(codec = "hevc", height = 480))
        val real720 = file(base, "Movies/I/Inception (2010) {tmdb-27205}.720p.x264.mkv", video(codec = "h264", height = 720))

        run(base)

        assertThat(real720).exists()
        assertThat(fake1080).doesNotExist()
    }

    @Test
    fun `should treat a cropped widescreen picture as its resolution class`() {
        assertThat(DuplicateCleaner.resolutionClassOf(VideoInfo("h264", 1920, 800, null, null))).isEqualTo(1080)
        assertThat(DuplicateCleaner.resolutionClassOf(VideoInfo("h264", 1280, 536, null, null))).isEqualTo(720)
        assertThat(DuplicateCleaner.resolutionClassOf(VideoInfo("h264", 3840, 2160, null, null))).isEqualTo(2160)
        assertThat(DuplicateCleaner.resolutionClassOf(VideoInfo("h264", 720, 576, null, null))).isEqualTo(576)
        assertThat(DuplicateCleaner.resolutionClassOf(VideoInfo("h264", 640, 480, null, null))).isEqualTo(480)
        assertThat(DuplicateCleaner.resolutionClassOf(VideoInfo("h264", null, null, null, null))).isEqualTo(0)
    }

    @Test
    fun `should prefer the higher bitrate at the same resolution and the codec if it is the same`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val big = file(base, "Movies/I/Inception (2010) {tmdb-27205}.mkv", video(codec = "h264", bitrate = 9_000_000), bytes = 900)
        val small = file(base, "Movies/I/Inception (2010) {tmdb-27205}.mp4", video(codec = "hevc", bitrate = 2_000_000))
        val dune1 = file(base, "Movies/D/Dune (2021) {tmdb-438631}.mkv", video(codec = "h264", bitrate = 4_000_000))
        val dune2 = file(base, "Movies/D/Dune (2021) {tmdb-438631}.mp4", video(codec = "hevc", bitrate = 4_000_000))

        run(base)

        assertThat(big).exists()
        assertThat(small).doesNotExist()
        assertThat(dune2).exists()
        assertThat(dune1).doesNotExist()
    }

    @Test
    fun `should prefer the better codec before the bitrate if asked to`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val hevc = file(base, "Movies/I/Inception (2010) {tmdb-27205}.mkv", video(codec = "hevc", bitrate = 2_000_000))
        val h264 = file(base, "Movies/I/Inception (2010) {tmdb-27205}.mp4", video(codec = "h264", bitrate = 9_000_000), bytes = 900)
        val old = file(base, "Movies/I/Inception (2010) {tmdb-27205}.avi", video(codec = "mpeg4", bitrate = 20_000_000), bytes = 5000)
        val dune1 = file(base, "Movies/D/Dune (2021) {tmdb-438631}.mkv", video(bitrate = 4_000_000))
        val dune2 = file(base, "Movies/D/Dune (2021) {tmdb-438631}.mp4", video(bitrate = 8_000_000))

        DuplicateCleaner({ infos[it.name] }, 2, DuplicateCleaner.Ranking.CODEC_FIRST).processFilesFromDirectory(base.absolutePath, null, false)

        assertThat(hevc).exists()
        assertThat(h264).doesNotExist()
        assertThat(old).doesNotExist()
        assertThat(dune2).exists()
        assertThat(dune1).doesNotExist()
    }

    @Test
    fun `the resolution counts before bitrate and codec in both rankings`(@TempDir tempDir: Path) {
        DuplicateCleaner.Ranking.entries.forEach { ranking ->
            val base = File(tempDir.toFile(), ranking.name)
            val hd = file(base, "Movies/I/Inception (2010) {tmdb-27205}.mkv", video(height = 1080, codec = "h264", bitrate = 1_000_000))
            val sd = file(base, "Movies/I/Inception (2010) {tmdb-27205}.mp4", video(height = 480, codec = "hevc", bitrate = 9_000_000))

            DuplicateCleaner({ infos[it.name] }, 2, ranking).processFilesFromDirectory(base.absolutePath, null, false)

            assertThat(hd).describedAs(ranking.name).exists()
            assertThat(sd).describedAs(ranking.name).doesNotExist()
        }
    }

    @Test
    fun `the ranking comes from the setting and is the bitrate by default`() {
        assertThat(DuplicateCleaner.Ranking.of("codec")).isEqualTo(DuplicateCleaner.Ranking.CODEC_FIRST)
        assertThat(DuplicateCleaner.Ranking.of(" Codec ")).isEqualTo(DuplicateCleaner.Ranking.CODEC_FIRST)
        assertThat(DuplicateCleaner.Ranking.of("bitrate")).isEqualTo(DuplicateCleaner.Ranking.BITRATE_FIRST)
        assertThat(DuplicateCleaner.Ranking.of(null)).isEqualTo(DuplicateCleaner.Ranking.BITRATE_FIRST)
        assertThat(DuplicateCleaner.Ranking.of("something else")).isEqualTo(DuplicateCleaner.Ranking.BITRATE_FIRST)
    }

    @Test
    fun `should keep exactly one of identical versions`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val first = file(base, "Movies/I/Inception (2010) {tmdb-27205}.mkv")
        val second = file(base, "Movies/I/Inception (2010) {tmdb-27205}.mp4")

        run(base)

        assertThat(listOf(first, second).count { it.exists() }).isEqualTo(1)
    }

    // ---- groups which are not decided

    @Test
    fun `should not decide groups with versions of different length`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val full = file(base, "Series-Shows/D/Dark {tvdb-334824}/Season 1/Dark.S01E01.mkv", video(seconds = 3000.0))
        val cut = file(base, "Series-Shows/D/Dark {tvdb-334824}/Season 1/Dark.S01E01.mp4", video(height = 480, seconds = 1500.0))
        val log = File(base, "duplicates.log")

        val result = run(base, logFile = log)

        assertThat(result.groups).isEmpty()
        assertThat(result.unsureGroups).hasSize(1)
        assertThat(result.unsureGroups.single().reason).contains("not equally long").contains("0:50:00").contains("0:25:00")
        assertThat(full).exists()
        assertThat(cut).exists()
        assertThat(log.readText()).contains("UNSURE series {tvdb-334824} S01E01: the versions are not equally long")
    }

    @Test
    fun `should decide versions which differ in length by a few percent`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        // 24 and 25 frames per second
        val a = file(base, "Movies/I/Inception (2010) {tmdb-27205}.mkv", video(height = 1080, seconds = 8880.0))
        val b = file(base, "Movies/I/Inception (2010) {tmdb-27205}.mp4", video(height = 720, seconds = 8520.0))

        val result = run(base)

        assertThat(result.groups).hasSize(1)
        assertThat(a).exists()
        assertThat(b).doesNotExist()
    }

    @Test
    fun `should not decide groups with a file ffprobe cannot read`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val good = file(base, "Movies/I/Inception (2010) {tmdb-27205}.mkv", video())
        val broken = file(base, "Movies/I/Inception (2010) {tmdb-27205}.mp4", null)

        val result = run(base)

        assertThat(result.groups).isEmpty()
        assertThat(result.unsureGroups.single().reason).contains("ffprobe could not read").contains("Inception (2010) {tmdb-27205}.mp4")
        assertThat(result.probeStats.failed).isEqualTo(1)
        assertThat(good).exists()
        assertThat(broken).exists()
    }

    @Test
    fun `should not decide groups if the length is not known`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val a = file(base, "Movies/I/Inception (2010) {tmdb-27205}.mkv", video())
        val b = file(base, "Movies/I/Inception (2010) {tmdb-27205}.mp4", VideoInfo("h264", 1920, 1080, 1_000_000, null))

        val result = run(base)

        assertThat(result.unsureGroups.single().reason).contains("length")
        assertThat(listOf(a, b)).allMatch { it.exists() }
    }

    // ---- what is not compared

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

        val result = run(base)

        assertThat(result.groups).isEmpty()
        assertThat(result.unsureGroups).isEmpty()
        assertThat(files).allMatch { it.exists() }
        // only files which could be duplicates are read
        assertThat(result.probeStats.files).isEqualTo(0)
    }

    @Test
    fun `should find duplicates in different folders of the movies`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val a = file(base, "Movies/I/Inception (2010) {tmdb-27205}.mkv", video(height = 1080))
        val b = file(base, "Movies/Old/Inception (2010) {tmdb-27205}.avi", video(height = 480, codec = "mpeg4"))

        run(base)

        assertThat(a).exists()
        assertThat(b).doesNotExist()
    }

    @Test
    fun `should keep the best version of every episode`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val show = "Series-Shows/D/Dark {tvdb-334824}/Season 1"
        val e1hd = file(base, "$show/Dark.S01E01.mkv", video(codec = "hevc"))
        val e1sd = file(base, "$show/Dark.S01E01.mp4", video(height = 720))
        val e2 = file(base, "$show/Dark.S01E02.mkv", video(height = 720))
        val e3a = file(base, "$show/Dark.S01E03.mkv", video(bitrate = 4_000_000))
        val e3b = file(base, "$show/Dark.S01E03.mp4", video(bitrate = 6_000_000))

        val result = run(base)

        assertThat(result.groups.map { it.key }).containsExactly("series {tvdb-334824} S01E01", "series {tvdb-334824} S01E03")
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
            file(base, "Series-Shows/D/Dark {tvdb-334824}/Featurettes/Making of.1080p.mkv"),
            file(base, "Series-Shows/N/NoId/Season 1/NoId.S01E01.1080p.mkv"),
            file(base, "Series-Shows/N/NoId/Season 1/NoId.S01E01.720p.mkv"),
        )

        assertThat(run(base).groups).isEmpty()
        assertThat(files).allMatch { it.exists() }
    }

    // ---- test run, log, benchmark and subtitles

    @Test
    fun `a test run only writes to the log and tells how fast ffprobe was`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val hd = file(base, "Movies/I/Inception (2010) {tmdb-27205}.mkv", video(codec = "hevc", bitrate = 3_000_000, seconds = 8880.0))
        val sd = file(base, "Movies/I/Inception (2010) {tmdb-27205}.avi", video(codec = "mpeg4", height = 480, bitrate = 1_000_000, seconds = 8880.0))
        val subtitle = file(base, "Movies/I/Inception (2010) {tmdb-27205}.en.srt")
        val log = File(base, "duplicates.log")

        val result = run(base, isTestRun = true, logFile = log)

        assertThat(result.groups).hasSize(1)
        assertThat(listOf(hd, sd, subtitle)).allMatch { it.exists() }
        val text = log.readText()
        assertThat(text).contains("ffprobe: 2 files in ").contains("with 2 threads").contains("files/s), per file avg ").contains("median ").contains("95% under ").contains("max ")
        assertThat(text).contains("KEEP '${hd.path}' (1080p, hevc, 3.0 Mbit/s, 2:28:00,")
        assertThat(text).contains("DELETE '${sd.path}' (480p, mpeg4, 1.0 Mbit/s, 2:28:00,")
        assertThat(text).doesNotContain("srt")
        assertThat(text).contains("1 duplicates found, 1 worse versions (test run, nothing was deleted), 0 groups not decided")
    }

    @Test
    fun `should keep the subtitles which versions with the same name share`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val hd = file(base, "Movies/I/Inception (2010) {tmdb-27205}.mkv", video(height = 1080))
        val sd = file(base, "Movies/I/Inception (2010) {tmdb-27205}.avi", video(height = 480))
        val subtitle = file(base, "Movies/I/Inception (2010) {tmdb-27205}.en.srt")

        run(base)

        assertThat(hd).exists()
        assertThat(sd).doesNotExist()
        assertThat(subtitle).exists()
    }

    @Test
    fun `the benchmark counts the files and the time`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        repeat(6) { index ->
            file(base, "Movies/A/Film $index (2000) {tmdb-$index}.mkv")
            file(base, "Movies/A/Film $index (2000) {tmdb-$index}.mp4")
        }
        val slowCleaner = DuplicateCleaner({ Thread.sleep(20); video() }, probeThreads = 3)

        val stats = slowCleaner.findDuplicates(base.absolutePath).probeStats

        assertThat(stats.files).isEqualTo(12)
        assertThat(stats.failed).isEqualTo(0)
        assertThat(stats.threads).isEqualTo(3)
        assertThat(stats.durationsMillis).hasSize(12)
        assertThat(stats.averageMillis).isGreaterThanOrEqualTo(20)
        assertThat(stats.maxMillis).isGreaterThanOrEqualTo(stats.medianMillis)
        // 12 files of 20 ms on 3 threads are faster than one after the other
        assertThat(stats.wallMillis).isLessThan(stats.durationsMillis.sum())
        assertThat(stats.filesPerSecond).isGreaterThan(0.0)
    }

    @Test
    fun `should move the subtitles of the deleted version to the one which stays and delete the ones it already has`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val hd = file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.mkv", video(height = 1080))
        val hdEnglish = file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.en.srt")
        val sd = file(base, "Movies/I/Inception (2010) {tmdb-27205}.720p.x264.mkv", video(height = 720))
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
        val shortName = file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.mkv", video(height = 720))
        val longName = file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.mkv", video(height = 1080))
        val longNameSubtitle = file(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.en.srt")

        run(base)

        assertThat(longName).exists()
        assertThat(shortName).doesNotExist()
        assertThat(longNameSubtitle).exists()
    }

    @Test
    fun `should remove folders which are empty afterwards but keep the library folders`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val hd = file(base, "Movies/I/Inception (2010) {tmdb-27205}.mkv", video(height = 1080))
        val sd = file(base, "Movies/Old/Sub/Inception (2010) {tmdb-27205}.avi", video(height = 480))

        run(base)

        assertThat(hd).exists()
        assertThat(sd).doesNotExist()
        assertThat(File(base, "Movies/Old")).doesNotExist()
        assertThat(File(base, "Movies/I")).exists()
        assertThat(File(base, "Movies")).exists()
    }

    @Test
    fun `should rank codecs`() {
        assertThat(DuplicateCleaner.codecRankOf("hevc")).isGreaterThan(DuplicateCleaner.codecRankOf("h264"))
        assertThat(DuplicateCleaner.codecRankOf("av1")).isEqualTo(DuplicateCleaner.codecRankOf("hevc"))
        assertThat(DuplicateCleaner.codecRankOf("h264")).isGreaterThan(DuplicateCleaner.codecRankOf("mpeg4"))
        assertThat(DuplicateCleaner.codecRankOf("mpeg4")).isGreaterThan(DuplicateCleaner.codecRankOf(null))
    }
}
