package boat.rename

import boat.info.MediaProxyService
import boat.rename.EpisodeParser.bracketPattern
import boat.rename.EpisodeParser.bracketedEpisodePattern
import boat.rename.EpisodeParser.dashEpisodePattern
import boat.rename.EpisodeParser.doubleDashEpisodePattern
import boat.rename.EpisodeParser.episodeWithTitlePattern
import boat.rename.EpisodeParser.explicitEpisodeLookbehindPattern
import boat.rename.EpisodeParser.gluedSeasonEpisode
import boat.rename.EpisodeParser.isOpeningEndingOrOva
import boat.rename.EpisodeParser.nonAlphanumericPattern
import boat.rename.EpisodeParser.seasonEpisodePattern
import boat.rename.EpisodeParser.seasonPattern
import boat.rename.EpisodeParser.seasonPattern2
import boat.rename.EpisodeParser.specialMarkerIndex
import boat.rename.EpisodeParser.standaloneEpisodePattern
import boat.rename.EpisodeParser.versionPattern
import boat.rename.FileAttributes.decodeIfEncoded
import boat.rename.FileAttributes.extractSubtitleLanguage
import boat.rename.FileAttributes.isMovieExtra
import boat.rename.FileAttributes.movieExtraPattern
import boat.rename.FileAttributes.videoExtensions
import java.io.File

/**
 * The title and year of a movie or show from file and folder names, and what kind of file or folder it is (extras, junk, library folders).
 */
object MediaNameParser {

    /**
     * Removes known release groups attached with "-", e.g. "sparks-inception" or "Inception.2010.x264-SPARKS".
     */
    fun removeReleaseGroups(name: String): String {
        if (releaseGroups.isEmpty()) return name
        return name.replace(leadingReleaseGroupPattern, "").replace(trailingReleaseGroupPattern, "")
    }

    fun cleanName(inputName: String): String {
        val withoutDecoration = removeBracketsAndPrefixes(inputName)
        val (withoutTags, hasParenthesizedYear) = removeParenthesizedTags(withoutDecoration)

        // the title ends where the season or episode starts
        val cutIndex = earliestEpisodeMarkerIndex(withoutTags, inputName) ?: episodeFallbackCutIndex(withoutTags, hasParenthesizedYear)
        val truncated = cutIndex != null
        val title = if (cutIndex != null) withoutTags.substring(0, cutIndex) else withoutTags

        val tidy = removeTrailingNoise(normalizeSeparators(title), hasParenthesizedYear, truncated)
        return if (truncated) tidy else cutTrailingEpisodeNumber(tidy)
    }

    // brackets with release info, site and group prefixes, remote media ids
    private fun removeBracketsAndPrefixes(inputName: String): String {
        // Normalize unicode and foreign characters, remove backslash escape characters
        var name = inputName.replace("：", " ").replace("?", " ").replace("\\", "")

        // the title ends at a bracketed episode like "House - [2x01] - Acceptance" or "Dragon Ball GT [1.21] A Secret Revealed"
        val bracketedEpisode = bracketedEpisodePattern.find(name)
        if (bracketedEpisode != null && bracketedEpisode.range.first > 0) {
            name = name.substring(0, bracketedEpisode.range.first)
        }

        // Remove bracketed release info like [UNCENSORED], [1080p], [5.1 MP3], [x265], [FINAL]
        name = name.replace(bracketPattern, " ").trim()

        // Remove common release group/site prefixes
        name = name.replace(sitePrefixPattern, "")
        name = removeReleaseGroups(name)

        // Remove remote media ids of already renamed files like {tmdb-27205}
        name = name.replace(remoteMediaIdPattern, " ").trim()

        // Remove release tag prefix like (B-A) or [GAX]
        name = name.replace(parenthesizedPrefixPattern, "")

        // check if it's a known hex garbage prefix (containing digits)
        val match = groupPrefixPattern.find(name)
        if (match != null && match.value.any { it.isDigit() } && !name.lowercase().startsWith("star") && !name.lowercase().startsWith("hack") &&
            !name.lowercase().matches(unitPattern)
        ) {
            name = name.replace(groupPrefixPattern, "")
        }
        return name
    }

    /**
     * Removes the parenthesized tags and the year in parentheses, version markers and release tags in the middle of the
     * name. Also tells if the name had a year in parentheses (it is the year of the title then).
     */
    private fun removeParenthesizedTags(nameWithTags: String): Pair<String, Boolean> {
        var name = nameWithTags
        // Remove parenthesized tags like (BBC 1995), (2013 BluRay - 1080p DUAL AUDIO), (ger.sub), (h264), (moviesbyrizzo), (720p - DUAL Audio), (1BB88E0D)
        // underscores are word characters and hide tags like (1520x1080_Blu-Ray_FLAC) from the word boundaries
        name = name.replace(parenthesizedGroupPattern) { it.value.replace("_", " ") }
        name = name.replace(parenthesizedTagsPattern, " ")

        name = name.replace(parenthesizedEpisodeRangePattern, " ")

        name = name.replace(episodeRangePattern, " ")

        val hasParenthesizedYear = parenthesizedYearPattern.containsMatchIn(name)
        if (hasParenthesizedYear) {
            name = name.replace(copyCounterAfterParenthesizedYearPattern, "")
        }
        name = name.replace(parenthesizedYearPattern, " ")
        name = name.replace("(", "").replace(")", "").replace("_", " ").trim()
        name = name.removePrefix(".")

        name = name.replace(yearEpisodePrefixPattern, "")

        // Remove version markers like v2 in 01v2
        name = name.replace(versionPattern, "")

        // release tags end the title, the season and episode detection works on what's left
        val technicalTagMatch = midNameTechnicalTagPattern.find(name)
        if (technicalTagMatch != null && technicalTagMatch.range.first > 0) {
            name = name.substring(0, technicalTagMatch.range.first).trim()
        }
        return Pair(name, hasParenthesizedYear)
    }

    private fun Regex.startIndexIn(name: String): Int? = find(name)?.range?.first

    // only a match behind the first character cuts something off
    private fun Regex.startIndexAfterFirstCharacterIn(name: String): Int? = startIndexIn(name)?.takeIf { it > 0 }

    /**
     * Where the first season or episode marker (S01, Season 2, Part 3, Day 4, Extras, ...) starts, null if there is none.
     */
    private fun earliestEpisodeMarkerIndex(name: String, inputName: String): Int? = listOfNotNull(
        seasonPattern.startIndexIn(name),
        seasonPattern2.startIndexIn(name),
        seasonInNamePattern.startIndexIn(name),
        yearEpisodeInNamePattern.startIndexAfterFirstCharacterIn(name),
        partInNamePattern.startIndexAfterFirstCharacterIn(name),
        episodeWordPattern.startIndexAfterFirstCharacterIn(name),
        doubleDashEpisodePattern.startIndexAfterFirstCharacterIn(name),
        specialMarkerIndex(name, casingReference = inputName)?.takeIf { it > 0 },
        dayInNamePattern.startIndexAfterFirstCharacterIn(name),
        dvdPattern.startIndexIn(name),
        bookPattern.startIndexIn(name),
        packInNamePattern.startIndexAfterFirstCharacterIn(name),
        extrasInNamePattern.startIndexAfterFirstCharacterIn(name),
    ).minOrNull()

    /**
     * Where an episode number without a clear marker starts (the first rule that finds one wins): "Show 101 Title",
     * "Show - 05", "Show 05", ... The year of the title ends the name as the last resort.
     */
    private fun episodeFallbackCutIndex(name: String, hasParenthesizedYear: Boolean): Int? {
        if (name.matches(numericRangePattern)) return null
        episodeWithTitlePattern.startIndexIn(name)?.let { return it }
        explicitEpisodePattern.startIndexIn(name)?.let { return it }
        dashEpisodePattern.startIndexIn(name)?.let { return it }
        cleanEpisodeMatchPattern.startIndexIn(name)?.let { return it }
        zeroPaddedMatchPattern.startIndexIn(name)?.let { return it }

        val standaloneEpisodeMatch = standaloneEpisodePattern.find(name)
        if (standaloneEpisodeMatch != null && yearPattern.find(standaloneEpisodeMatch.value) == null) {
            // a number behind an article is part of the title
            val beforeMatch = name.substring(0, standaloneEpisodeMatch.range.first).trim().lowercase()
            return standaloneEpisodeMatch.range.first.takeIf { !leadingArticles.contains(beforeMatch) }
        }
        val yearIndex = yearPattern.startIndexAfterFirstCharacterIn(name)
        return yearIndex.takeIf { !hasParenthesizedYear }
    }

    private fun normalizeSeparators(title: String): String {
        var name = title.replace(bracketPattern, "").trim()
        name = name.replace(acronymPattern) { matchResult ->
            matchResult.value.replace(".", "")
        }
        name = name.replace(".", " ").replace("_", " ").trim()
        name = name.replace(extrasPlusPattern, " ")
        name = name.replace(episodeRangePattern, " ")
        return name.replace(whitespacePattern, " ").trim()
    }

    // quality tags, years, episode markers and punctuation at the end, punctuation at the start
    private fun removeTrailingNoise(normalized: String, hasParenthesizedYear: Boolean, truncated: Boolean): String {
        var name = normalized
        while (true) {
            val before = name
            name = name.replace(trailingQualityTag, "").trim()
            if (!hasParenthesizedYear) {
                name = name.replace(trailingYearPattern, "").trim()
                if (truncated) {
                    name = name.replace(trailingYearAfterNumberPattern, "").trim()
                }
            }
            name = name.replace(trailingEpisodeMarkerPattern, "").trim()
            name = name.replace(trailingDashArticlePattern, "").trim()
            while (name.isNotEmpty() && name.last() in trailingPunctuation) {
                name = name.dropLast(1).trim()
            }
            if (name == before) break
        }
        while (name.isNotEmpty() && name.first() in trailingPunctuation) {
            name = name.drop(1).trim()
        }
        return name
    }

    // last resort if nothing was cut off yet
    private fun cutTrailingEpisodeNumber(name: String): String {
        // If extracted name ends with a number that looks like an episode (e.g., show-24)
        val trailingMatch = trailingEpisodePattern.find(name)
        if (trailingMatch != null) {
            return name.substring(0, trailingMatch.range.first).trim()
        }
        // If name has a number followed by space and another number (e.g. Golgo 13 20), truncate the trailing episode
        val multiMatch = multiNumberTrailingPattern.find(name)
        if (multiMatch != null) {
            return name.substring(0, multiMatch.range.first).trim()
        }
        return name
    }

    fun isJunk(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.length <= 1 || trimmed.all { !it.isLetterOrDigit() }) return true

        val lower = trimmed.lowercase()
        val normalized = lower.replace("_", " ").replace(".", " ").replace("-", " ").trim()
        return isKnownJunkWord(lower, normalized) ||
                isUrl(lower) ||
                isNumberOrHash(lower) ||
                isSeasonDiscOrPartMarker(normalized) ||
                junkPrefixes.any { normalized.startsWith(it) || lower.startsWith(it) } ||
                consistsOfJunkTokens(trimmed)
    }

    // release groups, articles, category folders and the other exact words that are no title
    private fun isKnownJunkWord(lower: String, normalized: String): Boolean {
        fun isOneOf(words: Set<String>) = words.contains(lower) || words.contains(normalized)
        return releaseGroups.contains(lower) ||
                isOneOf(junkArticles) ||
                isOneOf(categoryFolderNames) ||
                // range folder names like "0-9", "a-z"
                lower.matches(rangeSinglePattern) || lower.matches(rangeLetterPattern) ||
                isOneOf(countryAndLanguageTags) ||
                isOneOf(auxiliaryExactKeywords) ||
                isOneOf(technicalTags)
    }

    private val topLevelDomains = listOf(".com", ".org", ".net", ".me", ".to", ".tv", ".io")

    // magnet uris and urls
    private fun isUrl(lower: String): Boolean =
        lower.startsWith("magnet:") || lower.contains("xt=urn:") || lower.startsWith("http://") || lower.startsWith("https://") ||
                lower.startsWith("www.") || topLevelDomains.any { lower.endsWith(it) }

    // pure numbers, numbered sub folders like "2a. Season 1 (2010-11)" and pure hex hashes (1E372EE5, BB605406)
    private fun isNumberOrHash(lower: String): Boolean =
        lower.matches(pureNumberPattern) || lower.matches(numberedSubFolderPattern) ||
                (lower.matches(hexHashPattern) && lower.any { it.isDigit() } && lower.any { it.isLetter() })

    private fun isSeasonDiscOrPartMarker(normalized: String): Boolean =
        normalized.matches(seasonDiscPartPattern) ||
                normalized.matches(sEPattern) ||
                normalized.matches(xPattern) ||
                normalized.matches(dashDotNumPattern) ||
                (normalized.matches(shortWordNumPattern) && !normalized.startsWith("the ") && !normalized.startsWith("a ") && !normalized.startsWith("an ")) ||
                normalized.matches(numShortWordPattern) // e.g. "6 spa", "6_spa"

    // "1080p x264 rarbg", at least one word is a known junk word, the others are junk words, single characters or numbers
    private fun consistsOfJunkTokens(trimmed: String): Boolean {
        val tokens = trimmed.lowercase().replace(nonAlphanumericPattern, " ").trim().split(" ").filter { it.isNotBlank() }
        return tokens.any { junkTokens.contains(it) } &&
                tokens.all { token -> token.length <= 1 || token.matches(pureNumberPattern) || junkTokens.contains(token) }
    }

    private fun isAuxiliaryPathOrFile(file: File, cleanedFileName: String): Boolean {
        val nonMediaExtensions = setOf(
            "pdf", "txt", "nfo", "jpg", "jpeg", "png", "gif", "bmp", "doc", "docx",
            "xls", "xlsx", "exe", "zip", "rar", "7z", "tar", "gz", "sfv", "par2", "cue", "md5", "torrent", "url"
        )
        if (nonMediaExtensions.contains(file.extension.lowercase())) return true

        val auxiliaryFolderKeywords = setOf(
            "extras", "extra", "featurettes", "featurette", "bonus", "bonuses",
            "samples", "sample", "screenshots", "screenshot", "screens cap", "screens",
            "trailers", "trailer", "teasers", "teaser", "promos", "promo",
            "deleted scenes", "deleted scene", "screentests", "screentest",
            "subs", "subtitles", "sub", "original scripts", "soundtracks", "soundtrack",
            "ost", "artwork", "covers", "fonts", "scans", "proofs",
            "outtakes", "outtake", "bloopers", "blooper", "gag reel", "behind the scenes", "making of",
            "fake psas", "fake psa", "tv spots", "tv spot", "storyboard comparison", "tv recap specials",
            "lessons", "videolessons", "audio blog", "gallery", "galleries", "photo gallery", "image gallery", "still gallery", "stills gallery", "slideshow", "slideshows"
        )

        val lowerCleaned = cleanedFileName.lowercase()
        val auxiliaryFileKeywords = listOf(
            "alternate ending", "alternate endings", "alternate opening", "alternate openings", "alternate cut",
            "alternate scene", "alternate scenes", "extended scene", "extended scenes", "extended cut",
            "featurette", "featurettes", "trailer", "trailers", "teaser", "teasers", "promo", "promos",
            "deleted scene", "deleted scenes", "screentest", "screentests", "outtake", "outtakes",
            "blooper", "bloopers", "gag reel", "gag reels", "screenshot", "screenshots", "sample", "samples",
            "character profile", "character profiles", "behind the scenes", "making of", "interview", "interviews",
            "original script", "original scripts", "textless", "special feature", "special features", "bonus", "bonuses",
            "gallery", "galleries", "photo gallery", "image gallery", "still gallery", "stills gallery", "slideshow", "slideshows"
        )
        if (auxiliaryFileKeywords.any { lowerCleaned.contains(it) }) {
            val fileNameWithoutExt = file.nameWithoutExtension
            if (seasonPattern.find(fileNameWithoutExt) == null && seasonPattern2.find(fileNameWithoutExt) == null) {
                return true
            }
        }

        var p = file.parentFile
        var depth = 0
        while (p != null && depth < 6) {
            val pName = decodeIfEncoded(p.name).lowercase().trim()
            val pNormalized = pName.replace("_", " ").replace(".", " ").replace("-", " ")
            val pTokens = pNormalized.split(nonAlphanumericPattern).filter { it.isNotBlank() }

            val isPackFolder = pName.contains("+") || pNormalized.contains("with extras") || pNormalized.contains("and extras") ||
                    pNormalized.matches(packWithExtrasPattern)

            if (!isPackFolder) {
                if (auxiliaryFolderKeywords.contains(pName) || auxiliaryFolderKeywords.contains(pNormalized)) {
                    return true
                }
                if (pTokens.size <= 3 && pTokens.any { auxiliaryFolderKeywords.contains(it) }) {
                    return true
                }
            }
            p = p.parentFile
            depth++
        }

        return false
    }

    fun extractName(file: File): String {
        val rawBaseName = decodeIfEncoded(file.name).substringBeforeLast(".")
        val cleanedFileName = cleanName(gluedSeasonEpisode(file)?.first ?: rawBaseName)
        val cleanedFileNameWithoutTrackNumber = cleanName(rawBaseName.replace(leadingTrackNumberPattern, ""))

        val folders = collectShowFolders(file)
        val parentShowName = combineShowFolderNames(folders.names)?.let { removeTrailingSeasonNumber(it, rawBaseName) }

        if (isUsableFileName(file, rawBaseName, cleanedFileName)) {
            if (!parentShowName.isNullOrBlank()) {
                // the file name of an extra describes the extra, e.g. "DVD Extras/Deep Space Nine Sketchbook.mkv"
                if (folders.isBelowExtrasContainer) {
                    return showFromFileNameIfFolderAddsWords(cleanedFileNameWithoutTrackNumber, parentShowName) ?: parentShowName
                }
                showFromFolderIfItDescribesTheFileBetter(parentShowName, cleanedFileName, rawBaseName, folders.isInsidePackOrSeriesFolder)?.let { return it }
            }
            return knownShowName(cleanedFileName)
        }

        if (parentShowName != null && parentShowName.isNotBlank()) {
            if (!isJunk(cleanedFileNameWithoutTrackNumber)) {
                showFromFileNameIfFolderAddsWords(cleanedFileNameWithoutTrackNumber, parentShowName)?.let { return it }
            }
            return parentShowName
        }

        showAfterEpisodeMarkerPattern.find(rawBaseName)?.let { return cleanName(it.groupValues[1]) }
        doubleDashEpisodePattern.find(rawBaseName)?.takeIf { it.range.first > 0 }?.let { return cleanName(rawBaseName.substring(0, it.range.first)) }
        return ""
    }

    // shows which are known by an abbreviation
    private fun knownShowName(name: String): String = showNamesOfAbbreviations[name.lowercase()] ?: name

    /**
     * If the file name can be the name of the show: it isn't junk, an episode number, a season marker or a file next to
     * the media (cover, nfo, ...), and it isn't a short acronym of a file in a folder.
     */
    private fun isUsableFileName(file: File, rawBaseName: String, cleanedFileName: String): Boolean {
        val startsWithEpisodeNumber = startsWithEpisodeNumberPattern.find(rawBaseName) != null
        val isNumericShowName = cleanedFileName.matches(pureNumberPattern) &&
                !cleanedFileName.startsWith("0") &&
                cleanedFileName != rawBaseName &&
                !startsWithEpisodeNumber

        val lowerBaseName = rawBaseName.lowercase()
        val isUnusable = (isJunk(cleanedFileName) && !isNumericShowName) ||
                startsWithEpisodeNumber ||
                listOf("s0", "s1", "s2", "s3").any { lowerBaseName.startsWith(it) } ||
                isAuxiliaryPathOrFile(file, cleanedFileName)

        val isShortAcronym = cleanedFileName.matches(shortAcronymPattern) &&
                (cleanedFileName == cleanedFileName.uppercase() || cleanedFileName.lowercase() in acronymFileNames)

        return !isUnusable && cleanedFileName.isNotBlank() && (!isShortAcronym || file.parentFile == null)
    }

    /** The folders above a file which can name its show, nearest first. */
    private class ShowFolders(val names: List<String>, val isInsidePackOrSeriesFolder: Boolean, val isBelowExtrasContainer: Boolean)

    /**
     * Walks up (at most 5 folders, up to the library category or letter folder) and collects the cleaned names of the
     * folders which can be the name of the show.
     */
    private fun collectShowFolders(file: File): ShowFolders {
        val names = mutableListOf<String>()
        var isInsidePackOrSeriesFolder = false
        var isBelowExtrasContainer = false
        var currentFile: File? = file.parentFile
        var depth = 0
        while (currentFile != null && depth < 5) {
            val parentName = decodeIfEncoded(currentFile.name)
            val lowerParent = parentName.lowercase().trim()
            val normalizedParent = lowerParent.replace("_", " ").replace(".", " ").replace("-", " ").trim()

            // Stop traversing upwards if we reached a single letter folder, symbol folder, range folder, or root category / filesystem folder
            val isCategoryFolder = categoryFolderNames.contains(lowerParent) || categoryFolderNames.contains(normalizedParent)
            if (parentName.trim().length <= 1 ||
                parentName.trim().all { !it.isLetterOrDigit() } ||
                lowerParent.matches(rangeSinglePattern) ||
                lowerParent.matches(rangeLetterPattern) ||
                isCategoryFolder
            ) {
                if (isCategoryFolder && lowerParent in seriesCategoryFolders) {
                    isInsidePackOrSeriesFolder = true
                }
                break
            }

            val isExtrasContainer = isExtrasContainer(parentName)
            if (isExtrasContainer) {
                isBelowExtrasContainer = true
                names.clear()
            }

            if (packInNamePattern.containsMatchIn(parentName) || releaseFolderMarkers.any { parentName.contains(it, ignoreCase = true) }) {
                isInsidePackOrSeriesFolder = true
            }

            val cleanedParentName = cleanName(parentName)
            val isParentNumericShowName = cleanedParentName.matches(pureNumberPattern) &&
                    !cleanedParentName.startsWith("0") &&
                    (parentName != cleanedParentName || currentFile.parentFile?.let { p ->
                        val pName = decodeIfEncoded(p.name).lowercase().trim()
                        pName.matches(rangeSinglePattern) || categoryFolderNames.contains(pName)
                    } == true)

            if (!isExtrasContainer && cleanedParentName.isNotBlank() && (!isJunk(cleanedParentName) && !isJunk(parentName) || isParentNumericShowName)) {
                names.add(cleanedParentName)
            }
            currentFile = currentFile.parentFile
            depth++
        }
        return ShowFolders(names, isInsidePackOrSeriesFolder, isBelowExtrasContainer)
    }

    /** The show name of the folders: the outermost folder, extended by the inner folders which add something. */
    private fun combineShowFolderNames(names: List<String>): String? {
        if (names.isEmpty()) return null
        val reversed = names.reversed()
        var combined = reversed.first()
        for (i in 1 until reversed.size) {
            combined = mergeShowFolderName(combined, reversed[i])
        }
        return knownShowName(combined.trim())
    }

    private fun mergeShowFolderName(combined: String, next: String): String {
        val lowerCombined = combined.lowercase()
        val lowerNext = next.lowercase()
        val normCombined = lowerCombined.replace(nonAlphanumericOnlyPattern, "")
        val normNext = lowerNext.replace(nonAlphanumericOnlyPattern, "")
        val isSameShow = normCombined == normNext ||
                normCombined.startsWith(normNext) ||
                normNext.startsWith(normCombined) ||
                lowerCombined.contains(lowerNext) ||
                lowerNext.contains(lowerCombined)
        if (isSameShow) {
            // keep the longer name, unless it is a season folder
            return if (lowerCombined.length >= lowerNext.length && !lowerCombined.contains("season")) {
                combined
            } else if (!lowerNext.contains("season") && !lowerNext.contains("temporada")) {
                next
            } else {
                combined
            }
        }
        val lowerNextCleaned = cleanName(next).lowercase()
        val isNextAcronym = lowerNextCleaned in showAcronyms ||
                (lowerNextCleaned.length in 2..6 && lowerCombined.split(" ").mapNotNull { it.firstOrNull() }.joinToString("").lowercase() == lowerNextCleaned)
        return if (!isJunk(lowerNextCleaned) && !isNextAcronym && !lowerNextCleaned.startsWith("story ") && !lowerNextCleaned.startsWith("temporada ")) {
            "$combined $next"
        } else {
            combined
        }
    }

    /**
     * The show name of the folder if it describes the file better than the name of the file does ("Full Metal Panic 10
     * Run, Running, Ran" in "Full Metal Panic"), null if the name of the file stays.
     */
    private fun showFromFolderIfItDescribesTheFileBetter(
        parentShowName: String, cleanedFileName: String, rawBaseName: String, isInsidePackOrSeriesFolder: Boolean
    ): String? {
        // "Full Metal Panic 10 Run, Running, Ran" in "Full Metal Panic", episodes below 10 are already cut as zero padded number
        // or "01. Crest of the Stars" and "1-01 Gigantor (US) S01E01" with a leading track number
        val normalizedParentShowName = parentShowName.lowercase().replace(nonAlphanumericOnlyPattern, "")
        val episodeNumberMatch = episodeNumberWithTitlePattern.find(cleanedFileName)
        val showBeforeEpisodeNumber = episodeNumberMatch?.groupValues?.get(1)
        val normalizedShowBeforeEpisodeNumber = showBeforeEpisodeNumber?.lowercase()?.replace(nonAlphanumericOnlyPattern, "")
        if (normalizedShowBeforeEpisodeNumber != null && normalizedShowBeforeEpisodeNumber.startsWith(normalizedParentShowName)) {
            return parentShowName
        }
        // the folder carries more than the show, e.g. "No_Game_No_Life_English_Dub_720p_1-12_Complete"
        // but the number isn't part of the show like "Space Battleship Yamato 2199"
        if (normalizedShowBeforeEpisodeNumber != null && showBeforeEpisodeNumber.trim().contains(' ') &&
            normalizedParentShowName.startsWith(normalizedShowBeforeEpisodeNumber) &&
            !normalizedParentShowName.startsWith(normalizedShowBeforeEpisodeNumber + episodeNumberMatch.groupValues[2])
        ) {
            return showBeforeEpisodeNumber.trim()
        }
        val fileNameAfterTrackNumber = rawBaseName.replace(leadingTrackNumberPattern, "")
        if (fileNameAfterTrackNumber != rawBaseName) {
            val normalizedFileName = cleanName(fileNameAfterTrackNumber).lowercase().replace(nonAlphanumericOnlyPattern, "")
            if (normalizedFileName.isNotEmpty() &&
                (normalizedFileName.startsWith(normalizedParentShowName) || normalizedParentShowName.startsWith(normalizedFileName))
            ) {
                return parentShowName
            }
        }
        val remainder = if (cleanedFileName.startsWith(parentShowName, ignoreCase = true)) {
            cleanedFileName.substring(parentShowName.length).trim()
        } else {
            cleanedFileName.removePrefix(parentShowName).trim()
        }
        val isNumberPartOfTitle = remainder.startsWith("-") && seasonEpisodePattern.containsMatchIn(rawBaseName)
        if (remainder.isEmpty() || remainder.matches(remainderEpisodePattern) && !isNumberPartOfTitle) {
            return parentShowName
        }
        val remainderParent = if (parentShowName.startsWith(cleanedFileName, ignoreCase = true)) {
            parentShowName.substring(cleanedFileName.length).trim()
        } else {
            parentShowName.removePrefix(cleanedFileName).trim()
        }
        if (remainderParent.isEmpty() || remainderParent.matches(remainderYearOrNumPattern)) {
            return parentShowName
        }
        if (isInsidePackOrSeriesFolder) {
            val parentWords = significantWordsOf(parentShowName)
            val fileWords = significantWordsOf(cleanedFileName)
            val hasOverlap = fileWords.any { fw -> parentWords.any { pw -> fw == pw || fw.contains(pw) || pw.contains(fw) } }
            if (!hasOverlap) {
                return parentShowName
            }
        }
        return null
    }

    private fun significantWordsOf(name: String): List<String> =
        name.lowercase().split(nonAlphanumericPattern).filter { it.length > 2 && it !in overlapStopWords }

    /**
     * The file name if the folder only adds words to it, e.g. "Pride and Prejudice" in "Pride and Prejudice Jane Austen".
     * A single added word like "The Office US" is kept, it tells versions of a show apart.
     */
    private fun showFromFileNameIfFolderAddsWords(fileShowName: String, folderShowName: String): String? {
        if (!fileShowName.contains(' ') || !folderShowName.startsWith(fileShowName, ignoreCase = true)) return null
        val addedWords = folderShowName.substring(fileShowName.length).trim().split(whitespacePattern).filter { it.isNotBlank() }
        return if (addedWords.size >= 2 && addedWords.none { word -> word.any { it.isDigit() } }) fileShowName else null
    }

    /**
     * Season folders like "Jessica.Jones.3.(2019)" or "Takeshis.Castle.1" carry the season as trailing number,
     * it's only removed if the file name confirms that season, e.g. "S03E01" or "Takeshis Castle - 101".
     */
    private fun removeTrailingSeasonNumber(showName: String, fileBaseName: String): String {
        val season = trailingSeasonNumberPattern.find(showName)?.groupValues?.get(1)?.toIntOrNull() ?: return showName
        val seasonInFileName = "(?i)(?:\\bs0*$season\\s?e[0-9]|\\b$season[x][0-9]|(?:^|[\\s_.-])$season[0-9]{2}(?:[\\s_.\\[]|$))".toRegex()
        return if (seasonInFileName.containsMatchIn(fileBaseName)) showName.replace(trailingSeasonNumberPattern, "").trim() else showName
    }

    /**
     * Movie title and year from the file name, or from the closest folder if the file name doesn't carry a title
     * (e.g. cd1.mkv, sample.mkv, RARBG.txt). Unlike [extractName], folder names aren't combined, so movies in a
     * collection like "Bruce Lee Collection/01. The Big Boss (1971).mp4" are named after their file.
     */
    fun extractMovieNameAndYear(file: File): Pair<String, Int?> {
        val rawBaseName = decodeIfEncoded(file.name).substringBeforeLast(".")
        val parsedFileName = parseMovieName(rawBaseName)
        // extras like "Movie (2010)/Featurettes/Interview.mkv" belong to the movie of the folder above
        val isInExtrasFolder = file.parentFile?.let { parent ->
            movieExtraPattern.containsMatchIn(decodeIfEncoded(parent.name)) && parent.parentFile?.let { !isLibraryGroupFolder(it) } == true
        } == true
        // and extras like "Movie (2010)/Movie Trailer.mkv" to the movie of their folder
        val isExtraInMovieFolder = movieExtraPattern.containsMatchIn(rawBaseName) && file.parentFile?.let { !isLibraryGroupFolder(it) } == true
        if (!isInExtrasFolder && !isExtraInMovieFolder && isMovieTitle(parsedFileName)) {
            return yearPrefixedTitle(file, parsedFileName) ?: parsedFileName
        }
        var current: File? = file.parentFile
        var depth = 0
        while (current != null && depth < 5 && !isLibraryGroupFolder(current)) {
            val parentName = decodeIfEncoded(current.name)
            if (parentName.lowercase() !in discStructureFolderNames && !(current == file.parentFile && isInExtrasFolder)) {
                val parsedParentName = parseMovieName(parentName)
                if (isMovieTitle(parsedParentName)) return parsedParentName
            }
            current = current.parentFile
            depth++
        }
        return Pair("", null)
    }

    /**
     * Splits a movie release name like "Alien.Romulus.2024.2160p.WEB-DL.DDP5.1" into title and year. Unlike [cleanName]
     * numbers are never taken for episodes, so "127 Hours" or "1917.2019" keep their title. The name is cut at the
     * release year, or without one at the first release tag. A cut inside parentheses drops the whole group, e.g.
     * "Zabriskie Point (Antonioni 1970)".
     */
    fun parseMovieName(rawName: String): Pair<String, Int?> {
        // "!" separates like "Werner - Beinhart!1990"
        var name = rawName.replace("：", " ").replace("?", " ").replace("!", "! ")
        name = name.replace(movieHashPrefixPattern, "").replace(yearGluedToResolutionPattern, "$1 ")
        name = name.replace(remoteMediaIdPattern, " ")
        name = name.replace(movieSitePrefixPattern, "")
        name = name.replace(leadingBracketGroupsPattern, "").trim()
        name = removeReleaseGroups(name)
        name = name.replace(listNumberPrefixPattern, "")
        name = name.replace("(", " (").replace(")", ") ").replace("[", " [").replace("]", "] ").replace("_", " ").replace("+", " ")
        name = name.replace(acronymPattern) { matchResult ->
            matchResult.value.replace(".", "")
        }
        name = name.replace(".", " ").replace(whitespacePattern, " ").trim()
        // dash separated names like "Fear.Eats.the.Soul-1974--720p-EN" or "maleficent-1080p", but not "Spider-Man"
        name = name.replace(tokenPattern) { token ->
            val parts = token.value.split('-')
            val isTaggedPart = parts.size > 1 && parts.any { it.lowercase().matches(movieTechnicalTagPattern) || it.matches(movieYearPattern) }
            if (isTaggedPart) parts.joinToString(" ") else token.value
        }.replace(whitespacePattern, " ").trim()

        val tokens = tokenPattern.findAll(name).toList()
        val cores = tokens.map { token -> token.value.trim { !it.isLetterOrDigit() }.lowercase() }
        fun isTag(index: Int, tagPattern: Regex) = cores[index].matches(tagPattern) || cores[index].split('-').any { it.matches(tagPattern) }
        fun isAnyTag(index: Int) = isTag(index, movieTechnicalTagPattern) || isTag(index, movieLanguageOrEditionTagPattern)

        // the first token is always part of the title, e.g. "1917" or "2012"
        val firstTechnicalTag = (1 until tokens.size).firstOrNull { isTag(it, movieTechnicalTagPattern) } ?: tokens.size
        val firstLanguageOrEditionTag = (1 until tokens.size).firstOrNull { isTag(it, movieLanguageOrEditionTagPattern) } ?: tokens.size
        val yearCandidates = (1 until firstTechnicalTag).filter { cores[it].matches(movieYearPattern) }
        // the release year is in parentheses, follows a year of the title or is followed by tags, otherwise it's the first
        // one ("Wonder Woman 1984 2020", "Summer of 1984 2023 1080p", "2000 Mules 2022 - ... of 2020")
        val yearIndex = yearCandidates.lastOrNull { index ->
            val token = tokens[index].value
            (token.startsWith("(") && token.endsWith(")")) || (token.startsWith("[") && token.endsWith("]")) ||
                    cores[index - 1].matches(movieYearPattern) || (index + 1 < tokens.size && isAnyTag(index + 1))
        } ?: yearCandidates.firstOrNull()
        val cutIndex = yearIndex ?: minOf(firstTechnicalTag, firstLanguageOrEditionTag)

        var cut = if (cutIndex < tokens.size) tokens[cutIndex].range.first else name.length
        val prefix = name.substring(0, cut)
        val openGroup = prefix.lastIndexOfAny(charArrayOf('(', '['))
        if (openGroup >= 0 && prefix.indexOfAny(charArrayOf(')', ']'), openGroup) == -1) {
            cut = openGroup
        }

        var title = name.substring(0, cut).replace(bracketPattern, " ").replace("(", " ").replace(")", " ")
            .replace(whitespacePattern, " ").trim()
        val trailingPunctuation = charArrayOf('-', '.', ',', ';', ':', '_', '~', '+', '!', '?', '/', '\\')
        while (true) {
            val before = title
            title = title.replace(trailingMovieTagPattern, "").trimEnd(*trailingPunctuation).trim()
            if (title == before) break
        }
        title = title.trimStart(*trailingPunctuation).trim()
        return Pair(title, yearIndex?.let { cores[it].toInt() })
    }

    /**
     * Collections like "The Texas Chainsaw Massacre Collection (1974-2017)/2017.Leatherface.mkv" put the year in front.
     */
    private fun yearPrefixedTitle(file: File, titleAndYear: Pair<String, Int?>): Pair<String, Int?>? {
        if (titleAndYear.second != null) return null
        val match = leadingYearTitlePattern.find(titleAndYear.first) ?: return null
        val year = match.groupValues[1].toInt()
        val isInCollectionOfThatTime = generateSequence(file.parentFile) { it.parentFile }.take(3).takeWhile { !isLibraryGroupFolder(it) }.any { folder ->
            yearRangePattern.findAll(decodeIfEncoded(folder.name)).any { range ->
                val from = range.groupValues[1].toInt()
                val to = range.groupValues[2].let { if (it.length == 2) from / 100 * 100 + it.toInt() else it.toInt() }
                year in from..to
            }
        }
        return if (isInCollectionOfThatTime) Pair(match.groupValues[2], year) else null
    }

    private fun isMovieTitle(titleAndYear: Pair<String, Int?>): Boolean {
        val (title, year) = titleAndYear
        if (title.isBlank() || title.none { it.isLetterOrDigit() } || title.lowercase().matches(movieTechnicalTagPattern)) return false
        // numeric titles like 300 or 1917 need a release year, "00001.m2ts" or "2022.mp4" aren't titles
        if (title.matches(pureNumberPattern)) return !title.startsWith("0") && year != null
        // with a release year short titles like "It (2017)", "Home (2015)" or "48.Hrs.1982" are titles as well
        if (year != null || !isJunk(title)) return true
        // isJunk takes "Cars 2" or "Saw 3" for a season or volume
        val normalized = title.lowercase()
        return normalized.matches(shortWordNumPattern) && !normalized.matches(seasonDiscPartPattern) && !normalized.matches(episodeLikeShortPattern)
    }

    /**
     * Letter, range (0-9) and category folders group the library, they never carry a title.
     */
    private fun isLibraryGroupFolder(folder: File): Boolean {
        val name = decodeIfEncoded(folder.name).trim()
        val lower = name.lowercase()
        val normalized = lower.replace("_", " ").replace(".", " ").replace("-", " ").trim()
        return name.length <= 1 || name.all { !it.isLetterOrDigit() } || lower.matches(rangeSinglePattern) || lower.matches(rangeLetterPattern) ||
                lower in categoryFolderNames || normalized in categoryFolderNames
    }

    /**
     * Series in the movies folder, detected by markers a movie title doesn't carry: S01E01 or 1x01 in the file name, a
     * season folder or several numbered episodes of the same show next to each other. A plain number like in
     * "Apollo 13" doesn't count.
     */
    fun hasSeriesMarkers(file: File): Boolean {
        val fileName = decodeIfEncoded(file.nameWithoutExtension)
        if (seasonEpisodeMarkerPattern.containsMatchIn(fileName) || seasonXEpisodeMarkerPattern.containsMatchIn(fileName)) return true

        // files directly in a letter folder, e.g. Star.Wars.Episode.1, have no show folder
        val showFolders = generateSequence(file.parentFile) { it.parentFile }.take(5).takeWhile { !isLibraryGroupFolder(it) }.toList()
        if (showFolders.isEmpty()) return false
        if (showFolders.any { seasonFolderMarkerPattern.containsMatchIn(decodeIfEncoded(it.name)) }) return true

        // e.g. "Tales.Of.Little.Women.EP1", "Samurai Champloo - Ep. 20 - Elegy", "Patlabor ON TELEVISION - 47 (BDRip)" or "Gunslinger Girl 01"
        val episodeMarker = episodeMarker(fileName) ?: return false
        val episodeMarkers = file.parentFile.listFiles()
            ?.filter { it.isFile && it.name != file.name && it.extension.lowercase() in videoExtensions }
            ?.mapNotNull { sibling -> episodeMarker(decodeIfEncoded(sibling.nameWithoutExtension))?.takeIf { it.showName == episodeMarker.showName } }
            .orEmpty()
            .plus(episodeMarker)
        // plain numbers only count zero-padded, "Ocean's 11", "Ocean's 12" and "Ocean's 13" are movies
        val hasEpisodeNotation = episodeMarkers.any { it.isExplicit || it.number.startsWith("0") }
        return hasEpisodeNotation && episodeMarkers.map { it.number.toInt() }.toSet().size >= 3
    }

    /**
     * Removes the episode number [extractName] keeps for plain numbered episodes, e.g. "Gunslinger Girl 10".
     */
    fun removeEpisodeNumber(seriesName: String, file: File): String {
        val episodeNumber = episodeMarker(decodeIfEncoded(file.nameWithoutExtension))?.number ?: return seriesName
        return seriesName.removeSuffix(" $episodeNumber").trim()
    }

    /**
     * Episode number in a file name, [isExplicit] if it's marked as episode (Ep. 20, - 47) rather than a plain trailing
     * number. [showName] is the normalized text in front of it.
     */
    private data class EpisodeMarker(val showName: String, val number: String, val isExplicit: Boolean)

    private fun episodeMarker(name: String): EpisodeMarker? {
        fun showName(text: String, end: Int) = text.substring(0, end).lowercase().replace(nonAlphanumericPattern, "")
        (explicitEpisodeLookbehindPattern.find(name) ?: dashEpisodePattern.find(name))?.let {
            return EpisodeMarker(showName(name, it.range.first), it.groupValues[1], true)
        }
        val withoutTags = name.replace(bracketPattern, " ").replace(versionPattern, "").trim()
        return trailingEpisodeNumberMarkerPattern.find(withoutTags)?.let { EpisodeMarker(showName(withoutTags, it.range.first), it.groupValues[1], false) }
    }

    /**
     * Extras folders inside a movie or show folder, like "Movie (2010)/Featurettes" or "Show/NC". The top folder itself
     * doesn't count, packs are named like "Good Omens Season 1 + Extras".
     */
    fun isInExtrasFolder(file: File): Boolean {
        val folders = generateSequence(file.parentFile) { it.parentFile }.take(6).takeWhile { !isLibraryGroupFolder(it) }.toList()
        return folders.dropLast(1).any { folder ->
            val name = decodeIfEncoded(folder.name)
            movieExtraPattern.containsMatchIn(name) || creditlessFolderPattern.matches(name.trim())
        }
    }

    /**
     * Files left alone: parts of a disc structure (moving them breaks the disc) and video courses, which no media
     * database knows.
     */
    fun getSkipReason(file: File): String? {
        val folderNames = generateSequence(file.parentFile) { it.parentFile }.map { decodeIfEncoded(it.name).lowercase() }.toList()
        return when {
            folderNames.any { it in discStructureRootNames } -> "disc structure"
            paddingFilePattern.containsMatchIn(decodeIfEncoded(file.name)) -> "padding file"
            file.extension.lowercase() in nonMediaExtensions -> "no media file"
            coursePattern.containsMatchIn(decodeIfEncoded(file.absolutePath)) -> "course"
            else -> null
        }
    }

    fun extractYear(fullName: String): Int? {
        val file = File(fullName)

        // 1. Prefer explicit parenthesized year in file name
        val nameParenMatch = parenthesizedYearExtractorRegex.find(file.name)
        if (nameParenMatch != null) return nameParenMatch.groupValues[1].toInt()

        val parent = file.parentFile
        val isSeasonFolder = parent?.name?.let {
            seasonFolderPattern.containsMatchIn(it)
        } ?: false

        // If parent is a season folder, check grandparent show folder first
        if (isSeasonFolder && parent.parentFile != null) {
            val grandParentParenMatch = parenthesizedYearExtractorRegex.find(parent.parentFile.name)
            if (grandParentParenMatch != null) return grandParentParenMatch.groupValues[1].toInt()

            val grandParentMatch = yearExtractorRegex.find(parent.parentFile.name)
            if (grandParentMatch != null && grandParentMatch.range.first > 0) {
                return grandParentMatch.groupValues[1].toInt()
            }
            if (grandParentMatch != null) {
                return grandParentMatch.groupValues[1].toInt()
            }
        }

        // 2. Prefer explicit parenthesized year in parent folders
        var current: File? = parent
        var depth = 0
        while (current != null && depth < 5) {
            val parentParenMatch = parenthesizedYearExtractorRegex.find(current.name)
            if (parentParenMatch != null) return parentParenMatch.groupValues[1].toInt()
            current = current.parentFile
            depth++
        }

        // 3. Fall back to unparenthesized year (prefer match where index > 0, a leading year like 1917.2019 is the title)
        val nameMatches = yearExtractorRegex.findAll(file.name).toList()
        nameMatches.firstOrNull { it.range.first > 0 }?.let { return it.groupValues[1].toInt() }

        current = parent
        depth = 0
        while (current != null && depth < 5) {
            val currMatch = yearExtractorRegex.findAll(current.name).firstOrNull { it.range.first > 0 }
            if (currMatch != null) {
                return currMatch.groupValues[1].toInt()
            }
            current = current.parentFile
            depth++
        }

        nameMatches.firstOrNull()?.let { return it.groupValues[1].toInt() }

        current = parent
        depth = 0
        while (current != null && depth < 5) {
            val currMatch = yearExtractorRegex.find(current.name)
            if (currMatch != null) {
                return currMatch.groupValues[1].toInt()
            }
            current = current.parentFile
            depth++
        }

        return null
    }

    private fun containerName(folderName: String) =
        folderName.lowercase().replace(bracketPattern, " ").replace("_", " ").replace(".", " ").substringAfterLast(" - ")
            .replace(whitespacePattern, " ").trim()

    private fun isExtrasFolder(folderName: String) = containerName(folderName).matches(extrasFolderPattern)

    private fun isExtrasContainer(folderName: String) = containerName(folderName).let { it.matches(extrasFolderPattern) || it.matches(subtitleFolderPattern) }

    /**
     * Name of an extra in the featurettes of its show, with the folders below the outermost extras folder so extras of
     * different folders don't collide, e.g. "Season 2 - The Log Lady - The Log Lady 01.mkv" for
     * "Twin.Peaks.(1990)/Featurettes/Season 2/The Log Lady/The Log Lady 01.mkv". Null if the file isn't an extra.
     */
    fun extraNameInFeaturettes(file: File): String? {
        val folders = generateSequence(file.parentFile) { it.parentFile }.takeWhile { !isLibraryGroupFolder(it) }.toList()
        val outermostExtrasFolder = folders.indexOfLast { isExtrasFolder(decodeIfEncoded(it.name)) }
        // extras named after their folder like "Season 03 Extras - Inside The X-Files.avi"
        val fileName = decodeIfEncoded(file.nameWithoutExtension)
        if (outermostExtrasFolder < 0 && fileName.contains(" - ") && isExtrasFolder(fileName.substringBefore(" - "))) return decodeIfEncoded(file.name)
        if (outermostExtrasFolder < 0) return null
        val subFolders = folders.take(outermostExtrasFolder).reversed().map { decodeIfEncoded(it.name) }
        return (subFolders + decodeIfEncoded(file.name)).joinToString(" - ")
    }

    /**
     * Language of a subtitle from its name or folder, e.g. "en" for "2_English.srt", "da" for "Danish/Show E1.srt" or
     * "en.sdh" for "english_SDH/Show.S01E01.srt".
     */
    fun extractSeriesSubtitleLanguage(file: File): String? {
        extractSubtitleLanguage(file)?.let { return it }
        val tokens = (listOf(file.nameWithoutExtension) + listOfNotNull(file.parentFile?.name))
            .flatMap { decodeIfEncoded(it).lowercase().split(nonLetterPattern) }.filter { it.isNotBlank() }
        val language = tokens.firstNotNullOfOrNull { languageNames[it] } ?: return null
        return (listOf(language) + subtitleFlags.filter { it in tokens }).joinToString(".")
    }

    /**
     * Movies in a series folder go to the movies, detected by a movie in the file name or in one of the two folders
     * above, e.g. "Naruto/3.Naruto Movies/1 - Ninja Clash in the Land of Snow.mkv" or "Aeon Flux/Movie (2005)/Aeon Flux.mp4".
     * Packs of episodes and movies like "Eden of the East 1-11+Movie 1-2 Complete" don't make their episodes movies.
     */
    fun isMovieInSeriesFolder(file: File): Boolean {
        val fileName = decodeIfEncoded(file.nameWithoutExtension)
        if (isOpeningEndingOrOva(file) || pilotPattern.containsMatchIn(fileName) || isMovieExtra(file)) return false
        if (seasonEpisodeMarkerPattern.containsMatchIn(fileName) || seasonXEpisodeMarkerPattern.containsMatchIn(fileName) ||
            seasonInNamePattern.containsMatchIn(fileName)
        ) return false
        // files in a show of the library were put there on purpose, e.g. "The Office (US) {tvdb-73244}/Season 7/Threat Level Midnight The Movie.mkv"
        if (remoteMediaIdPattern.containsMatchIn(decodeIfEncoded(file.absolutePath))) return false
        val folders = generateSequence(file.parentFile) { it.parentFile }.takeWhile { !isLibraryGroupFolder(it) }.map { decodeIfEncoded(it.name) }.toList()
        if (folders.any { isExtrasContainer(it) }) return false
        return movieFileWordPattern.containsMatchIn(fileName) ||
                folders.take(2).any { movieFolderWordPattern.containsMatchIn(it) && !episodesAndMoviesPackPattern.containsMatchIn(it) }
    }

    /**
     * Title and year of a movie in a series folder without its numbering, "Hunter X Hunter - Movie 1 - Phantom Rouge"
     * is searched as "Hunter X Hunter - Phantom Rouge", the year may come from its folder like "Movie (2005)".
     */
    fun extractMovieInSeriesNameAndYear(file: File): Pair<String, Int?> {
        val fileName = decodeIfEncoded(file.nameWithoutExtension).replace(leadingTrackNumberPattern, "")
        val (name, year) = parseMovieName(fileName)
        val subtitleMatch = movieSubtitlePattern.find(fileName.replace(".", " ").replace("_", " "))
        val fullName = if (subtitleMatch != null) {
            // the year stands before the number, "Ranma ½ [1991] - Movie 01 - Big Trouble In Nekonron China" would end at it
            val show = cleanName(subtitleMatch.groupValues[1])
            "$show ${parseMovieName(subtitleMatch.groupValues[2]).first}"
        } else name
        val title = fullName.replace(movieNumberPattern, " - ").replace(repeatedDashPattern, " - ")
            .replace(whitespacePattern, " ").trim { it == ' ' || it == '-' }
        val folderYear = file.parentFile?.let { parseMovieName(decodeIfEncoded(it.name)) }?.second
        return Pair(title, year ?: folderYear)
    }

    fun normalizeMovieTitle(title: String) = MediaProxyService.normalizeTitle(title).removePrefix("the ").replace(" ", "")
}
