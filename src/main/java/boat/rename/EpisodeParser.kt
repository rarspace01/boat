package boat.rename

import boat.info.MediaItem
import boat.info.MediaType
import boat.rename.FileAttributes.decodeIfEncoded
import java.io.File

/**
 * Season and episode numbers of a file name, in all the notations releases use (S01E02, 1x02, 0102, ...).
 */
object EpisodeParser {
    val seasonPattern = "(?<=^|[^a-zA-Z])(?:S([0-9]{1,2})(?:[x._\\-\\s]?(?:E|EP)[0-9]{1,3})?|(?<![0-9])([0-9]{1,2})[x._\\-\\s]?(?:E|EP)[0-9]{1,3}(?=[^a-zA-Z0-9]|$))".toRegex(RegexOption.IGNORE_CASE)

    val seasonPattern2 = "(?:\\[|\\b)([0-9]{1,2})x([0-9]{1,2})(?:\\]|\\b)".toRegex()

    val episodePattern =
        "(?:(?<=^|[^a-zA-Z0-9])(?:Episode|Ep\\.?|E)|(?:\\s-\\s|\\s|-|\\.))([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)

    val standaloneEpisodePattern = "(?<=^|[\\s._-])(?<![0-9])([0-9]{3})(?![0-9])(?=[\\s._-]|$)".toRegex()

    val bracketPattern = "\\[.*?]".toRegex()

    val bracketedEpisodePattern = "(?i)\\[(?:S?([0-9]{1,2})[x.]([0-9]{1,3})|S([0-9]{1,2})E([0-9]{1,3}))]".toRegex()

    val dvdDirectPattern = "(?<=^|\\s)(?:dvd|disc|cd|book|volume|vol)\\s?([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)

    val seasonDirectPatternInName = "(?<=(?:Season|Day)[\\s_.-]?)([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)

    val versionPattern = "(?<=[0-9])v[0-9]+".toRegex()

    val episodeWithTitlePattern = "(?:\\s-\\s|\\s-_|_-_|\\s-\\s?|_-\\s?|\\s|^)(?:Episode|Ep\\.?|E)?\\s*([0-9]{1,3})\\s+-\\s+[a-zA-Z]".toRegex(RegexOption.IGNORE_CASE)

    val dashEpisodePattern = "(?:\\s-\\s|\\s-_|_-_|\\s-\\s?|_-\\s?)([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex()

    // S01E101 is episode 101, absolute numbering in a single season like Dragon Ball's
    val explicitSeasonEpisodePattern = "(?i)(?<![a-z0-9])S([0-9]{1,2})[\\s._-]?E([0-9]{1,4})(?![0-9])".toRegex()

    // specials attached to an episode like "Lost S01E20X01 Lost The Journey"
    val episodeSpecialPattern = "(?i)(?<![a-z0-9])S[0-9]{1,2}[\\s._-]?E[0-9]{1,4}X[0-9]{1,3}(?![0-9])".toRegex()

    // daily shows like "Dr.Phil.Show.2013.10.01.Dangerous.and.Violent.Love" have no season and episode in the name
    val airDatePattern = "(?<![0-9])(?:19|20)[0-9]{2}[._\\s-](?:0[1-9]|1[0-2])[._\\s-](?:0[1-9]|[12][0-9]|3[01])(?![0-9])".toRegex()

    // parts of a mini series like "The Hitchhikers Guide To The Galaxy - Part 1 Of 6"
    val partOfPattern = "(?i)(?<![a-z])part\\s*([0-9]{1,2})\\s*of\\s*[0-9]{1,2}(?![0-9])".toRegex()

    // "No Game No Life 12 Rule Number 10" or "Eden of the East - 01 - I've Found Myself a Prince", three digits like
    // "Duckman 101 - I, Duckman" can be season and episode
    val episodeAfterShowPattern = "^(?:\\[[^\\]]*][\\s._-]*)?[A-Za-z][^0-9]*?[\\s._-]+(?:-[\\s._-]+)?([0-9]{2})(?:[\\s._]+-)?[\\s._]+[A-Za-z]".toRegex()

    // numbers like "Tokyo Magnitude 8.0" or audio channels like "AAC 5.1" aren't episodes
    val decimalNumberPattern = "(?<![0-9.])[0-9]\\.[0-9](?![0-9.])".toRegex()

    // audio channels after the codec, also with spaces like "EAC3 2 0" or "DDP 5 1"
    val audioChannelsPattern = "(?i)(?<=aac|ac3|eac3|ddp|dd|dts|opus|flac|mp3)[\\s._-]*[0-9][\\s._][0-9](?![0-9])".toRegex()

    // channels after a resolution, source or codec tag like "1080p.5.1.BDrip" or "x264 DD+ 7.1"
    val taggedAudioChannelsPattern = ("(?i)(?<=(?:[0-9]{3,4}p|bluray|bdrip|brrip|webrip|web-dl|webdl|hdtv|dvdrip|hdrip|remux|x264|x265|h264|h265|hevc|" +
            "aac|ac3|eac3|ddp|dd|dts|dts-hd|truehd|atmos|opus|flac|mp3)\\+?[\\s._-]{1,3})[1-7][\\s._][01](?![0-9])").toRegex()

    val nonAlphanumericPattern = Regex("[^a-z0-9]+")

    val yearEpisodePattern = "(?<=^|[^a-zA-Z0-9])(?:19[0-9]{2}|20[0-2][0-9])[eE]([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)

    // season and episode glued to the title like "Shameless901.Are You There Shim"
    val gluedSeasonEpisodePattern = "^([^\\s\\[(]*?[a-zA-Z]{3})([1-9][0-9]?)([0-9]{2})(?=[\\s._-]|$)".toRegex()

    val yearSpecialPattern = "(?i)(?<=^|[^a-zA-Z0-9])(?:19[0-9]{2}|20[0-2][0-9])(?:e[0-9]{1,3})?x[0-9]{1,3}(?=[^a-zA-Z0-9]|$)".toRegex()

    val ovaMarkerPattern = "(?i)(?<![a-z])(?:NCOP|NCED|OVA|OAD)s?(?![a-z])".toRegex()

    val openingEndingMarkerPattern = "(?<![A-Za-z])(?:OP|ED)(?:[ ._]?[0-9]{1,2}(?:v[0-9])?)?(?![A-Za-z0-9])".toRegex()

    // "hak5--1718--free-virtualization-clusters" is season 17 episode 18
    val doubleDashEpisodePattern = "--([0-9]{1,2})([0-9]{2})--".toRegex()

    // files with several episodes like "Captain.Future.Folge.01-06" have no single episode
    val episodeRangeWordPattern = "(?i)(?<![a-z])(?:Folge|Episodes?|Eps?)[\\s._-]*[0-9]{1,3}-[0-9]{1,3}(?![0-9])".toRegex()

    val yearEpisodeDirectPattern = "(?<=^|[^a-zA-Z0-9])(19[0-9]{2}|20[0-2][0-9])[eExX][0-9]{1,3}(?:[xX][0-9]{1,3})?(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)

    val explicitEpisodeLookbehindPattern = "(?<=^|[^a-zA-Z0-9])(?:Episode|Ep\\.?|E)\\s*([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)

    val zeroPaddedSeasonEpisodePattern = "(?<=^|[^a-zA-Z0-9])(0[1-9][0-9]{2})(?=[^a-zA-Z0-9]|$)".toRegex()

    val fourDigitYearPattern = "\\b([1-2][0-9]{3})\\b".toRegex()

    val seasonEpisodePattern = "(?<=^|[^a-zA-Z])(?:S[0-9]{1,2}[x._\\-\\s]?(?:E|EP)|(?<![0-9])[0-9]{1,2}[eE])([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)

    val acronymEpisodePattern = "(?<=^|[^a-zA-Z0-9])([a-zA-Z]{1,4})([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)

    val dashTrailingNumberPattern = "-([0-9]{1,3})\\.".toRegex()

    val zeroPadded3DigitsPattern = Regex("(?<=^|[^0-9])0[0-9]{2}(?=[^0-9]|$)")

    /**
     * Position of an opening, ending or OVA marker like "NCOP 01", "- ED1" or "OVA 02", ignoring hashes like [ED8CC609].
     */
    fun specialMarkerIndex(name: String, casingReference: String = name): Int? {
        val nameWithoutBrackets = name.replace(bracketPattern) { " ".repeat(it.value.length) }
        val ovaMatch = ovaMarkerPattern.find(nameWithoutBrackets)
        // an all upper case name like "ED.EDD.N.EDDY" has no distinguishable OP or ED markers
        val hasLowerCase = casingReference.replace(bracketPattern, " ").any { it.isLowerCase() }
        val openingEndingMatch = if (hasLowerCase) openingEndingMarkerPattern.find(nameWithoutBrackets) else null
        return listOfNotNull(ovaMatch?.range?.first, openingEndingMatch?.range?.first).minOrNull()
    }

    /**
     * Openings, endings and OVAs are stored as "Other" extras of the show, Plex lists them there by file name. As specials
     * they'd need the special's number of TVDB, their own numbering would collide otherwise ("NCOP 01" and "OVA 01").
     */
    fun isOpeningEndingOrOva(file: File) = specialMarkerIndex(decodeIfEncoded(file.nameWithoutExtension)) != null

    fun extractSeason(file: File): Int? {
        if (isOpeningEndingOrOva(file) || airDatePattern.containsMatchIn(decodeIfEncoded(file.name))) return null
        val directSeason = extractDirectSeason(file)
        if (directSeason != null) return directSeason

        // If direct season is null, check if we can infer season from 3-digit/4-digit episode numbering (e.g. 101 -> season 1, 201 -> season 2, 0308 -> season 3)
        val rawEpisodeInfo = findRawEpisodeMatch(file)
        if (rawEpisodeInfo != null) {
            val (rawStr, num) = rawEpisodeInfo
            if (rawStr.length == 4 && rawStr.startsWith("0") && num in 101..9999) {
                val s = rawStr.substring(0, 2).toInt()
                val e = rawStr.substring(2, 4).toInt()
                if (s in 1..99 && e in 1..99) {
                    return s
                }
            }
            if (num in 101..9999 && !rawStr.startsWith("0")) {
                if (shouldDisambiguateAsSeasonEpisode(file, null, num, rawStr)) {
                    return num / 100
                }
            }
        }
        return null
    }

    private fun bracketedSeasonEpisode(fileName: String): Pair<Int, Int>? {
        val match = bracketedEpisodePattern.find(fileName) ?: return null
        val (season, episode) = match.groupValues.drop(1).filter { it.isNotEmpty() }.map { it.toInt() }
        return Pair(season, episode)
    }

    fun extractDirectSeason(file: File): Int? {
        bracketedSeasonEpisode(decodeIfEncoded(file.name))?.let { return it.first }
        doubleDashEpisodePattern.find(decodeIfEncoded(file.name))?.let { return it.groupValues[1].toInt() }
        // Check seasonPattern2 (e.g. [2x01]) first across file and parents
        var current: File? = file
        var depth = 0
        while (current != null && depth < 6) {
            val name = decodeIfEncoded(current.name)
            val match2 = seasonPattern2.find(name)
            if (match2 != null) {
                return match2.groupValues[1].toInt()
            }
            current = current.parentFile
            depth++
        }

        // Check yearEpisodeDirectPattern, seasonPattern (e.g. S01), seasonDirectPatternInName (e.g. Season 1), dvdDirectPattern
        current = file
        depth = 0
        while (current != null && depth < 6) {
            val name = decodeIfEncoded(current.name)
            val match = yearEpisodeDirectPattern.find(name)
                ?: seasonPattern.find(name)
                ?: seasonDirectPatternInName.find(name)
                ?: dvdDirectPattern.find(name)
            if (match != null) {
                val groupVal = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: match.groupValues.last()
                return groupVal.toInt()
            }
            current = current.parentFile
            depth++
        }

        return null
    }

    fun extractEpisode(file: File, mediaItem: MediaItem? = null): Int? {
        return extractEpisodeAndImpliedSeason(file, mediaItem)?.first
    }

    /**
     * Episode number plus the season implied by its notation (1x05, 0105 or a disambiguated 305), if any.
     */
    private fun extractEpisodeAndImpliedSeason(file: File, mediaItem: MediaItem?): Pair<Int, Int?>? {
        if (isOpeningEndingOrOva(file)) return null
        val fileName = decodeIfEncoded(file.name)
        val parentName = decodeIfEncoded(file.parentFile?.name ?: "")
        val grandParentName = decodeIfEncoded(file.parentFile?.parentFile?.name ?: "")
        val greatGrandParentName = decodeIfEncoded(file.parentFile?.parentFile?.parentFile?.name ?: "")

        val match2 = seasonPattern2.find(fileName)
            ?: seasonPattern2.find(parentName)
            ?: seasonPattern2.find(grandParentName)
            ?: seasonPattern2.find(greatGrandParentName)

        if (match2 != null) {
            return Pair(match2.groupValues[2].toInt(), match2.groupValues[1].toInt())
        }

        gluedSeasonEpisode(file)?.let { (_, season, episode) -> return Pair(episode, season) }
        // specials of year seasons like "MythBusters 2008X01 Shark Week Special 2" have no regular episode number
        if (yearSpecialPattern.containsMatchIn(fileName) || episodeSpecialPattern.containsMatchIn(fileName) || airDatePattern.containsMatchIn(fileName)) return null
        explicitSeasonEpisodePattern.find(fileName)?.let { return Pair(it.groupValues[2].toInt(), it.groupValues[1].toInt()) }
        doubleDashEpisodePattern.find(fileName)?.let { return Pair(it.groupValues[2].toInt(), it.groupValues[1].toInt()) }
        if (episodeRangeWordPattern.containsMatchIn(fileName)) return null
        // a bracketed episode wins over numbers in the title like "The Simpsons [20.19] Waverly Hills 9-0-2-1-D'oh"
        bracketedSeasonEpisode(fileName)?.let { (season, episode) -> return Pair(episode, season) }
        partOfPattern.find(fileName)?.let { return Pair(it.groupValues[1].toInt(), null) }
        // the number after the show, not the one in the episode's title like "No Game No Life 12 Rule Number 10"
        episodeAfterShowPattern.find(decodeIfEncoded(file.nameWithoutExtension))?.let { return Pair(it.groupValues[1].toInt(), null) }

        val rawEpisodeInfo = findRawEpisodeMatch(file) ?: return null
        val (rawStr, num) = rawEpisodeInfo

        val directSeason = extractDirectSeason(file)
        if (num in 101..9999) {
            if (rawStr.length == 4 && rawStr.startsWith("0")) {
                val s = rawStr.substring(0, 2).toInt()
                val e = rawStr.substring(2, 4).toInt()
                if (directSeason == null || directSeason == s) {
                    return Pair(e, s)
                }
            }
            if (!rawStr.startsWith("0") && shouldDisambiguateAsSeasonEpisode(file, directSeason, num, rawStr, mediaItem)) {
                return Pair(num % 100, num / 100)
            }
        }

        return Pair(num, null)
    }

    /**
     * Show, season and episode of a file name like "Shameless901.Are You There Shim", only if a folder confirms the season.
     */
    fun gluedSeasonEpisode(file: File): Triple<String, Int, Int>? {
        val match = gluedSeasonEpisodePattern.find(decodeIfEncoded(file.nameWithoutExtension)) ?: return null
        val season = match.groupValues[2].toInt()
        val episode = match.groupValues[3].toInt()
        if (episode == 0 || extractDirectSeason(file) != season) return null
        return Triple(match.groupValues[1], season, episode)
    }

    private fun findRawEpisodeMatch(file: File): Pair<String, Int>? {
        val fileName = decodeIfEncoded(file.name).replace(audioChannelsPattern, " ").replace(taggedAudioChannelsPattern, " ").replace(decimalNumberPattern, " ")
        val parentName = decodeIfEncoded(file.parentFile?.name ?: "")
        val grandParentName = decodeIfEncoded(file.parentFile?.parentFile?.name ?: "")
        val greatGrandParentName = decodeIfEncoded(file.parentFile?.parentFile?.parentFile?.name ?: "")

        val excludedAcronyms = setOf(
            "av1", "x264", "h264", "x265", "h265", "hevc", "avc", "xvid", "dvd", "disc", "cd", "bd", "eac", "eac3", "ddp", "dd",
            "part", "pt", "vol", "v", "ch", "aac", "mp3", "mp4", "ac3", "fps", "kbps", "mbps",
            "s", "se", "season", "dvdrip", "bdrip", "brrip", "webrip", "webdl", "hdtv", "pdtv"
        )
        val replaced = fileName.replace(bracketPattern, " ").replace(versionPattern, "").replace("_", " ")

        val rawBaseName = fileName.substringBeforeLast(".").replace(bracketPattern, " ").replace(versionPattern, "").trim()
        val acronymMatch = acronymEpisodePattern.findAll(rawBaseName).firstOrNull { m ->
            val prefix = m.groupValues[1].lowercase()
            val full = m.value.lowercase()
            !excludedAcronyms.contains(prefix) && !excludedAcronyms.contains(full) && prefix != "s" && prefix != "se"
        }

        val nonEpisodePrefix = "(?i)(?:season|series|disc|disk|cd|dvd|bd|volume|vol|book|chapter|part|pt|day)\\s*$".toRegex()
        val candidatesInOrder = episodePattern.findAll(replaced)
            .map { it to replaced }
            .plus(episodePattern.findAll(fileName).map { it to fileName })
            .filter { (m, text) ->
                val textBefore = text.substring(0, m.range.first).trim()
                !nonEpisodePrefix.containsMatchIn(textBefore)
            }.toList()

        val parentTokens = listOf(parentName, grandParentName, greatGrandParentName)
            .flatMap { it.split(nonAlphanumericPattern) }
            .filter { it.isNotBlank() }
            .toSet()

        val nonParentCandidates = candidatesInOrder.filter { (m, _) ->
            val numStr = m.groupValues[m.groupValues.size - 1]
            !parentTokens.contains(numStr)
        }

        val firstValidEpisodeMatch = if (nonParentCandidates.isNotEmpty()) {
            nonParentCandidates.first().first
        } else {
            candidatesInOrder.firstOrNull()?.first
        }

        val match = seasonEpisodePattern.find(fileName)
            ?: yearEpisodePattern.find(fileName)
            ?: seasonEpisodePattern.find(parentName)
            ?: yearEpisodePattern.find(parentName)
            ?: seasonEpisodePattern.find(grandParentName)
            ?: seasonEpisodePattern.find(greatGrandParentName)
            ?: explicitEpisodeLookbehindPattern.find(fileName)
            ?: episodeWithTitlePattern.find(fileName)
            ?: episodeWithTitlePattern.find(replaced)
            ?: dashEpisodePattern.find(fileName)
            ?: dashEpisodePattern.find(replaced)
            ?: dashTrailingNumberPattern.find(fileName)
            ?: acronymMatch
            ?: zeroPaddedSeasonEpisodePattern.find(fileName)
            ?: standaloneEpisodePattern.find(fileName)?.let { if (fourDigitYearPattern.find(it.value) == null) it else null }
            ?: firstValidEpisodeMatch
            ?: explicitEpisodeLookbehindPattern.find(parentName)
            ?: explicitEpisodeLookbehindPattern.find(grandParentName)

        if (match != null) {
            val groupIdx = if (match.groupValues.size > 1) match.groupValues.size - 1 else 0
            val str = match.groupValues[groupIdx]
            val num = str.toIntOrNull()
            if (num != null) {
                return Pair(str, num)
            }
        }
        return null
    }

    private fun shouldDisambiguateAsSeasonEpisode(
        file: File,
        knownSeason: Int?,
        num: Int,
        rawStr: String,
        mediaItem: MediaItem? = null
    ): Boolean {
        if (rawStr.startsWith("0")) return false

        // Tier 1: Known season context matching (Folder or path has Season X, Book X, etc.)
        if (knownSeason != null && knownSeason > 0) {
            return (num / 100 == knownSeason) && (num % 100 in 1..99)
        }

        // Tier 2: Sibling file inspection if parent directory exists on disk
        val siblings = file.parentFile?.listFiles()?.filter { it.isFile && it.name != file.name } ?: emptyList()
        if (siblings.isNotEmpty()) {
            val hasZeroPadded3Digits = siblings.any { s ->
                val sName = decodeIfEncoded(s.name)
                zeroPadded3DigitsPattern.containsMatchIn(sName)
            }
            if (hasZeroPadded3Digits) return false

            val siblingNumbers = siblings.mapNotNull { s -> findRawEpisodeMatch(s)?.second }
            val hasUnder100 = siblingNumbers.any { it in 1..99 }
            val has100Plus = siblingNumbers.any { it >= 100 } || num >= 100
            if (hasUnder100 && has100Plus) {
                // Continuous episodes from 1 to 100+ -> absolute episode numbering
                return false
            }

            if (siblingNumbers.isNotEmpty() && siblingNumbers.all { it >= 100 } && !hasUnder100) {
                return (num % 100) in 1..99
            }
        }

        // Tier 3 / Tier 4: Metadata verification (TMDB / TVDB) if available (optional)
        if (mediaItem != null && mediaItem.type == MediaType.Series && mediaItem.seasonCount != null) {
            val inferredSeason = num / 100
            if (inferredSeason in 1..(mediaItem.seasonCount ?: 1) && (num % 100) in 1..99) {
                return true
            }
        }

        return false
    }

    /**
     * Detects season and episode of a file belonging to [mediaItem]. Movies have neither. For series without an
     * explicit season, the season implied by the episode number (e.g. 305 -> S03E05) is used, otherwise season 1.
     */
    fun extractSeasonAndEpisode(file: File, mediaItem: MediaItem): Pair<Int?, Int?> {
        if (mediaItem.type == MediaType.Movie) return Pair(null, null)
        val episodeInfo = extractEpisodeAndImpliedSeason(file, mediaItem)
        val episode = episodeInfo?.first
        var season = extractSeason(file)
        if (season == null && episode != null && mediaItem.type == MediaType.Series) {
            season = episodeInfo.second ?: 1
        }
        return Pair(season, episode)
    }
}
