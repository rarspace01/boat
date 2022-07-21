package boat.info

import boat.torrent.Torrent
import boat.torrent.TorrentHelper.getNormalizedTorrentStringWithSpaces
import boat.torrent.TorrentHelper.urlEncode
import boat.utilities.HttpHelper
import boat.utilities.PropertiesHelper
import com.google.gson.JsonElement
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
class TheFilmDataBaseService @Autowired constructor(private val httpHelper: HttpHelper) {
    private val baseUrl = String.format(
        "https://api.themoviedb.org/3/search/multi?api_key=%s",
        PropertiesHelper.getProperty("TFDB_APIKEY")
    )

    fun search(name: String?): List<MediaItem>? {
        val page = httpHelper.getPage(String.format("%s&query=%s", baseUrl, urlEncode(name!!)))
        return parseResponsePage(page)
    }

    fun search(name: String?, year: Int): List<MediaItem>? {
        val urlString = String.format("%s&query=%s&year=%d", baseUrl, urlEncode(name!!), year)
        val page = httpHelper.getPage(urlString)
        return parseResponsePage(page)
    }

    fun parseResponsePage(pageContent: String?): List<MediaItem>? {
        if (pageContent == null) {
            return null
        }
        val mediaItems: MutableList<MediaItem> = ArrayList()
        val jsonRoot = JsonParser.parseString(pageContent)
        val results = jsonRoot.asJsonObject["results"]
        val jsonArray = results.asJsonArray
        jsonArray.forEach(Consumer { jsonMedia: JsonElement ->
            val jsonMediaObject = jsonMedia.asJsonObject
            val mediaTypeString = jsonMediaObject["media_type"].asString.lowercase(Locale.getDefault())
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
            try {
                val calendar = Calendar.getInstance()
                val releaseDate = jsonMediaObject["release_date"]
                val firstAirDate = jsonMediaObject["first_air_date"]
                val releaseDateString = if (releaseDate != null) releaseDate.asString else firstAirDate?.asString
                if (releaseDateString != null) {
                    calendar.time = SimpleDateFormat("yyyy-MM-dd").parse(releaseDateString)
                    year = calendar[Calendar.YEAR]
                }
            } catch (ignored: ParseException) {
            }
            if (title != null || originalTitle != null) {
                mediaItems.add(MediaItem(title!!, originalTitle, year, mediaType))
            }
        })
        return mediaItems
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