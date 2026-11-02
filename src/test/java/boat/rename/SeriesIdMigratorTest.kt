package boat.rename

import boat.info.MediaItem
import boat.info.MediaType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class SeriesIdMigratorTest {

    @TempDir
    lateinit var series: File

    /** TMDB ids to TVDB ids, the season counts by remote id and the TVDB titles. */
    private class FakeLookup(
        val tvdbIds: Map<String, String>,
        val seasons: Map<String, Int>,
        val titles: Map<String, String> = emptyMap(),
    ) : SeriesIdLookup {
        override fun tvdbIdOf(tmdbId: String) = tvdbIds[tmdbId]
        override fun tmdbSeries(tmdbId: String) = seasons["tmdb-$tmdbId"]?.let { series("Tmdb title", "tmdb-$tmdbId", it) }
        override fun tvdbSeries(tvdbId: String) = titles["tvdb-$tvdbId"]?.let { series(it, "tvdb-$tvdbId", seasons["tvdb-$tvdbId"]) }

        private fun series(title: String, id: String, seasonCount: Int?) =
            MediaItem(title, title, 2010, MediaType.Series, "{$id}").apply { this.seasonCount = seasonCount }
    }

    private fun file(path: String, content: String = path) = File(series, path).apply { parentFile.mkdirs(); writeText(content) }

    private fun files() = series.walkTopDown().filter { it.isFile }.map { it.relativeTo(series).path }.sorted().toList()

    @Test
    fun shouldMoveAShowToItsTvdbId() {
        file("D/Dobi {tmdb-1}/Season 1/Dobi.S01E01.1080p.mkv")
        file("D/Dobi {tmdb-1}/Season 2/Dobi.S02E01.1080p.mkv")
        file("D/Dobi {tmdb-1}/Featurettes/Behind.mkv")
        val lookup = FakeLookup(mapOf("1" to "334824"), mapOf("tmdb-1" to 2, "tvdb-334824" to 2), mapOf("tvdb-334824" to "Dobi"))

        val result = SeriesIdMigrator(lookup).migrate(series, isTestRun = false)

        assertThat(files()).containsExactly(
            "D/Dobi {tvdb-334824}/Featurettes/Behind.mkv",
            "D/Dobi {tvdb-334824}/Season 1/Dobi.S01E01.1080p.mkv",
            "D/Dobi {tvdb-334824}/Season 2/Dobi.S02E01.1080p.mkv",
        )
        assertThat(File(series, "D/Dobi {tmdb-1}")).doesNotExist()
        assertThat(result.moves).hasSize(1)
        assertThat(result.unsure).isEmpty()
    }

    @Test
    fun shouldOnlyLogInATestRun() {
        file("D/Dobi {tmdb-1}/Season 1/Dobi.S01E01.mkv")
        val log = File(series.parentFile, "${series.name}.log")
        val lookup = FakeLookup(mapOf("1" to "334824"), mapOf("tmdb-1" to 1, "tvdb-334824" to 1), mapOf("tvdb-334824" to "Dobi"))

        val result = SeriesIdMigrator(lookup).migrate(series, log, isTestRun = true)

        assertThat(files()).containsExactly("D/Dobi {tmdb-1}/Season 1/Dobi.S01E01.mkv")
        assertThat(result.moves).hasSize(1)
        assertThat(log.readText()).contains("MOVE: ${File(series, "D/Dobi {tmdb-1}").path} → ${File(series, "D/Dobi {tvdb-334824}").path}")
        log.delete()
    }

    @Test
    fun shouldPutTheShowInTheLetterFolderOfItsTvdbTitle() {
        file("O/The Osugol {tmdb-2316}/Season 1/The Osugol.S01E01.mkv")
        val lookup = FakeLookup(mapOf("2316" to "73244"), mapOf("tmdb-2316" to 9, "tvdb-73244" to 9), mapOf("tvdb-73244" to "The Osugol (US)"))

        SeriesIdMigrator(lookup).migrate(series, isTestRun = false)

        assertThat(files()).containsExactly("O/The Osugol (US) {tvdb-73244}/Season 1/The Osugol.S01E01.mkv")
    }

    @Test
    fun shouldMergeIntoAnExistingTvdbFolderAndLeaveFilesWhichAreThereAlready() {
        file("D/Dobi {tmdb-1}/Season 1/Dobi.S01E01.mkv", "old")
        file("D/Dobi {tmdb-1}/Season 1/Dobi.S01E02.mkv")
        file("D/Dobi {tvdb-334824}/Season 1/Dobi.S01E01.mkv", "new")
        file("D/Dobi {tvdb-334824}/Season 2/Dobi.S02E01.mkv")
        val lookup = FakeLookup(mapOf("1" to "334824"), mapOf("tmdb-1" to 2, "tvdb-334824" to 2), mapOf("tvdb-334824" to "Dobi"))

        val result = SeriesIdMigrator(lookup).migrate(series, isTestRun = false)

        assertThat(files()).containsExactly(
            "D/Dobi {tmdb-1}/Season 1/Dobi.S01E01.mkv",
            "D/Dobi {tvdb-334824}/Season 1/Dobi.S01E01.mkv",
            "D/Dobi {tvdb-334824}/Season 1/Dobi.S01E02.mkv",
            "D/Dobi {tvdb-334824}/Season 2/Dobi.S02E01.mkv",
        )
        assertThat(File(series, "D/Dobi {tvdb-334824}/Season 1/Dobi.S01E01.mkv").readText()).isEqualTo("new")
        assertThat(result.collisions).containsExactly(File(series, "D/Dobi {tmdb-1}/Season 1/Dobi.S01E01.mkv"))
    }

    @Test
    fun shouldLeaveShowsWhoseSeasonsDiffer() {
        // TMDB splits the series into two with their own seasons, TVDB has one with all of them
        file("G/Gurabi Bekoff {tmdb-34549}/Season 8/Gurabi Bekoff.S08E01.mkv")
        val lookup = FakeLookup(mapOf("34549" to "184871"), mapOf("tmdb-34549" to 7, "tvdb-184871" to 17), mapOf("tvdb-184871" to "Gurabi Bekoff"))

        val result = SeriesIdMigrator(lookup).migrate(series, isTestRun = false)

        assertThat(files()).containsExactly("G/Gurabi Bekoff {tmdb-34549}/Season 8/Gurabi Bekoff.S08E01.mkv")
        assertThat(result.unsure).singleElement().asString().contains("the seasons differ, {tmdb-34549} has 7, {tvdb-184871} has 17")
    }

    @Test
    fun shouldMoveShowsWhoseSeasonsDifferIfAllSeasonsOnDiskExistInBoth() {
        // only a season which is not here is missing at TMDB
        file("F/Fondu {tmdb-5}/Season 1/Fondu.S01E01.mkv")
        file("F/Fondu {tmdb-5}/Season 3/Fondu.S03E01.mkv")
        val lookup = FakeLookup(mapOf("5" to "55"), mapOf("tmdb-5" to 3, "tvdb-55" to 4), mapOf("tvdb-55" to "Fondu"))

        val result = SeriesIdMigrator(lookup).migrate(series, isTestRun = false)

        assertThat(files()).containsExactly("F/Fondu {tvdb-55}/Season 1/Fondu.S01E01.mkv", "F/Fondu {tvdb-55}/Season 3/Fondu.S03E01.mkv")
        assertThat(result.unsure).isEmpty()
    }

    @Test
    fun shouldLeaveShowsWhoseSeasonsDifferWithoutSeasonFolders() {
        file("R/Ranmo {tmdb-6}/Other/Extra.mkv")
        val lookup = FakeLookup(mapOf("6" to "66"), mapOf("tmdb-6" to 1, "tvdb-66" to 7), mapOf("tvdb-66" to "Ranmo"))

        val result = SeriesIdMigrator(lookup).migrate(series, isTestRun = false)

        assertThat(files()).containsExactly("R/Ranmo {tmdb-6}/Other/Extra.mkv")
        assertThat(result.unsure).hasSize(1)
    }

    @Test
    fun shouldLeaveShowsWhichTmdbSplitsEvenIfTheSeasonsMatch() {
        file("G/Gurabi Bekoff {tmdb-34549}/Season 1/Gurabi Bekoff.S01E01.mkv")
        file("G/Gurabi Bekoff {tmdb-87012}/Season 1/Gurabi Bekoff.S01E01.mkv")
        val lookup = FakeLookup(
            mapOf("34549" to "184871", "87012" to "184871"),
            mapOf("tmdb-34549" to 17, "tmdb-87012" to 17, "tvdb-184871" to 17), mapOf("tvdb-184871" to "Gurabi Bekoff")
        )

        val result = SeriesIdMigrator(lookup).migrate(series, isTestRun = false)

        assertThat(files()).hasSize(2).allMatch { it.contains("{tmdb-") }
        assertThat(result.unsure).hasSize(2).allMatch { it.contains("TMDB splits {tvdb-184871}") }
    }

    @Test
    fun shouldLeaveShowsWithoutTvdbIdAndOthers() {
        file("D/Dobi {tmdb-1}/Season 1/Dobi.S01E01.mkv")
        file("D/Daca {tvdb-5}/Season 1/Daca.S01E01.mkv")
        file("D/Dibu/Season 1/Dibu.S01E01.mkv")
        val lookup = FakeLookup(emptyMap(), emptyMap())

        val result = SeriesIdMigrator(lookup).migrate(series, isTestRun = false)

        assertThat(files()).hasSize(3)
        assertThat(result.unsure).singleElement().asString().contains("TMDB has no TVDB id for {tmdb-1}")
        assertThat(result.moves).isEmpty()
    }
}
