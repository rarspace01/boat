package boat.rename

import boat.rename.EpisodeParser.bracketPattern
import boat.rename.EpisodeParser.bracketedEpisodePattern
import boat.rename.EpisodeParser.dashEpisodePattern
import boat.rename.EpisodeParser.doubleDashEpisodePattern
import boat.rename.EpisodeParser.episodeWithTitlePattern
import boat.rename.EpisodeParser.seasonPattern
import boat.rename.EpisodeParser.seasonPattern2
import boat.rename.EpisodeParser.specialMarkerIndex
import boat.rename.EpisodeParser.standaloneEpisodePattern
import boat.rename.EpisodeParser.versionPattern

/**
 * Cuts a file or folder name down to the title: removes brackets, release groups, tags, years and the season and episode markers.
 */
object NameCleaner {

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
}
