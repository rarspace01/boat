package boat.info

import boat.torrent.Torrent
import boat.torrent.TorrentHelper.getNormalizedTorrentStringWithSpaces
import boat.torrent.TorrentHelper.urlEncode
import boat.utilities.HttpHelper
import boat.utilities.PropertiesHelper
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.function.Consumer
import java.util.regex.Pattern

@Service
class TheMovieDataBaseService @Autowired constructor(private val httpHelper: HttpHelper) {
    private val apiRootUrl = "https://api.themoviedb.org/3"
    private val apiKey = PropertiesHelper.getProperty("TFDB_APIKEY")
    private val baseUrl = String.format("%s/search/multi?api_key=%s", apiRootUrl, apiKey)

    fun search(name: String?): List<MediaItem>? {
        return name?.let { val url = "$baseUrl&query=${urlEncode(name)}"
            parseResponsePage(httpHelper.getPage(url)) } ?: emptyList()
    }

    fun search(name: String?, year: Int): List<MediaItem>? {
        val urlString = String.format("%s&query=%s&year=%d", baseUrl, urlEncode(name!!), year)
        val page = httpHelper.getPage(urlString)
        return parseResponsePage(page)
    }

    /**
     * Searches only movies or only series. Unlike search/multi, which ignores the year, these endpoints filter by it.
     */
    fun search(name: String, year: Int, mediaType: MediaType): List<MediaItem>? {
        val (path, yearParameter) = if (mediaType == MediaType.Series) "tv" to "first_air_date_year" else "movie" to "year"
        val page = httpHelper.getPage("$apiRootUrl/search/$path?api_key=$apiKey&query=${urlEncode(name)}&$yearParameter=$year")
        return parseResponsePage(page, path)
    }

    /**
     * The media items of a search response. Null if there was no usable answer (no response, no JSON or an error
     * like an invalid api key), which is something else than an answer without results.
     */
    fun parseResponsePage(pageContent: String?, defaultMediaType: String? = null): List<MediaItem>? {
        if (pageContent.isNullOrBlank()) {
            return null
        }
        val mediaItems: MutableList<MediaItem> = ArrayList()
        val jsonRoot = try {
            JsonParser.parseString(pageContent)
        } catch (_: JsonParseException) {
            return null
        }
        if (!jsonRoot.isJsonObject) return null
        val results = jsonRoot.asJsonObject["results"]
        if (results == null || !results.isJsonArray) return null
        val jsonArray = results.asJsonArray
        jsonArray.forEach(Consumer { jsonMedia: JsonElement ->
            val jsonMediaObject = jsonMedia.asJsonObject
            // typed searches (search/movie, search/tv) don't return a media_type
            val mediaTypeString = (jsonMediaObject["media_type"]?.asString ?: defaultMediaType)?.lowercase(Locale.getDefault())
            mediaTypeString?.let { parseMediaObject(jsonMediaObject, it) }?.let { mediaItems.add(it) }
        })
        return mediaItems
    }

    fun findById(id: String, mediaType: MediaType): MediaItem? {
        val path = if (mediaType == MediaType.Series) "tv" else "movie"
        val page = httpHelper.getPage("$apiRootUrl/$path/${urlEncode(id)}?api_key=$apiKey")
        return parseDetailsPage(page, mediaType)
    }

    fun parseDetailsPage(pageContent: String?, mediaType: MediaType): MediaItem? {
        if (pageContent.isNullOrBlank()) return null
        return try {
            val jsonRoot = JsonParser.parseString(pageContent)
            if (!jsonRoot.isJsonObject) return null
            val jsonMediaObject = jsonRoot.asJsonObject
            if (!jsonMediaObject.has("id")) return null
            parseMediaObject(jsonMediaObject, if (mediaType == MediaType.Series) "tv" else "movie")
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMediaObject(jsonMediaObject: JsonObject, mediaTypeString: String): MediaItem? {
        var title: String? = null
        var originalTitle: String? = null
        if (mediaTypeString.contains("tv")) {
            title = jsonMediaObject["name"].asString
            originalTitle = jsonMediaObject["original_name"].asString
        } else {
            val titleElement = jsonMediaObject["title"]
            val originalTitleElement = jsonMediaObject["original_title"]
            if (titleElement != null) {
                title = titleElement.asString
            }
            if (originalTitleElement != null) {
                originalTitle = originalTitleElement.asString
            }
        }
        val mediaType = determineMediaType(mediaTypeString)
        var year: Int? = null
        var seasonCount: Int? = null
        try {
            val calendar = Calendar.getInstance()
            val releaseDate = jsonMediaObject["release_date"]
            val firstAirDate = jsonMediaObject["first_air_date"]
            val releaseDateString = if (releaseDate != null) releaseDate.asString else firstAirDate?.asString
            if (releaseDateString != null) {
                calendar.time = SimpleDateFormat("yyyy-MM-dd").parse(releaseDateString)
                year = calendar[Calendar.YEAR]
            }
        } catch (_: ParseException) {
        }

        // TMDB search multi doesn't provide season_count usually, 
        // but sometimes it's available in other contexts.
        // If it's a TV show, we might want it.
        if (jsonMediaObject.has("season_count")) {
            seasonCount = jsonMediaObject["season_count"].asInt
        } else if (jsonMediaObject.has("number_of_seasons")) {
            // provided by the tv details endpoint
            seasonCount = jsonMediaObject["number_of_seasons"].asInt
        }

        if (title != null || originalTitle != null) {
            val mediaItem = MediaItem(title ?: originalTitle!!, originalTitle, year, mediaType)
            mediaItem.seasonCount = seasonCount
            if (jsonMediaObject.has("id")) {
                mediaItem.id = "{tmdb-${jsonMediaObject["id"].asInt}}"
            }
            return mediaItem
        }
        return null
    }

    private fun determineMediaType(mediaTypeString: String): MediaType {
        val mediaTypeStringCleaned = mediaTypeString.lowercase(Locale.getDefault())
        return if (mediaTypeStringCleaned.contains("movie")) {
            MediaType.Movie
        } else if (mediaTypeStringCleaned.contains("series") || mediaTypeStringCleaned.contains("tv")) {
            MediaType.Series
        } else {
            MediaType.Other
        }
    }

    fun determineMediaType(remoteTorrent: Torrent): MediaType? {
        val yearOfRelease = extractYearInTorrent(remoteTorrent.name)
        val mediaItems: MutableList<MediaItem> = ArrayList()
        if (yearOfRelease != null) {
            mediaItems.addAll(
                search(
                    getNormalizedTorrentStringWithSpaces(remoteTorrent.name).replace(yearOfRelease.toString().toRegex(), "").trim { it <= ' ' },
                    yearOfRelease
                )!!
            )
        } else {
            mediaItems.addAll(search(getNormalizedTorrentStringWithSpaces(remoteTorrent.name).trim { it <= ' ' })!!)
        }
        val mediaItem = mediaItems.stream().findFirst()
        return mediaItem.map(MediaItem::type).orElse(null)
    }

    private fun extractYearInTorrent(torrentName: String): Int? {
        val pattern = Pattern.compile("([0-9]{4})[^\\w]")
        val matcher = pattern.matcher(torrentName)
        while (matcher.find()) {
            // Get the group matched using group() method
            val group = matcher.group(1)
            if (group != null) {
                return group.toInt()
            }
        }
        return null
    }
}