package boat.torrent

import boat.info.MediaItem
import org.apache.commons.codec.net.URLCodec
import org.apache.logging.log4j.util.Strings
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import java.time.Duration
import java.util.EnumMap
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.stream.Collectors

object TorrentHelper {
    const val REGEX_RELEASE_GROUP = "(-[A-Za-z\\s]+)"
    private val torrentService = TorrentService()
    const val SIZE_UPPER_LIMIT = 15000.0
    const val SEED_RATIO_UPPER_LIMIT = 5.0

    val mediaFileRegex = Regex("[.]?(mp4|mkv|avi|divx|ts|mpeg|divx|xvid|mov|webm|wmv|avchd|flv)\$")
    val seriesRegex = "(s[0-9]{1,2}[a-z]+)|(season)".toRegex()

    val TAG_REGEX = "(" + listOfReleaseTagsPiped() + ")"
    private fun listOfReleaseTagsPiped(): String {
        return torrentService.getReleaseTags().joinToString("($|[ .-]+)|[ .]")
    }

    fun extractTorrentSizeFromString(tempTorrent: Torrent): Double {
        var torrentSize: Long = 0
        try {
            if (tempTorrent.size.contains("GiB") || tempTorrent.size.contains("GB")) {
                torrentSize = (trimSizeStringToValue(tempTorrent).toDouble() * 1024).toLong()
            } else if (tempTorrent.size.contains("MiB") || tempTorrent.size.contains("MB")) {
                torrentSize = trimSizeStringToValue(tempTorrent).toDouble().toLong()
            }
        } catch (ignored: Exception) {
        }
        return torrentSize.toDouble()
    }

    fun extractTorrentSizeFromString(sizeString: String): Double {
        var torrentSize: Long = 0
        try {
            if (sizeString.contains("GiB") || sizeString.contains("GB")) {
                torrentSize = (trimSizeStringToValue(sizeString).toDouble() * 1024).toLong()
            } else if (sizeString.contains("MiB") || sizeString.contains("MB")) {
                torrentSize = trimSizeStringToValue(sizeString).toDouble().toLong()
            }
        } catch (ignored: Exception) {
        }
        return torrentSize.toDouble()
    }

    private fun trimSizeStringToValue(tempTorrent: Torrent): String {
        return tempTorrent.size.replace("(GiB)|(GB)|(MiB)|(MB)|(<.*?>)".toRegex(), "").trim { it <= ' ' }
    }

    private fun trimSizeStringToValue(stringValue: String): String {
        return stringValue.replace("(GiB)|(GB)|(MiB)|(MB)|(<.*?>)".toRegex(), "").trim { it <= ' ' }
    }

    fun evaluateRating(tempTorrent: Torrent, searchName: String): Torrent {
        tempTorrent.debugRating = ""
        tempTorrent.searchRating = 0.0
        var debugAdditional = ""
        var torrentName = tempTorrent.name
        var additionalRating = 0.0
        if (torrentName.isBlank()) {
            torrentName = ""
        }
        val normalizedTorrentName = getNormalizedTorrentString(torrentName)
        val normalizedTorrentNameWithSpaces = getNormalizedTorrentStringWithSpaces(torrentName)
        val normalizedSearchNameWithSpaces = getNormalizedTorrentStringWithSpaces(searchName)
        val normalizedSearchName = getNormalizedTorrentString(searchName)

        //check individual words
        val searchWords = listOf(*normalizedSearchNameWithSpaces.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        val torrentWords = listOf(*normalizedTorrentNameWithSpaces.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        val torrentWordCount = torrentWords.size
        val matches = AtomicInteger()
        searchWords.forEach(Consumer { searchWord: String? ->
            if (normalizedTorrentName.contains(searchWord!!)) {
                matches.getAndIncrement()
            }
        })
        val matchedScoreOfTorrent = matches.get().toDouble() / torrentWordCount.toDouble()
        val matchedScoreOfSearch = matches.get().toDouble() / searchWords.size.toDouble()

        // bonus for year in torrentName
        if (normalizedTorrentNameWithSpaces.matches(".*[1-2][09][0-9][0-9].*".toRegex())) {
            additionalRating += 0.5
            debugAdditional += "📅"
        }

        // calc first range
        val sizeRating = Math.min(tempTorrent.sizeInMB, SIZE_UPPER_LIMIT) / SIZE_UPPER_LIMIT
        // calculate seeder ratio
        val seedRatio: Double
        seedRatio = if (tempTorrent.leecher > 0) {
            tempTorrent.seeder.toDouble() / tempTorrent.leecher.toDouble()
        } else {
            tempTorrent.seeder.toDouble()
        }
        val seedRatioOptimized: Double
        seedRatioOptimized = if (tempTorrent.seeder >= 1 && tempTorrent.seeder <= 3) {
            1.0 / Math.max(tempTorrent.leecher, 1)
        } else {
            seedRatio
        }
        // if movie or Series patter +1
        val name = tempTorrent.name
        val typeOfMedia = determineTypeOfMedia(prepareTorrentName(name))
        if (TorrentType.MOVIES == typeOfMedia || TorrentType.SERIES_SHOWS == typeOfMedia) {
            additionalRating += 1.0
            debugAdditional += "🎬"
        }
        if (tempTorrent.isVerified) {
            additionalRating += 0.25
            debugAdditional += "✅"
        }
        val speedRating: Double
        var speedMultiplier = 0.5
        if (tempTorrent.statsVerified) {
            speedMultiplier = 1.0
            debugAdditional += "☑️"
        }
        if (tempTorrent.cached.isNotEmpty()) {
            debugAdditional += String.format("⚡(%s)", tempTorrent.cached.stream().map { s: String -> s[0] }.collect(Collectors.toList()))
            speedRating = 2.0
            speedMultiplier = 1.0
        } else if (seedRatio > 1.0) {
            speedRating = seedRatioOptimized.coerceAtMost(SEED_RATIO_UPPER_LIMIT) / SEED_RATIO_UPPER_LIMIT
        } else if (tempTorrent.seeder == 1) {
            speedRating = seedRatioOptimized / 10.0
            speedMultiplier = speedRating
        } else {
            speedRating = seedRatioOptimized
        }

        // searchRatingNew Calc
        tempTorrent.searchRating = matchedScoreOfSearch * speedMultiplier * (matchedScoreOfTorrent + sizeRating + speedRating + additionalRating)
        tempTorrent.debugRating = String.format(
            "🔍%.2f * 📶%.2f * (🔦%.2f + 📦%.2f + 🚄%.2f (%.2f) + 🧮%.2f - %s)",
            matchedScoreOfSearch,
            speedMultiplier,
            matchedScoreOfTorrent,
            sizeRating,
            speedRating,
            seedRatioOptimized,
            additionalRating,
            debugAdditional
        )
        return tempTorrent
    }

    fun getNormalizedTorrentString(name: String): String {
        val lowerCase = name.replace(REGEX_RELEASE_GROUP.toRegex(), "").lowercase(Locale.getDefault())
        val trimmedAndCleaned =
            lowerCase.trim { it <= ' ' }.replace("['`´!]".toRegex(), "").replace(",".toRegex(), ".").replace("\\[[A-Za-z0-9. -]*\\]".toRegex(), "")
        val regexCleaned = getRegexCleaned(trimmedAndCleaned)
        return regexCleaned.replace("[()]+".toRegex(), "").replace("\\s".toRegex(), "").replace("\\.".toRegex(), "")
    }

    private fun getRegexCleaned(inputString: String): String {
        val workString = arrayOf(inputString)
        torrentService.getReleaseTags().forEach(Consumer { tag: String ->
            workString[0] = workString[0].replace(
                "([ .-]+$tag)([ .-]+|$)".toRegex(), "."
            )
        })
        return workString[0]
    }

    fun getNormalizedTorrentStringWithSpaces(name: String): String {
        val lowerCase = name.replace(REGEX_RELEASE_GROUP.toRegex(), "").lowercase(Locale.getDefault()).replace("\"".toRegex(), "")
        return getRegexCleaned(lowerCase.trim { it <= ' ' }.replace("['`´!]".toRegex(), "").replace("\\[[a-z0-9. -]*\\]".toRegex(), "")
            .replace(",".toRegex(), ".")).replace("[()]+".toRegex(), "").replace("\\.".toRegex(), " ").trim { it <= ' ' }
    }

    fun getNormalizedTorrentStringWithSpacesKeepCase(name: String?): String? {
        if (name == null) {
            return null
        }
        val string = name.replace(REGEX_RELEASE_GROUP.toRegex(), "").lowercase(Locale.getDefault()).replace("\"".toRegex(), "")
        return getRegexCleaned(string.trim { it <= ' ' }.replace("\\[[A-Za-z0-9. -]*\\]".toRegex(), "")).replace("\\.".toRegex(), " ").trim { it <= ' ' }
    }

    fun cleanNumberString(value: String): String {
        return value.replace(",".toRegex(), "")
    }

    fun isBlocklisted(torrent: Torrent): Boolean {
        val torrentNameLowerCased = torrent.name.lowercase(Locale.getDefault())
        return isBadQualityName(torrentNameLowerCased) || isExplicitContent(torrentNameLowerCased)
    }

    private fun isExplicitContent(torrentNameLowerCased: String): Boolean {
        return torrentNameLowerCased.contains("xxx") || torrentNameLowerCased.contains("porn") || torrentNameLowerCased.contains("sluts")
    }

    private fun isBadQualityName(torrentNameLowerCased: String) =
        torrentNameLowerCased.contains("telesync") || torrentNameLowerCased.contains("telecine") || torrentNameLowerCased.contains("hdcam") || torrentNameLowerCased.contains(
            "tscam"
        ) || torrentNameLowerCased.contains(".cam.") || torrentNameLowerCased.contains(" cam ") || torrentNameLowerCased.contains("cam-rip") || torrentNameLowerCased.contains(
            "camrip"
        ) || torrentNameLowerCased.contains("tsrip") || torrentNameLowerCased.contains(".hdcam.") || torrentNameLowerCased.contains("hqcam") || torrentNameLowerCased.contains("hdts") || torrentNameLowerCased.contains(
            " hd-ts"
        ) || torrentNameLowerCased.contains(".hd-ts") || torrentNameLowerCased.contains(".hdtc.") || torrentNameLowerCased.contains(".ts.") || torrentNameLowerCased.contains(
            "[ts]"
        ) || torrentNameLowerCased.contains("pdvd") || torrentNameLowerCased.contains("predvdrip") || torrentNameLowerCased.contains("workprint") || torrentNameLowerCased.contains(".hdts.")

    fun isValidTorrent(torrent: Torrent): Boolean {
        return !isBlocklisted(torrent) && torrent.sizeInMB > 0 && torrent.magnetUri.isNotBlank()
    }

    fun urlEncode(string: String): String {
        return try {
            URLEncoder.encode(string, StandardCharsets.UTF_8.toString())
        } catch (e: UnsupportedEncodingException) {
            string
        }
    }

    fun urlDecode(string: String): String {
        return try {
            URLDecoder.decode(string, StandardCharsets.UTF_8.toString())
        } catch (e: UnsupportedEncodingException) {
            string
        }
    }

    fun urlEncode(string: ByteArray?): String {
        return String(URLCodec().encode(string))
    }

    fun buildMagnetUriFromHash(hash: String?, torrentName: String): String {
        return "magnet:?xt=urn:btih:$hash&dn=${urlEncode(torrentName)}&tr=http://tracker.opentrackr.org:1337/announce"
    }

    fun determineTypeOfMedia(string: String?): TorrentType {
        val cleanedString = string!!.lowercase(Locale.getDefault())

        if(cleanedString.matches(mediaFileRegex)) {
            if (cleanedString.matches(seriesRegex)) {
                return TorrentType.SERIES_SHOWS
            } else if (isMovieString(cleanedString)) {
                return TorrentType.MOVIES
            }
        }
        return TorrentType.TRANSFER
    }

    fun isMovieString(string: String): Boolean {
        return string.matches(".*([xXhH]26[4-5]|[xX][vV][iI][dD]|[1-2][0-9]{3}[^0-9p\\/M\\@]*).*".toRegex())
    }

    fun prepareTorrentName(torrentName: String?): String {
        val normalizedTorrentStringWithSpaces = getNormalizedTorrentStringWithSpacesKeepCase(torrentName)
        return removeReleaseTags(normalizedTorrentStringWithSpaces)
    }

    private fun removeReleaseTags(string: String?): String {
        val releaseTagsRemoved = StringBuilder(string)
        torrentService.getReleaseTags().forEach(Consumer { tag: String ->
            val temporaryString = releaseTagsRemoved.toString().replace("\\s(?i)$tag\\s".toRegex(), " ")
            releaseTagsRemoved.setLength(0)
            releaseTagsRemoved.append(temporaryString)
        })
        return releaseTagsRemoved.toString()
    }

    fun humanReadableByteCountBinary(bytes: Long): String {
        val absB = if (bytes == Long.MIN_VALUE) Long.MAX_VALUE else Math.abs(bytes)
        if (absB < 1024) {
            return "$bytes B"
        }
        var value = absB
        val ci: CharacterIterator = StringCharacterIterator("KMGTPE")
        var i = 40
        while (i >= 0 && absB > 0xfffccccccccccccL shr i) {
            value = value shr 10
            ci.next()
            i -= 10
        }
        value *= java.lang.Long.signum(bytes).toLong()
        return String.format("%.2f %ciB", value / 1024.0, ci.current())
    }

    fun determineTypeOfMedia(filesFromTorrent: List<TorrentFile>): TorrentType {
        val countMap: MutableMap<TorrentType, Int> = EnumMap(boat.torrent.TorrentType::class.java)
        filesFromTorrent.forEach(Consumer { (_, name): TorrentFile -> countMap.compute(determineTypeOfMedia(name)) { _: TorrentType?, integer: Int? -> if (integer == null) 1 else integer + 1 } })
        if (countMap.entries.size == 0) return TorrentType.TRANSFER
        val maxEntry: Map.Entry<TorrentType, Int> = countMap.entries.maxBy { it.value }
        return maxEntry.key
    }

    fun getSearchNameFrom(mediaItem: MediaItem): String {
        val year = mediaItem.year
        var searchName = mediaItem.title + if (year != null) " $year" else ""
        searchName = getNormalizedTorrentStringWithSpaces(searchName).replace("['!]".toRegex(), "")
        return searchName
    }

    fun formatDuration(duration: Duration): String {
        return if (duration.toDays() > 0) {
            String.format(
                "D: %sdays %shrs %smin %ssec", duration.toDays(), duration.toHours() % 24, duration.toMinutes() % 60, duration.seconds % 60
            )
        } else {
            String.format("D: %shrs %smin %ssec", duration.toHours() % 24, duration.toMinutes() % 60, duration.seconds % 60)
        }
    }

    fun extractTorrentName(torrent: Torrent): String {
        return if (Strings.isNotEmpty(torrent.name) && torrent.name != "null") {
            torrent.name
        } else {
            val torrentNameFromUri = torrent.torrentNameFromUri
            if (torrentNameFromUri != null) urlDecode(torrentNameFromUri) else torrent.magnetUri
        }
    }
}
