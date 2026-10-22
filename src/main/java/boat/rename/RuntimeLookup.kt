package boat.rename

import boat.info.TheMovieDataBaseService
import boat.info.TheTVDBService
import java.util.concurrent.ConcurrentHashMap

/** How long a movie or an episode is according to the database it was named after. */
fun interface RuntimeLookup {
    /** The runtime in minutes of the movie ([season] null) or the episode of [remoteMediaId] (`tmdb-123`), null if unknown. */
    fun minutes(remoteMediaId: String, season: Int?, episode: Int?): Double?
}

/**
 * The runtimes of TMDB and TVDB. A season is fetched once for all of its episodes. A lookup which fails is not kept,
 * so it is tried again.
 */
class OnlineRuntimeLookup(
    private val theMovieDataBaseService: TheMovieDataBaseService,
    private val theTVDBService: TheTVDBService,
) : RuntimeLookup {
    private val movies = ConcurrentHashMap<String, Int>()
    private val seasons = ConcurrentHashMap<String, Map<Int, Int>>()

    override fun minutes(remoteMediaId: String, season: Int?, episode: Int?): Double? {
        val (provider, id) = idPattern.matchEntire(remoteMediaId.trim())?.destructured ?: return null
        return try {
            val minutes = if (season == null) {
                if (provider.lowercase() != "tmdb") return null
                movies[id] ?: theMovieDataBaseService.movieRuntime(id)?.also { movies[id] = it }
            } else {
                val key = "${provider.lowercase()}-$id S$season"
                val runtimes = seasons[key] ?: run {
                    if (provider.lowercase() == "tvdb") theTVDBService.episodeRuntimes(id, season)
                    else theMovieDataBaseService.episodeRuntimes(id, season)
                }.also { if (it.isNotEmpty()) seasons[key] = it }
                runtimes[episode]
            }
            minutes?.toDouble()
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        val idPattern = "\\{?(tmdb|tvdb)-(\\d+)}?".toRegex(RegexOption.IGNORE_CASE)
    }
}
