package boat.info

import boat.torrent.TorrentHelper.urlEncode
import boat.utilities.HttpHelper
import boat.utilities.PropertiesHelper
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.Locale
import java.util.function.Consumer

@Service
class TheTVDBService @Autowired constructor(private val httpHelper: HttpHelper) {
    private val baseUrl = "https://api4.thetvdb.com/v4"
    private var token: String? = null

    private fun login(): String? {
        val apiKey = PropertiesHelper.getProperty("TVDB_APIKEY")
        if (apiKey.isNullOrBlank()) {
            println("TVDB_APIKEY not found")
            return null
        }
        val body = "{\"apikey\": \"$apiKey\"}"
        val response = httpHelper.getPage("$baseUrl/login", body)
        return try {
            val jsonRoot = JsonParser.parseString(response)
            val data = jsonRoot.asJsonObject["data"]
            val tokenElement = data.asJsonObject["token"]
            tokenElement.asString
        } catch (e: Exception) {
            println("Failed to login to TVDB: ${e.message}")
            null
        }
    }

    private fun getToken(): String? {
        if (token == null) {
            token = login()
        }
        return token
    }

    fun search(name: String?): List<MediaItem> {
        return name?.let {
            val jwtToken = getToken() ?: return emptyList()
            val url = "$baseUrl/search?q=${urlEncode(name)}"
            val headers = mapOf("Authorization" to "Bearer $jwtToken")
            parseResponsePage(httpHelper.getPage(url, null, headers))
        } ?: emptyList()
    }

    fun search(name: String?, year: Int): List<MediaItem> {
        return name?.let {
            val jwtToken = getToken() ?: return emptyList()
            val url = "$baseUrl/search?q=${urlEncode(name)}&year=$year"
            val headers = mapOf("Authorization" to "Bearer $jwtToken")
            parseResponsePage(httpHelper.getPage(url, null, headers))
        } ?: emptyList()
    }

    fun parseResponsePage(pageContent: String?): List<MediaItem>? {
        if (pageContent.isNullOrBlank()) {
            return null
        }
        val mediaItems: MutableList<MediaItem> = ArrayList()
        try {
            val jsonRoot = JsonParser.parseString(pageContent)
            if (!jsonRoot.isJsonObject) return emptyList()
            val data = jsonRoot.asJsonObject["data"]
            if (data == null || !data.isJsonArray) return emptyList()
            val jsonArray = data.asJsonArray
            jsonArray.forEach(Consumer { jsonMedia: JsonElement ->
                val jsonMediaObject = jsonMedia.asJsonObject
                
                val name = if (jsonMediaObject.has("name")) jsonMediaObject["name"].asString else null
                val type = if (jsonMediaObject.has("type")) jsonMediaObject["type"].asString else ""
                val year = if (jsonMediaObject.has("year")) {
                    val yearStr = jsonMediaObject["year"].asString
                    yearStr.substringBefore("-").toIntOrNull()
                } else null
                
                val seasonCount = if (jsonMediaObject.has("season_count")) {
                    jsonMediaObject["season_count"].asInt
                } else null

                if (name != null) {
                    val mediaItem = MediaItem(name, name, year, determineMediaType(type))
                    if (seasonCount != null) {
                        mediaItem.seasonCount = seasonCount
                    }
                    if (jsonMediaObject.has("tvdb_id")) {
                        mediaItem.id = "{tvdb-${jsonMediaObject["tvdb_id"].asString}}"
                    }
                    mediaItems.add(mediaItem)
                }
            })
        } catch (e: Exception) {
            println("Error parsing TVDB response: ${e.message}")
        }
        return mediaItems
    }

    private fun determineMediaType(typeString: String): MediaType {
        val type = typeString.lowercase(Locale.getDefault())
        return when {
            type.contains("movie") -> MediaType.Movie
            type.contains("series") || type.contains("show") || type.contains("tv") -> MediaType.Series
            else -> MediaType.Other
        }
    }
}
