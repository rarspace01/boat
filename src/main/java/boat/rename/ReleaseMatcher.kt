package boat.rename

import boat.info.MediaItem
import boat.info.MediaProxyService
import boat.info.MediaType
import boat.rename.MovieNameParser.normalizeMovieTitle
import java.io.File

/**
 * Finds the movie or series of a download from its release name ("Ivakisaka.2010.1080p.BluRay.x264-GRP") the way the
 * renamer does for a file: name and year first, then the search. Only a result with the title of the name counts, the
 * first hit of a search for the whole release name can be another movie.
 */
internal class ReleaseMatcher(mediaProxyService: MediaProxyService) {

    private val movieMatcher = MovieMatcher(mediaProxyService)

    /** Null if there is no result with the title of [releaseName]. */
    fun match(releaseName: String, mediaType: MediaType?): MediaItem? {
        if (releaseName.isBlank()) return null
        // the parsers expect a file, without an extension the last part of the name would be taken for one
        val file = File(releaseName.replace('/', ' ').replace('\\', ' ') + ".mkv")
        val isSeries = when (mediaType) {
            MediaType.Series -> true
            MediaType.Movie -> false
            else -> EpisodeParser.extractSeason(file) != null || EpisodeParser.extractEpisode(file) != null
        }
        return if (isSeries) {
            val name = MediaNameParser.extractName(file)
            val year = MediaNameParser.extractYear(file.name)
            movieMatcher.searchMediaForFile(name, year, MediaType.Series, true)
                ?.takeIf { it.type == MediaType.Series && hasTitle(it, name, year) }
        } else {
            val (name, year) = MovieNameParser.extractMovieNameAndYear(file)
            movieMatcher.searchMediaForFile(name, year, MediaType.Movie, false)
                ?.takeIf { it.type == MediaType.Movie && hasTitle(it, name, year) }
        }
    }

    private fun hasTitle(item: MediaItem, name: String, year: Int?): Boolean {
        if (name.isBlank()) return false
        // "Dovaga Wko (2005)" is "Dovaga Wko" of 2005
        fun key(title: String) = normalizeMovieTitle(title.replace(yearSuffixPattern, ""))
        val names = listOfNotNull(key(name), year?.let { key("$name $it") })
        return listOfNotNull(item.title, item.originalTitle).any { key(it) in names }
    }

    private companion object {
        val yearSuffixPattern = "\\s*\\((?:19|20)\\d{2}\\)$".toRegex()
    }
}
