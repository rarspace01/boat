package boat.info

import boat.utilities.HttpHelper
import boat.utilities.LoggerDelegate
import org.springframework.stereotype.Service
import java.io.IOException
import java.util.Locale

/**
 * The Blu-ray releases of a month from blu-ray.com. The release calendar page lists them as lines of a script like
 * `movies[0] = {id: 295087, title: 'Walk the Line', year: '2005', releasedate: 'January 01, 2021', ...};`
 */
@Service
class BluRayComService(private val httpHelper: HttpHelper) {

    private val logger by LoggerDelegate()

    fun getReleasesForMonthAndYear(month: Int, year: Int): List<MediaItem> {
        val response = try {
            httpHelper.getBinary(
                "https://www.blu-ray.com/movies/releasedates.php?year=$year&month=$month",
                headers = mapOf("User-Agent" to USER_AGENT)
            )
        } catch (e: IOException) {
            logger.warn("could not load the releases of {}/{}: {}", month, year, e.message)
            return emptyList()
        }
        if (response.status !in 200..299) {
            logger.warn("could not load the releases of {}/{}: HTTP {}", month, year, response.status)
            return emptyList()
        }
        // the page is ISO-8859-1 (meta tag), the server sends no charset
        return parseReleases(String(response.body, Charsets.ISO_8859_1))
    }

    internal fun parseReleases(page: String): List<MediaItem> =
        page.lineSequence().filter { MOVIE_LINE.containsMatchIn(it) }.mapNotNull { parseRelease(it) }.toList()

    private fun parseRelease(line: String): MediaItem? {
        val title = TITLE_FIELD.find(line)?.groupValues?.get(1)?.let { unescape(it) }?.takeIf { it.isNotBlank() } ?: return null
        val year = YEAR_FIELD.find(line)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 1900 }
        val type = if (title.lowercase(Locale.getDefault()).contains("season")) MediaType.Series else MediaType.Movie
        return MediaItem(title = title, originalTitle = title, year = year, type = type)
    }

    // the strings of the script are single quoted, a quote in a title is written as \'
    private fun unescape(value: String) = value.replace("\\'", "'").replace("\\\\", "\\")

    companion object {
        // the site answers "error42" to the user agents of browsers (they look like a bot without the other browser
        // headers), but serves the page to clients which say what they are
        private const val USER_AGENT = "boat (https://github.com/rarspace01/boat)"
        private val MOVIE_LINE = Regex("""^\s*movies\[\d+]\s*=\s*\{""")
        private val TITLE_FIELD = Regex("""(?<![A-Za-z_])title:\s*'((?:[^'\\]|\\.)*)'""")
        private val YEAR_FIELD = Regex("""(?<![A-Za-z_])year:\s*'(\d{4})'""")
    }
}
