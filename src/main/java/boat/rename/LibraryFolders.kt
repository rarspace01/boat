package boat.rename

import boat.rename.EpisodeParser.bracketPattern
import boat.rename.EpisodeParser.dashEpisodePattern
import boat.rename.EpisodeParser.explicitEpisodeLookbehindPattern
import boat.rename.EpisodeParser.isOpeningEndingOrOva
import boat.rename.EpisodeParser.nonAlphanumericPattern
import boat.rename.EpisodeParser.versionPattern
import boat.rename.FileAttributes.decodeIfEncoded
import boat.rename.FileAttributes.isMovieExtra
import boat.rename.FileAttributes.movieExtraPattern
import boat.rename.FileAttributes.videoExtensions
import boat.rename.MediaNameParser.extractName
import java.io.File

/**
 * What the folders of the library say about a file: group folders (letters, ranges), extras folders and the show a movie or an extra lives in.
 */
object LibraryFolders {

    /**
     * Letter, range (0-9) and category folders group the library, they never carry a title.
     */
    fun isLibraryGroupFolder(folder: File): Boolean {
        val name = decodeIfEncoded(folder.name).trim()
        val lower = name.lowercase()
        val normalized = lower.replace("_", " ").replace(".", " ").replace("-", " ").trim()
        return name.length <= 1 || name.all { !it.isLetterOrDigit() } || lower.matches(rangeSinglePattern) || lower.matches(rangeLetterPattern) ||
                lower in categoryFolderNames || normalized in categoryFolderNames
    }

    /**
     * Series in the movies folder, detected by markers a movie title doesn't carry: S01E01 or 1x01 in the file name, a
     * season folder or several numbered episodes of the same show next to each other. A plain number like in
     * "Apitig 13" doesn't count.
     */
    fun hasSeriesMarkers(file: File): Boolean {
        val fileName = decodeIfEncoded(file.nameWithoutExtension)
        if (seasonEpisodeMarkerPattern.containsMatchIn(fileName) || seasonXEpisodeMarkerPattern.containsMatchIn(fileName)) return true

        // files directly in a letter folder, e.g. Suzo.Wnat.Episode.1, have no show folder
        val showFolders = generateSequence(file.parentFile) { it.parentFile }.take(5).takeWhile { !isLibraryGroupFolder(it) }.toList()
        if (showFolders.isEmpty()) return false
        if (showFolders.any { seasonFolderMarkerPattern.containsMatchIn(decodeIfEncoded(it.name)) }) return true

        // e.g. "Tapaz.Of.Linabi.Wmape.EP1", "Sapidaf Catokuto - Ep. 20 - Elegy", "Pekurufu ON TETOMETAKU - 47 (BDRip)" or "Govopatena Gofe 01"
        val episodeMarker = episodeMarker(fileName) ?: return false
        val episodeMarkers = file.parentFile.listFiles()
            ?.filter { it.isFile && it.name != file.name && it.extension.lowercase() in videoExtensions }
            ?.mapNotNull { sibling -> episodeMarker(decodeIfEncoded(sibling.nameWithoutExtension))?.takeIf { it.showName == episodeMarker.showName } }
            .orEmpty()
            .plus(episodeMarker)
        // plain numbers only count zero-padded, "Opave's 11", "Ocean's 12" and "Ocean's 13" are movies
        val hasEpisodeNotation = episodeMarkers.any { it.isExplicit || it.number.startsWith("0") }
        return hasEpisodeNotation && episodeMarkers.map { it.number.toInt() }.toSet().size >= 3
    }

    /**
     * Removes the episode number [extractName] keeps for plain numbered episodes, e.g. "Govopatena Gofe 10".
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
     * doesn't count, packs are named like "Gubu Otoce Season 1 + Extras".
     */
    fun isInExtrasFolder(file: File): Boolean {
        val folders = generateSequence(file.parentFile) { it.parentFile }.take(6).takeWhile { !isLibraryGroupFolder(it) }.toList()
        return folders.dropLast(1).any { folder ->
            val name = decodeIfEncoded(folder.name)
            movieExtraPattern.containsMatchIn(name) || creditlessFolderPattern.matches(name.trim())
        }
    }

    private fun containerName(folderName: String) =
        folderName.lowercase().replace(bracketPattern, " ").replace("_", " ").replace(".", " ").substringAfterLast(" - ")
            .replace(whitespacePattern, " ").trim()

    private fun isExtrasFolder(folderName: String) = containerName(folderName).matches(extrasFolderPattern)

    fun isExtrasContainer(folderName: String) = containerName(folderName).let { it.matches(extrasFolderPattern) || it.matches(subtitleFolderPattern) }

    /**
     * Name of an extra in the featurettes of its show, with the folders below the outermost extras folder so extras of
     * different folders don't collide, e.g. "Season 2 - The Lif Luza - The Lif Luza 01.mkv" for
     * "Turo.Pimes.(1990)/Featurettes/Season 2/The Lif Luza/The Lif Luza 01.mkv". Null if the file isn't an extra.
     */
    fun extraNameInFeaturettes(file: File): String? {
        val folders = generateSequence(file.parentFile) { it.parentFile }.takeWhile { !isLibraryGroupFolder(it) }.toList()
        val outermostExtrasFolder = folders.indexOfLast { isExtrasFolder(decodeIfEncoded(it.name)) }
        // extras named after their folder like "Season 03 Extras - Inside The X-Favuc.avi"
        val fileName = decodeIfEncoded(file.nameWithoutExtension)
        if (outermostExtrasFolder < 0 && fileName.contains(" - ") && isExtrasFolder(fileName.substringBefore(" - "))) return decodeIfEncoded(file.name)
        if (outermostExtrasFolder < 0) return null
        val subFolders = folders.take(outermostExtrasFolder).reversed().map { decodeIfEncoded(it.name) }
        return (subFolders + decodeIfEncoded(file.name)).joinToString(" - ")
    }

    /**
     * Movies in a series folder go to the movies, detected by a movie in the file name or in one of the two folders
     * above, e.g. "Nikulo/3.Nikulo Movies/1 - Noget Cakez in the Lufu of Seme.mkv" or "Asug Fabe/Movie (2005)/Asug Fabe.mp4".
     * Packs of episodes and movies like "Ecuf of the Edaz 1-11+Movie 1-2 Complete" don't make their episodes movies.
     */
    fun isMovieInSeriesFolder(file: File): Boolean {
        val fileName = decodeIfEncoded(file.nameWithoutExtension)
        if (isOpeningEndingOrOva(file) || pilotPattern.containsMatchIn(fileName) || isMovieExtra(file)) return false
        if (seasonEpisodeMarkerPattern.containsMatchIn(fileName) || seasonXEpisodeMarkerPattern.containsMatchIn(fileName) ||
            seasonInNamePattern.containsMatchIn(fileName)
        ) return false
        // files in a show of the library were put there on purpose, e.g. "The Osugol (US) {tvdb-73244}/Season 7/Tipici Lozuk Macatadu The Movie.mkv"
        if (remoteMediaIdPattern.containsMatchIn(decodeIfEncoded(file.absolutePath))) return false
        val folders = generateSequence(file.parentFile) { it.parentFile }.takeWhile { !isLibraryGroupFolder(it) }.map { decodeIfEncoded(it.name) }.toList()
        if (folders.any { isExtrasContainer(it) }) return false
        return movieFileWordPattern.containsMatchIn(fileName) ||
                folders.take(2).any { movieFolderWordPattern.containsMatchIn(it) && !episodesAndMoviesPackPattern.containsMatchIn(it) }
    }
}
