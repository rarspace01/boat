package boat.info

import boat.torrent.TorrentHelper.urlEncode
import boat.utilities.HttpHelper
import boat.utilities.LoggerDelegate
import boat.utilities.PropertiesHelper
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.Locale
import java.util.function.Consumer

@Service
class TheTVDBService @Autowired constructor(private val httpHelper: HttpHelper) {
    private val logger by LoggerDelegate()
    private val baseUrl = "https://api4.thetvdb.com/v4"
    private var token: String? = null

    private fun login(): String? {
        val apiKey = PropertiesHelper.getProperty("TVDB_APIKEY")
        if (apiKey.isNullOrBlank()) {
            logger.warn("TVDB_APIKEY not found")
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
            logger.warn("Failed to login to TVDB: {}", e.message)
            null
        }
    }

    @Synchronized
    private fun resetToken() {
        token = null
    }

    // the media proxy cache refresh searches in parallel, they share one login
    @Synchronized
    private fun getToken(): String? {
        if (token == null) {
            token = login()
        }
        return token
    }

    /**
     * Null if TVDB gave no usable answer (login or search failed), which is something else than no results. Without
     * an api key TVDB is not used, that is no failure.
     */
    fun search(name: String?): List<MediaItem>? {
        return name?.let {
            if (PropertiesHelper.getProperty("TVDB_APIKEY").isNullOrBlank()) return emptyList()
            val jwtToken = getToken() ?: return null
            val headers = mapOf("Authorization" to "Bearer $jwtToken")
            val queries = mutableListOf(name)
            if (name.matches("^[a-zA-Z]{2,6}$".toRegex())) {
                val dotted = name.toCharArray().joinToString(".")
                queries.add(dotted)
                queries.add("$dotted.")
            } else if (name.contains(".")) {
                val withoutDots = name.replace(".", "")
                if (withoutDots.isNotBlank() && withoutDots != name) {
                    queries.add(withoutDots)
                }
            }
            val results = mutableListOf<MediaItem>()
            for (q in queries.distinct()) {
                val url = "$baseUrl/search?q=${urlEncode(q)}&type=series"
                val items = parseResponsePage(httpHelper.getPage(url, null, headers))
                if (items == null) {
                    // the token might have expired, login again next time
                    resetToken()
                    return null
                }
                results.addAll(items)
            }
            results.distinctBy { (it.id ?: "") + it.title + it.year }
        } ?: emptyList()
    }

    fun search(name: String?, year: Int): List<MediaItem>? {
        return search(name)
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
                parseMediaObject(jsonMedia.asJsonObject)?.let { mediaItems.add(it) }
            })
        } catch (e: Exception) {
            logger.warn("Error parsing TVDB response: {}", e.message)
        }
        return mediaItems
    }

    fun findById(id: String): MediaItem? {
        val jwtToken = getToken() ?: return null
        val headers = mapOf("Authorization" to "Bearer $jwtToken")
        val url = "$baseUrl/series/${urlEncode(id)}/extended?meta=translations&short=true"
        return parseSeriesPage(httpHelper.getPage(url, null, headers))
    }

    /**
     * Parses the /series/{id}/extended response. Its field names differ from the search
     * response, so they're mapped onto the search shape to reuse the title extraction.
     */
    fun parseSeriesPage(pageContent: String?): MediaItem? {
        if (pageContent.isNullOrBlank()) {
            return null
        }
        return try {
            val jsonRoot = JsonParser.parseString(pageContent)
            if (!jsonRoot.isJsonObject) return null
            val data = jsonRoot.asJsonObject["data"]
            if (data == null || !data.isJsonObject) return null
            val series = data.asJsonObject
            val mapped = JsonObject()
            series["id"]?.let { mapped.add("tvdb_id", it) }
            series["name"]?.let { mapped.add("name", it) }
            mapped.addProperty("type", "series")
            (series["year"] ?: series["firstAired"])?.let { mapped.add("year", it) }
            series["originalLanguage"]?.let { mapped.add("primary_language", it) }
            series["originalCountry"]?.let { mapped.add("country", it) }
            series["aliases"]?.let { mapped.add("aliases", it) }
            officialSeasonCount(series["seasons"])?.let { mapped.addProperty("season_count", it) }
            val translations = series["translations"]
            if (translations != null && translations.isJsonObject) {
                translations.asJsonObject["nameTranslations"]?.let { mapped.add("translations", it) }
            }
            parseMediaObject(mapped)
        } catch (e: Exception) {
            logger.warn("Error parsing TVDB series response: {}", e.message)
            null
        }
    }

    /** The number of the last official season, the specials (season 0), DVD and absolute orders don't count. */
    private fun officialSeasonCount(seasons: JsonElement?): Int? {
        if (seasons == null || !seasons.isJsonArray) return null
        return seasons.asJsonArray.mapNotNull { element ->
            val season = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val isOfficial = season["type"]?.takeIf { it.isJsonObject }?.asJsonObject?.get("type")?.takeIf { !it.isJsonNull }?.asString == "official"
            season["number"]?.takeIf { isOfficial && !it.isJsonNull }?.asInt?.takeIf { it > 0 }
        }.maxOrNull()
    }

    private fun parseMediaObject(jsonMediaObject: JsonObject): MediaItem? {
        val name = if (jsonMediaObject.has("name") && !jsonMediaObject["name"].isJsonNull) jsonMediaObject["name"].asString else null
        val type = if (jsonMediaObject.has("type")) jsonMediaObject["type"].asString else ""
        val year = if (jsonMediaObject.has("year") && !jsonMediaObject["year"].isJsonNull) {
            val yearStr = jsonMediaObject["year"].asString
            yearStr.substringBefore("-").toIntOrNull()
        } else null

        val seasonCount = if (jsonMediaObject.has("season_count")) {
            jsonMediaObject["season_count"].asInt
        } else null

        if (name == null) return null
        val titleToUse = extractTitle(jsonMediaObject, name)
        val mediaItem = MediaItem(titleToUse, name, year, determineMediaType(type))
        if (seasonCount != null) {
            mediaItem.seasonCount = seasonCount
        }
        if (jsonMediaObject.has("tvdb_id")) {
            mediaItem.id = "{tvdb-${jsonMediaObject["tvdb_id"].asString}}"
        }
        return mediaItem
    }

    private fun isOriginallyGerman(jsonMediaObject: JsonObject): Boolean {
        val germanLangCodes = setOf("deu", "ger", "de", "german", "deutsch")
        val germanCountryCodes = setOf("deu", "de", "ger", "deutschland", "germany", "aut", "at", "austria", "che", "ch", "switzerland")

        for (key in listOf("primary_language", "primaryLanguage", "original_language", "originalLanguage", "language")) {
            if (jsonMediaObject.has(key) && !jsonMediaObject[key].isJsonNull) {
                val lang = jsonMediaObject[key].asString.trim().lowercase(Locale.getDefault())
                if (lang in germanLangCodes) return true
            }
        }

        for (key in listOf("primary_country", "primaryCountry", "country", "original_country", "originalCountry")) {
            if (jsonMediaObject.has(key) && !jsonMediaObject[key].isJsonNull) {
                val country = jsonMediaObject[key].asString.trim().lowercase(Locale.getDefault())
                if (country in germanCountryCodes) return true
            }
        }

        return false
    }

    private fun extractTitle(jsonMediaObject: JsonObject, primaryName: String): String {
        if (isOriginallyGerman(jsonMediaObject)) {
            val germanName = extractGermanName(jsonMediaObject, primaryName)
            if (!germanName.isNullOrBlank()) {
                return germanName
            }
        }
        return extractEnglishOrSecondaryName(jsonMediaObject, primaryName)
    }

    private fun extractGermanName(jsonMediaObject: JsonObject, primaryName: String): String? {
        val germanLangKeys = listOf("deu", "de", "ger", "german", "deutsch")

        // If translations contains German
        if (jsonMediaObject.has("translations") && !jsonMediaObject["translations"].isJsonNull) {
            val translations = jsonMediaObject["translations"]
            if (translations.isJsonObject) {
                val transObj = translations.asJsonObject
                for (lang in germanLangKeys) {
                    if (transObj.has(lang) && !transObj[lang].isJsonNull) {
                        val candidate = if (transObj[lang].isJsonObject) {
                            transObj[lang].asJsonObject.get("name")?.asString
                        } else {
                            transObj[lang].asString
                        }
                        if (!candidate.isNullOrBlank()) return candidate
                    }
                }
            } else if (translations.isJsonArray) {
                val transArr = translations.asJsonArray
                for (item in transArr) {
                    if (item.isJsonObject) {
                        val itemObj = item.asJsonObject
                        val lang = itemObj.get("language")?.asString?.lowercase(Locale.getDefault())
                        if (lang in germanLangKeys && itemObj.has("name") && !itemObj["name"].isJsonNull) {
                            val candidate = itemObj["name"].asString
                            if (candidate.isNotBlank()) return candidate
                        }
                    }
                }
            }
        }

        // If primaryName itself is non-empty, use primaryName
        if (primaryName.isNotBlank()) {
            return primaryName
        }

        return null
    }

    private fun extractEnglishOrSecondaryName(jsonMediaObject: JsonObject, primaryName: String): String {
        for (key in listOf("secondary_name", "secondaryName", "secondaryTitle", "name_translated", "english_name")) {
            if (jsonMediaObject.has(key) && !jsonMediaObject[key].isJsonNull) {
                val candidate = jsonMediaObject[key].asString
                if (candidate.isNotBlank()) return candidate
            }
        }

        if (jsonMediaObject.has("translations") && !jsonMediaObject["translations"].isJsonNull) {
            val translations = jsonMediaObject["translations"]
            if (translations.isJsonObject) {
                val transObj = translations.asJsonObject
                for (lang in listOf("eng", "en", "english", "usa", "gbr")) {
                    if (transObj.has(lang) && !transObj[lang].isJsonNull) {
                        val candidate = if (transObj[lang].isJsonObject) {
                            transObj[lang].asJsonObject.get("name")?.asString
                        } else {
                            transObj[lang].asString
                        }
                        if (!candidate.isNullOrBlank()) return candidate
                    }
                }
            } else if (translations.isJsonArray) {
                val transArr = translations.asJsonArray
                for (item in transArr) {
                    if (item.isJsonObject) {
                        val itemObj = item.asJsonObject
                        val lang = itemObj.get("language")?.asString
                        if (lang in listOf("eng", "en", "english") && itemObj.has("name") && !itemObj["name"].isJsonNull) {
                            val candidate = itemObj["name"].asString
                            if (candidate.isNotBlank()) return candidate
                        }
                    }
                }
            }
        }

        val isPrimaryAscii = primaryName.all { it.code in 32..126 }
        if (!isPrimaryAscii && jsonMediaObject.has("aliases") && !jsonMediaObject["aliases"].isJsonNull) {
            val aliases = jsonMediaObject["aliases"]
            if (aliases.isJsonArray) {
                for (alias in aliases.asJsonArray) {
                    val aliasStr = if (alias.isJsonObject) alias.asJsonObject.get("name")?.asString ?: "" else alias.asString
                    if (aliasStr.isNotBlank() && aliasStr.all { it.code in 32..126 }) {
                        return aliasStr
                    }
                }
            }
        }

        return primaryName
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
