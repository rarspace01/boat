package boat.info

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class MediaProxyService @Autowired constructor(
    private val theMovieDataBaseService: TheMovieDataBaseService,
    private val theTVDBService: TheTVDBService
) {

    private val cache = ConcurrentHashMap<String, List<MediaItem>>()

    fun search(name: String?): List<MediaItem> {
        if (name.isNullOrBlank()) return emptyList()
        val cacheKey = "search:$name"
        return cache.getOrPut(cacheKey) {
            val tmdbResults = theMovieDataBaseService.search(name) ?: emptyList()
            val tvdbResults = theTVDBService.search(name)
            tmdbResults.plus(tvdbResults).distinctBy { it.title + it.year + it.type }
        }
    }

    fun search(name: String?, year: Int): List<MediaItem> {
        if (name.isNullOrBlank()) return emptyList()
        val cacheKey = "search:$name:$year"
        return cache.getOrPut(cacheKey) {
            val tmdbResults = theMovieDataBaseService.search(name, year) ?: emptyList()
            val tvdbResults = theTVDBService.search(name, year) ?: emptyList()
            tmdbResults.plus(tvdbResults).distinctBy { it.title + it.year + it.type }
        }
    }

    fun search(name: String?, type: MediaType?): List<MediaItem> {
        if (name.isNullOrBlank()) return emptyList()
        val cacheKey = "search:$name:$type"
        return cache.getOrPut(cacheKey) {
            when (type) {
                MediaType.Movie -> theMovieDataBaseService.search(name) ?: emptyList()
                MediaType.Series -> theTVDBService.search(name)
                else -> search(name)
            }
        }
    }

    fun search(name: String?, year: Int, type: MediaType?): List<MediaItem> {
        if (name.isNullOrBlank()) return emptyList()
        val cacheKey = "search:$name:$year:$type"
        return cache.getOrPut(cacheKey) {
            when (type) {
                MediaType.Movie -> theMovieDataBaseService.search(name, year) ?: emptyList()
                MediaType.Series -> theTVDBService.search(name, year)
                else -> search(name, year)
            }
        }
    }
}
