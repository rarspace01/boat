package boat.info

import boat.utilities.HttpHelper
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.Executors

/**
 * Turns a Netflix viewing history (Account > Profile > Viewing activity > Download all) into a list of movies for
 * /boat/searchList. Netflix lists episodes as "Show: Season 3: Episode title", so the media proxy tells movies from
 * series. Not a test of anything, it is a tool which runs with the live APIs and the keys of boat.cfg:
 *
 *   NETFLIX_CSV=~/Downloads/NetflixViewingHistory.csv ./gradlew liveTest --tests '*NetflixHistoryImportTest*'
 *
 * The movies are written to NETFLIX_OUT (default netflixMovies.txt), one title per line.
 */
internal class NetflixHistoryImportTest {

    private val mediaProxyService = MediaProxyService(
        TheMovieDataBaseService(HttpHelper()), TheTVDBService(HttpHelper()), "build/netflixMediaProxyCache"
    )

    @Tag("live")
    @Test
    fun writeMoviesOfViewingHistory() {
        val csvPath = System.getenv("NETFLIX_CSV")
        assumeTrue(!csvPath.isNullOrBlank(), "NETFLIX_CSV is not set")
        val titles = parseTitles(File(csvPath.replaceFirst("~", System.getProperty("user.home"))).readText())

        val types = HashMap<String, MediaType?>()
        val executor = Executors.newFixedThreadPool(8)
        try {
            // the shows first (the part before the first colon), then the titles which are not episodes of a series
            val candidates = titles.flatMap { listOf(it.substringBefore(": ")) }.distinct() + titles
            candidates.distinct().map { title -> title to executor.submit<MediaType?> { typeOf(title) } }
                .forEach { (title, type) -> types[title] = type.get() }
        } finally {
            executor.shutdown()
            mediaProxyService.saveCache(force = true)
        }

        val movies = titles.filter { title ->
            val isEpisode = title.contains(": ") && types[title.substringBefore(": ")] == MediaType.Series
            !isEpisode && types[title] == MediaType.Movie
        }
        val unknown = titles.filter { types[it] == null && types[it.substringBefore(": ")] == null }
        File(System.getenv("NETFLIX_OUT") ?: "netflixMovies.txt").writeText(movies.joinToString("\n", postfix = "\n"))
        println("${titles.size} titles, ${movies.size} movies, ${unknown.size} without a match:")
        unknown.forEach { println("  $it") }
    }

    /** The type of the best match, like the first title in the search of the UI. */
    private fun typeOf(title: String): MediaType? =
        runCatching { mediaProxyService.searchWithTrailingYear(title).firstOrNull()?.type }.getOrNull()

    companion object {
        /** The distinct titles of the first column. */
        internal fun parseTitles(csv: String): List<String> = csv.lines().drop(1)
            .mapNotNull { line -> Regex("^\"(.*)\",\"[^\"]*\"$").find(line.trim())?.groupValues?.get(1)?.replace("\"\"", "\"")?.trim() }
            .filter { it.isNotEmpty() }.distinct()
    }
}
