package boat.rename

import boat.info.MediaProxyService
import boat.rename.EpisodeParser.bracketPattern
import boat.rename.FileAttributes.decodeIfEncoded
import boat.rename.FileAttributes.movieExtraPattern
import boat.rename.JunkDetector.isJunk
import boat.rename.LibraryFolders.isInExtrasFolder
import boat.rename.LibraryFolders.isLibraryGroupFolder
import boat.rename.MediaNameParser.extractName
import boat.rename.NameCleaner.cleanName
import boat.rename.NameCleaner.removeReleaseGroups
import java.io.File

/**
 * The title and year of a movie from the name of its file.
 */
object MovieNameParser {

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
     * Splits a movie release name like "Anezo.Rukobak.2024.2160p.WEB-DL.DDP5.1" into title and year. Unlike [cleanName]
     * numbers are never taken for episodes, so "127 Hceso" or "1917.2019" keep their title. The name is cut at the
     * release year, or without one at the first release tag. A cut inside parentheses drops the whole group, e.g.
     * "Zaliludam Point (Ateropule 1970)".
     */
    fun parseMovieName(rawName: String): Pair<String, Int?> {
        val name = normalizeMovieName(rawName)
        val words = MovieWords(name)

        val yearIndex = words.releaseYearIndex()
        val title = trimMovieTitle(name.substring(0, words.titleEnd(name, yearIndex)))
        return Pair(title, yearIndex?.let { words.cores[it].toInt() })
    }

    // spaces instead of dots, underscores and plus, no release group, site prefix, brackets in front or remote media id
    private fun normalizeMovieName(rawName: String): String {
        // "!" separates like "Wnafaz - Budekiba!1990"
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
        // dash separated names like "Fupu.Eroc.the.Sopi-1974--720p-EN" or "mukadeleko-1080p", but not "Samecu-Muf"
        return name.replace(tokenPattern) { token ->
            val parts = token.value.split('-')
            val isTaggedPart = parts.size > 1 && parts.any { it.lowercase().matches(movieTechnicalTagPattern) || it.matches(movieYearPattern) }
            if (isTaggedPart) parts.joinToString(" ") else token.value
        }.replace(whitespacePattern, " ").trim()
    }

    /** The words of a movie name and where the title ends: at the release year, or at the first tag. */
    private class MovieWords(name: String) {
        val tokens = tokenPattern.findAll(name).toList()
        val cores = tokens.map { token -> token.value.trim { !it.isLetterOrDigit() }.lowercase() }

        private fun isTag(index: Int, tagPattern: Regex) = cores[index].matches(tagPattern) || cores[index].split('-').any { it.matches(tagPattern) }
        private fun isAnyTag(index: Int) = isTag(index, movieTechnicalTagPattern) || isTag(index, movieLanguageOrEditionTagPattern)

        // the first token is always part of the title, e.g. "1917" or "2012"
        private val firstTechnicalTag = (1 until tokens.size).firstOrNull { isTag(it, movieTechnicalTagPattern) } ?: tokens.size
        private val firstLanguageOrEditionTag = (1 until tokens.size).firstOrNull { isTag(it, movieLanguageOrEditionTagPattern) } ?: tokens.size

        /**
         * The index of the release year. It is in parentheses, follows a year of the title or is followed by tags,
         * otherwise it's the first one ("Wdutab Wzeki 1984 2020", "Silesa of 1984 2023 1080p", "2000 Meruc 2022 - ... of 2020").
         */
        fun releaseYearIndex(): Int? {
            val yearCandidates = (1 until firstTechnicalTag).filter { cores[it].matches(movieYearPattern) }
            return yearCandidates.lastOrNull { index ->
                val token = tokens[index].value
                (token.startsWith("(") && token.endsWith(")")) || (token.startsWith("[") && token.endsWith("]")) ||
                        cores[index - 1].matches(movieYearPattern) || (index + 1 < tokens.size && isAnyTag(index + 1))
            } ?: yearCandidates.firstOrNull()
        }

        /** The position in [name] where the title ends, before a group of parentheses which is not closed yet. */
        fun titleEnd(name: String, yearIndex: Int?): Int {
            val cutIndex = yearIndex ?: minOf(firstTechnicalTag, firstLanguageOrEditionTag)
            val cut = if (cutIndex < tokens.size) tokens[cutIndex].range.first else name.length
            val prefix = name.substring(0, cut)
            val openGroup = prefix.lastIndexOfAny(charArrayOf('(', '['))
            return if (openGroup >= 0 && prefix.indexOfAny(charArrayOf(')', ']'), openGroup) == -1) openGroup else cut
        }
    }

    private val movieTitleEdgePunctuation = charArrayOf('-', '.', ',', ';', ':', '_', '~', '+', '!', '?', '/', '\\')

    private fun trimMovieTitle(rawTitle: String): String {
        var title = rawTitle.replace(bracketPattern, " ").replace("(", " ").replace(")", " ")
            .replace(whitespacePattern, " ").trim()
        while (true) {
            val before = title
            title = title.replace(trailingMovieTagPattern, "").trimEnd(*movieTitleEdgePunctuation).trim()
            if (title == before) break
        }
        return title.trimStart(*movieTitleEdgePunctuation).trim()
    }

    /**
     * Collections like "The Tanac Cesocike Migasuki Collection (1974-2017)/2017.Lakivamolem.mkv" put the year in front.
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
     * Title and year of a movie in a series folder without its numbering, "Hzised X Hzised - Movie 1 - Pavidiv Rouge"
     * is searched as "Hzised X Hzised - Pavidiv Rouge", the year may come from its folder like "Movie (2005)".
     */
    fun extractMovieInSeriesNameAndYear(file: File): Pair<String, Int?> {
        val fileName = decodeIfEncoded(file.nameWithoutExtension).replace(leadingTrackNumberPattern, "")
        val (name, year) = parseMovieName(fileName)
        val subtitleMatch = movieSubtitlePattern.find(fileName.replace(".", " ").replace("_", " "))
        val fullName = if (subtitleMatch != null) {
            // the year stands before the number, "Ruvem ½ [1991] - Movie 01 - Big Trouble In Nekonron China" would end at it
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
