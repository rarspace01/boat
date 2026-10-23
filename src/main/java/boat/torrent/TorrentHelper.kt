package boat.torrent

import boat.info.MediaItem
import org.apache.logging.log4j.util.Strings
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.stream.Collectors

object TorrentHelper {
    private val badQualityRegex: Regex =
        Regex("(telesync|telecine|hdcam|tscam|\\.cam\\.| cam |cam-rip|camrip|tsrip|hqcam|hdts|hd-ts|\\.hdtc\\.|\\.ts\\.|\\[ts\\]|pdvd|predvdrip|workprint|hd ts)")
    const val REGEX_RELEASE_GROUP = "(-[A-Za-z\\s]+)"
    private val torrentService = TorrentService(null)
    const val SIZE_UPPER_LIMIT = 15000.0
    const val SEED_RATIO_UPPER_LIMIT = 5.0

    // the extension must be a word of its own, otherwise names like "Cuvi" or "Shorts" count as media files
    val mediaFileRegex = Regex("(?<![a-z0-9])(mp4|mkv|avi|divx|ts|mpeg|divx|xvid|mov|webm|wmv|avchd|flv)\$")
    val seriesRegex = "(s[0-9]{1,2}[a-z]+)|(season)".toRegex()

    // compiled once, a search rates thousands of names; the tags are text ("5.1" must not match "5x1")
    private val releaseTagCleaners: List<Regex> by lazy {
        torrentService.getReleaseTags().filter { it.isNotBlank() }.map { "([ .-]+${Regex.escape(it)})([ .-]+|$)".toRegex() }
    }
    private val releaseTagRemovers: List<Regex> by lazy {
        torrentService.getReleaseTags().filter { it.isNotBlank() }.map { "\\s(?i)${Regex.escape(it)}\\s".toRegex() }
    }

    fun extractTorrentSizeFromString(tempTorrent: Torrent): Double {
        return extractTorrentSizeFromString(tempTorrent.size)
    }

    /**
     * The size in MB of strings like "1.5 GiB" or "700 MB", 0 if the unit or number isn't understood.
     */
    fun extractTorrentSizeFromString(sizeString: String): Double {
        val megabytesPerUnit = when {
            sizeString.contains("TiB") || sizeString.contains("TB") -> 1024.0 * 1024.0
            sizeString.contains("GiB") || sizeString.contains("GB") -> 1024.0
            sizeString.contains("MiB") || sizeString.contains("MB") -> 1.0
            else -> return 0.0
        }
        val value = sizeString.replace(sizeUnitAndTagPattern, "").trim { it <= ' ' }.toDoubleOrNull() ?: return 0.0
        return (value * megabytesPerUnit).toLong().toDouble()
    }

    private val sizeUnitAndTagPattern = "(TiB)|(TB)|(GiB)|(GB)|(MiB)|(MB)|(<.*?>)".toRegex()

    fun evaluateRating(tempTorrent: Torrent, searchName: String): Torrent {
        tempTorrent.debugRating = ""
        tempTorrent.searchRating = 0.0
        var debugAdditional = ""
        var isInstantDownload = false
        var isVerified = false
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
        val searchWords =
            listOf(*normalizedSearchNameWithSpaces.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        val torrentWords =
            listOf(*normalizedTorrentNameWithSpaces.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
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
        val mathSize = Math.min(tempTorrent.sizeInMB, SIZE_UPPER_LIMIT) / SIZE_UPPER_LIMIT
        val isRemux = torrentName.lowercase().contains("remux")
        if (isRemux) {
            debugAdditional += "♻"
        }
        val sizeRating = mathSize * (if (isRemux) 0.5 else 1.0)
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
        val videoCodec = VideoCodec.of(torrentName)
        additionalRating += (videoCodec ?: VideoCodec.DEFAULT).rating
        debugAdditional += "🎞" + (videoCodec?.name ?: "?")
        val speedRating: Double
        var speedMultiplier = 0.5
        if (tempTorrent.statsVerified) {
            speedMultiplier = 1.0
            debugAdditional += "☑️"
        }
        if (tempTorrent.cached.isNotEmpty()) {
            debugAdditional += String.format(
                "⚡(%s)",
                tempTorrent.cached.stream().map { s: String -> s[0] }.collect(Collectors.toList())
            )
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
        tempTorrent.searchRating =
            matchedScoreOfSearch * speedMultiplier * (matchedScoreOfTorrent + sizeRating + speedRating + additionalRating)
        tempTorrent.debugRating = String.format(
            "<!-- 🔍%.2f * 📶%.2f * (🔦%.2f + 📦%.2f + 🚄%.2f (%.2f) + 🧮%.2f - %s) -->",
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
            lowerCase.trim { it <= ' ' }.replace("['`´!]".toRegex(), "").replace(",".toRegex(), ".")
                .replace("\\[[A-Za-z0-9. -]*\\]".toRegex(), "")
        val regexCleaned = getRegexCleaned(trimmedAndCleaned)
        return regexCleaned.replace("[()]+".toRegex(), "").replace("\\s".toRegex(), "").replace("\\.".toRegex(), "")
    }

    private fun getRegexCleaned(inputString: String): String =
        releaseTagCleaners.fold(inputString) { cleaned, tag -> cleaned.replace(tag, ".") }

    fun getNormalizedTorrentStringWithSpaces(name: String): String {
        val lowerCase =
            name.replace(REGEX_RELEASE_GROUP.toRegex(), "").lowercase(Locale.getDefault()).replace("\"".toRegex(), "")
        return getRegexCleaned(
            lowerCase.trim { it <= ' ' }.replace("['`´!]".toRegex(), "").replace("\\[[a-z0-9. -]*\\]".toRegex(), "")
                .replace(",".toRegex(), ".")
        ).replace("[()]+".toRegex(), "").replace("\\.".toRegex(), " ").trim { it <= ' ' }
    }

    fun getNormalizedTorrentStringWithSpacesKeepCase(name: String?): String? {
        if (name == null) {
            return null
        }
        val string =
            name.replace(REGEX_RELEASE_GROUP.toRegex(), "").lowercase(Locale.getDefault()).replace("\"".toRegex(), "")
        return getRegexCleaned(string.trim { it <= ' ' }
            .replace("\\[[A-Za-z0-9. -]*\\]".toRegex(), "")).replace("\\.".toRegex(), " ").trim { it <= ' ' }
    }

    fun cleanNumberString(value: String): String {
        return value.replace(",".toRegex(), "")
    }

    /**
     * Adult releases carry "XXX" as a tag after their title. The movies "xXx" (2002, 2005, 2017) start with it, so
     * when the search itself is for "xxx", torrents named after the movie are kept.
     */
    fun isExplicitContent(torrent: Torrent, searchName: String): Boolean {
        val torrentNameLowerCased = torrent.name.lowercase(Locale.getDefault())
        if (torrentNameLowerCased.contains("porn") || torrentNameLowerCased.contains("sluts")) return true
        if (!torrentNameLowerCased.contains("xxx")) return false
        val isSearchForXxx = searchName.lowercase(Locale.getDefault()).contains(xxxWordRegex)
        return !(isSearchForXxx && torrentNameLowerCased.contains(startsWithXxxWordRegex))
    }

    private val xxxWordRegex = Regex("(?<![a-z0-9])xxx(?![a-z0-9])")
    // leading release groups like "[ETRG] " are skipped
    private val startsWithXxxWordRegex = Regex("^(?:\\W*\\[[^]]*])*\\W*xxx(?![a-z0-9])")

    private fun isBadQualityName(torrentNameLowerCased: String) =
        torrentNameLowerCased.contains(badQualityRegex)

    /**
     * Explicit content depends on the search, it is filtered by [TorrentSearchEngineService] with [isExplicitContent].
     */
    fun isValidTorrent(torrent: Torrent): Boolean {
        return !isBadQualityName(torrent.name.lowercase(Locale.getDefault())) && torrent.sizeInMB > 0 && torrent.magnetUri.isNotBlank()
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

    /**
     * Percent-encodes bytes (e.g. an info hash) for a query string. Everything except letters, digits and `-_.~` is
     * encoded as %XX. Not URLCodec, it turns a space byte into "+", which trackers don't decode, so about 7% of the
     * info hashes (the ones containing the byte 0x20) were asked for under a wrong hash.
     */
    fun urlEncode(bytes: ByteArray?): String {
        val encoded = StringBuilder()
        bytes?.forEach { byte ->
            val char = (byte.toInt() and 0xff).toChar()
            if (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in "-_.~") {
                encoded.append(char)
            } else {
                encoded.append('%').append("%02X".format(byte.toInt() and 0xff))
            }
        }
        return encoded.toString()
    }

    fun buildMagnetUriFromHash(hash: String?, torrentName: String): String {
        return "magnet:?xt=urn:btih:$hash&dn=${urlEncode(torrentName)}&tr=http://tracker.opentrackr.org:1337/announce"
    }

    fun determineTypeOfMedia(string: String?): TorrentType {
        val cleanedString = string!!.lowercase(Locale.getDefault())

        if (cleanedString.contains(mediaFileRegex)) {
            return if (cleanedString.contains(seriesRegex)) {
                TorrentType.SERIES_SHOWS
            } else //if (isMovieString(cleanedString)){
            {
                TorrentType.MOVIES
            }
        }
        return TorrentType.TRANSFER
    }

    fun prepareTorrentName(torrentName: String?): String {
        val normalizedTorrentStringWithSpaces = getNormalizedTorrentStringWithSpacesKeepCase(torrentName)
        return removeReleaseTags(normalizedTorrentStringWithSpaces)
    }

    private fun removeReleaseTags(string: String?): String =
        releaseTagRemovers.fold(string.orEmpty()) { removed, tag -> removed.replace(tag, " ") }

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
        val countMap: MutableMap<TorrentType, Int> = EnumMap(TorrentType::class.java)
        filesFromTorrent.forEach(Consumer { (_, name): TorrentFile -> countMap.compute(determineTypeOfMedia(name)) { _: TorrentType?, integer: Int? -> if (integer == null) 1 else integer + 1 } })
        if (countMap.entries.isEmpty()) return TorrentType.TRANSFER
        val maxEntry: Map.Entry<TorrentType, Int> = countMap.entries.maxBy { it.value }
        return maxEntry.key
    }

    fun getSearchNameFrom(mediaItem: MediaItem): String {
        val year = mediaItem.year
        var searchName = mediaItem.title + if (year != null) " $year" else ""
        searchName = getNormalizedTorrentStringWithSpaces(searchName).replace("['!]".toRegex(), "")
        return searchName
    }

    /**
     * Strips special characters (e.g. ":" or "-") from a title for better search results,
     * "Mikizac: Ipamiraruk - Fuvavod" becomes "Mikizac Ipamiraruk Fuvavod"
     */
    fun removeSpecialCharacters(title: String): String {
        return title.replace("['`´’]".toRegex(), "")
            .replace("[^\\p{L}\\p{N}]+".toRegex(), " ")
            .trim()
    }

    fun formatDuration(duration: Duration): String {
        return if (duration.toDays() > 0) {
            String.format(
                "D: %sdays %shrs %smin %ssec",
                duration.toDays(),
                duration.toHours() % 24,
                duration.toMinutes() % 60,
                duration.seconds % 60
            )
        } else {
            String.format(
                "D: %shrs %smin %ssec",
                duration.toHours() % 24,
                duration.toMinutes() % 60,
                duration.seconds % 60
            )
        }
    }

    fun extractTorrentName(torrent: Torrent): String {
        return if (isUsableName(torrent.name)) {
            torrent.name
        } else {
            nameFromUri(torrent.magnetUri) ?: hashFromUri(torrent.magnetUri) ?: torrent.magnetUri
        }
    }

    /**
     * Resolves a name that is safe to be used as file/folder name. Multifilehosters sometimes report the (truncated)
     * magnet uri as name, so the first usable candidate wins, falling back to the dn or hash of the magnet uri.
     */
    fun resolveDownloadName(torrent: Torrent, vararg fallbackNames: String?): String {
        val candidates = listOf(torrent.name, torrent.magnetUri) + fallbackNames
        return listOf(torrent.name, *fallbackNames).firstOrNull { isUsableName(it) }
            ?: candidates.firstNotNullOfOrNull { nameFromUri(it) }
            ?: candidates.firstNotNullOfOrNull { hashFromUri(it) }
            ?: torrent.name
    }

    fun isMagnetUriLike(name: String?): Boolean {
        if (name == null) return false
        val lowercasedName = name.trim().lowercase(Locale.ROOT)
        return lowercasedName.startsWith("magnet:") || lowercasedName.contains("xt=urn:btih:") || lowercasedName.contains("urn:btih:")
    }

    private fun isUsableName(name: String?): Boolean {
        return Strings.isNotBlank(name) && name != "null" && !isMagnetUriLike(name)
    }

    private fun nameFromUri(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        if (!isMagnetUriLike(uri)) {
            return if (uri.startsWith("http")) urlDecode(uri.substringBefore("?").substringAfterLast("/")).ifBlank { null } else null
        }
        val nameFromUri = Torrent.of(uri).torrentNameFromUri?.let { urlDecode(it) }
        return if (isUsableName(nameFromUri)) nameFromUri else null
    }

    private fun hashFromUri(uri: String?): String? {
        if (uri.isNullOrBlank() || !isMagnetUriLike(uri)) return null
        return Torrent.of(uri).infoHash
    }
}
