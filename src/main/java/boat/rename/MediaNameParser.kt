package boat.rename

import boat.rename.EpisodeParser.doubleDashEpisodePattern
import boat.rename.EpisodeParser.gluedSeasonEpisode
import boat.rename.EpisodeParser.nonAlphanumericPattern
import boat.rename.EpisodeParser.seasonEpisodePattern
import boat.rename.FileAttributes.decodeIfEncoded
import boat.rename.JunkDetector.isAuxiliaryPathOrFile
import boat.rename.JunkDetector.isJunk
import boat.rename.LibraryFolders.isExtrasContainer
import boat.rename.NameCleaner.cleanName
import java.io.File

/**
 * The name of the show a file belongs to, taken from the name of the file and the folders above it (a file name like
 * "Dune.2021.mkv" or "01 - Pilot.mkv" in "Show/Season 1"), and the year of a file.
 */
object MediaNameParser {

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

    fun extractYear(fullName: String): Int? {
        val file = File(fullName)
        val parent = file.parentFile
        // the folders above the file, nearest first
        val folders = generateSequence(parent) { it.parentFile }.take(5).toList()

        // 1. Prefer explicit parenthesized year in file name
        parenthesizedYearIn(file.name)?.let { return it }

        // If parent is a season folder, check grandparent show folder first
        val showFolder = parent?.parentFile
        if (parent != null && seasonFolderPattern.containsMatchIn(parent.name) && showFolder != null) {
            (parenthesizedYearIn(showFolder.name) ?: yearExtractorRegex.find(showFolder.name)?.groupValues?.get(1)?.toInt())?.let { return it }
        }

        // 2. Prefer explicit parenthesized year in parent folders
        folders.firstNotNullOfOrNull { parenthesizedYearIn(it.name) }?.let { return it }

        // 3. Fall back to unparenthesized year (prefer match where index > 0, a leading year like 1917.2019 is the title)
        val nameMatches = yearExtractorRegex.findAll(file.name).toList()
        nameMatches.firstOrNull { it.range.first > 0 }?.let { return it.groupValues[1].toInt() }
        folders.firstNotNullOfOrNull { folder -> yearExtractorRegex.findAll(folder.name).firstOrNull { it.range.first > 0 } }
            ?.let { return it.groupValues[1].toInt() }
        nameMatches.firstOrNull()?.let { return it.groupValues[1].toInt() }
        return folders.firstNotNullOfOrNull { folder -> yearExtractorRegex.find(folder.name) }?.groupValues?.get(1)?.toInt()
    }

    private fun parenthesizedYearIn(name: String): Int? = parenthesizedYearExtractorRegex.find(name)?.groupValues?.get(1)?.toInt()
}
