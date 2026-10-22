package boat.rename

import boat.rename.EpisodeParser.nonAlphanumericPattern
import boat.rename.EpisodeParser.seasonPattern
import boat.rename.EpisodeParser.seasonPattern2
import boat.rename.FileAttributes.decodeIfEncoded
import java.io.File

/**
 * Tells names and files that are no title: articles, tags, urls, numbers, extras, samples and files that are not media.
 */
object JunkDetector {

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

    fun isAuxiliaryPathOrFile(file: File, cleanedFileName: String): Boolean =
        file.extension.lowercase() in auxiliaryNonMediaExtensions ||
                hasAuxiliaryKeywordInName(file, cleanedFileName) ||
                isBelowAuxiliaryFolder(file)

    // "Show - Deleted Scenes.mkv", unless it is an episode of a season
    private fun hasAuxiliaryKeywordInName(file: File, cleanedFileName: String): Boolean {
        val lowerCleaned = cleanedFileName.lowercase()
        if (auxiliaryFileKeywords.none { lowerCleaned.contains(it) }) return false
        val fileNameWithoutExt = file.nameWithoutExtension
        return seasonPattern.find(fileNameWithoutExt) == null && seasonPattern2.find(fileNameWithoutExt) == null
    }

    // up to 6 folders above the file, unless the folder is a pack with extras ("Show + Extras")
    private fun isBelowAuxiliaryFolder(file: File): Boolean =
        generateSequence(file.parentFile) { it.parentFile }.take(6).any { folder ->
            val folderName = decodeIfEncoded(folder.name).lowercase().trim()
            val normalized = folderName.replace("_", " ").replace(".", " ").replace("-", " ")
            val tokens = normalized.split(nonAlphanumericPattern).filter { it.isNotBlank() }

            val isPackFolder = folderName.contains("+") || normalized.contains("with extras") || normalized.contains("and extras") ||
                    normalized.matches(packWithExtrasPattern)

            !isPackFolder && (
                    auxiliaryFolderKeywords.contains(folderName) || auxiliaryFolderKeywords.contains(normalized) ||
                            (tokens.size <= 3 && tokens.any { auxiliaryFolderKeywords.contains(it) })
                    )
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
}
