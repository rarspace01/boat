package boat.rename

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import boat.info.TheMovieDataBaseService
import boat.info.TheTVDBService
import boat.utilities.HttpHelper
import java.io.File

/**
 * Manual runs against the real library, like [boat.ContentRenamerIntegrationTest]. The test runs only write what
 * would be deleted to `duplicates.log` (with the time ffprobe took), look at it before starting a run which deletes.
 * Rename the library first, duplicates are only found by the remote media id in the names. Needs ffprobe (ffmpeg).
 */
class DuplicateCleanerIntegrationTest {
    val homeDir: String? = System.getProperty("user.home")

    @Disabled
    @Test
    fun manualTestRunMovies() {
        findDuplicates("$homeDir/pfdb/PFDB/Movies/", isTestRun = true)
    }

    @Disabled
    @Test
    fun manualTestRunSeries() {
        findDuplicates("$homeDir/pfdb/PFDB/Series-Shows/", isTestRun = true)
    }

    @Disabled
    @Test
    fun deleteDuplicateMovies() {
        findDuplicates("$homeDir/pfdb/PFDB/Movies/", isTestRun = false)
    }

    @Disabled
    @Test
    fun deleteDuplicateSeries() {
        findDuplicates("$homeDir/pfdb/PFDB/Series-Shows/", isTestRun = false)
    }

    private fun runtimeLookup(): RuntimeLookup {
        val httpHelper = HttpHelper()
        return OnlineRuntimeLookup(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
    }

    private fun findDuplicates(path: String, isTestRun: Boolean) {
        val logFile = File("duplicates.log")
        if (logFile.exists()) logFile.delete()
        if (File(path).exists()) {
            val result = DuplicateCleaner(runtimeLookup = runtimeLookup()).processFilesFromDirectory(path, logFile, isTestRun)
            println("${result.groups.size} duplicates and ${result.unsureGroups.size} groups not decided in $path, ${result.probeStats}")
            println("see ${logFile.absolutePath}")
        }
    }
}
