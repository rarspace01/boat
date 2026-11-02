package boat.rename

import boat.info.TheMovieDataBaseService
import boat.info.TheTVDBService
import boat.utilities.HttpHelper
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Manual runs against the real library, like [boat.ContentRenamerIntegrationTest]: moves the shows with a TMDB id to
 * their TVDB id ([SeriesIdMigrator]). The test run only writes what it would do to `seriesIds.log`, look at it before
 * the run which moves. Needs the TMDB and TVDB api keys.
 */
class SeriesIdMigrationIntegrationTest {

    @Disabled
    @Test
    fun manualTestRunSeries() {
        migrate(isTestRun = true)
    }

    @Disabled
    @Test
    fun migrateSeriesToTvdb() {
        migrate(isTestRun = false)
    }

    private fun migrate(isTestRun: Boolean) {
        val seriesFolder = File(LibraryPaths.defaultBasePath, "Series-Shows")
        val logFile = File("seriesIds.log")
        if (seriesFolder.exists()) {
            val httpHelper = HttpHelper()
            val lookup = OnlineSeriesIdLookup(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
            val result = SeriesIdMigrator(lookup).migrate(seriesFolder, logFile, isTestRun)
            println("${result.moves.size} shows, ${result.unsure.size} unsure, ${result.collisions.size} files left in place, see ${logFile.absolutePath}")
        }
    }
}
