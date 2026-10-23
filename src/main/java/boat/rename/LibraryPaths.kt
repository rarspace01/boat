package boat.rename

import boat.info.MediaItem
import boat.info.MediaType
import boat.rename.EpisodeParser.isOpeningEndingOrOva
import boat.rename.FileAttributes.decodeIfEncoded
import boat.rename.FileAttributes.extractCodec
import boat.rename.FileAttributes.extractResolution
import boat.rename.FileAttributes.extractSeriesSubtitleLanguage
import boat.rename.FileAttributes.extractSubtitleLanguage
import boat.rename.FileAttributes.isMovieExtra
import boat.rename.FileAttributes.subtitleExtensions
import boat.rename.LibraryFolders.extraNameInFeaturettes
import boat.utilities.StringUtil
import java.io.File

/**
 * Where a file goes in the library: the folder structure and the new file name.
 */
object LibraryPaths {
    // the folder of a show Plex lists as featurettes
    const val featurettesFolder = "Featurettes"

    // double episodes like "Lost.S04E13E14 'There's No Place Like Home - Part 2'", not "S04E05-1960s.Week"
    val doubleEpisodePattern = "(?i)(?<![a-z0-9])S[0-9]{1,2}[\\s._-]?E([0-9]{1,4})(?:-?E|-)([0-9]{1,4})(?![0-9a-z])".toRegex()

    // segments of an episode like "Kim Possible - S02 E06b - Adventures In Rufus-Sitting"
    val episodePartPattern = "(?i)(?<![a-z0-9])S[0-9]{1,2}[\\s._-]?E[0-9]{1,4}([a-z])(?![a-z0-9])".toRegex()

    // openings, endings and OVAs go to the extras of the show, OP and ED only in upper case, they're words otherwise ("Ed, Edd n Eddy")
    // the folder of a show Plex lists as "Other" extras
    const val otherExtrasFolder = "Other"

    val moviePartPattern = "(?i)(?<=^|[\\s._\\-\\[(])(?:cd|disc|disk|part|pt)[\\s._\\-]?([0-9]{1,2})(?=$|[\\s._\\-\\])])".toRegex()

    val invalidFileCharsPattern = Regex("[\\\\/:*?\"<>|]")

    fun getTargetFile(
        file: File,
        mediaResult: MediaItem,
        extractedName: String,
        season: Int?,
        episode: Int?,
        basePath: String? = null
    ): String {
        val base = basePath ?: (System.getProperty("user.home") + "/pfdb/PFDB")
        val idPart = if (!mediaResult.id.isNullOrBlank()) " ${mediaResult.id}" else ""
        if (mediaResult.type == MediaType.Movie) {
            return getMovieTargetFile(file, mediaResult, base, idPart)
        }
        val firstPart = base + "/" + getMediaTypeFolderString(mediaResult) +
                "/" + StringUtil.deductFirstTorrentLetter(getSafeTitle(mediaResult)) + "/${getSafeTitle(mediaResult)}$idPart"
        if (isOpeningEndingOrOva(file)) {
            return "$firstPart/$otherExtrasFolder/${file.name}"
        }
        extraNameInFeaturettes(file)?.let { return "$firstPart/$featurettesFolder/$it" }
        val optionalSeasonFolder = if(season != null){
            "/Season $season"
        } else {
            ""
        }
        val optionalFileNaming = if(episode != null){
            val seasonStr = String.format("%02d", season ?: 1)
            val episodeStr = String.format("%02d", episode)
            val resolution = extractResolution(file)
            val codec = extractCodec(file)
            val extraInfo = listOfNotNull(resolution, codec).joinToString(".")
            val extraSuffix = if (extraInfo.isNotEmpty()) ".$extraInfo" else ""
            // subtitles of several languages for the same episode
            val languageSuffix = if (file.extension.lowercase() in subtitleExtensions) extractSeriesSubtitleLanguage(file)?.let { ".$it" } ?: "" else ""
            val partSuffix = episodePartPattern.find(decodeIfEncoded(file.nameWithoutExtension))?.groupValues?.get(1)?.lowercase()
                ?: doubleEpisodePattern.find(decodeIfEncoded(file.nameWithoutExtension))
                    ?.takeIf { it.groupValues[1].toInt() == episode && it.groupValues[2].toInt() == episode + 1 }
                    ?.let { "-E" + String.format("%02d", episode + 1) }
                ?: ""
            // a title like "hak5" in "Hak5 {tvdb-79008}" counts as junk, the name must not start with a dot
            val episodeTitle = extractedName.ifBlank { getSafeTitle(mediaResult) }
            "/${episodeTitle}.S${seasonStr}E${episodeStr}$partSuffix$extraSuffix$languageSuffix"
        } else if (season != null || mediaResult.type == MediaType.Series) {
            "/${file.nameWithoutExtension}"
        } else {
            ""
        }
        val fileExtension = file.extension.takeIf { it.isNotEmpty() }?.let { ".$it" } ?: ""
        return firstPart + optionalSeasonFolder+optionalFileNaming+fileExtension
    }

    /**
     * Movies are stored flat in their letter folder as "Title (Year) {id}.resolution.codec.ext". Subtitles with a
     * language code follow the movie name, extras/samples keep their original name so they don't overwrite the movie.
     */
    private fun getMovieTargetFile(file: File, mediaResult: MediaItem, base: String, idPart: String): String {
        val safeTitle = getSafeTitle(mediaResult)
        val folder = base + "/" + getMediaTypeFolderString(mediaResult) + "/" + StringUtil.deductFirstTorrentLetter(safeTitle)
        val extension = file.extension.lowercase()
        val isSubtitle = extension in subtitleExtensions
        val subtitleLanguage = if (isSubtitle) extractSubtitleLanguage(file) else null
        if (isMovieExtra(file) || (isSubtitle && subtitleLanguage == null)) {
            return "$folder/${file.name}"
        }
        val yearPart = mediaResult.year?.let { " ($it)" } ?: ""
        val mediaInfo = listOfNotNull(extractResolution(file), extractCodec(file))
        val mediaInfoSuffix = if (mediaInfo.isNotEmpty()) "." + mediaInfo.joinToString(".") else ""
        val partSuffix = moviePartPattern.find(file.nameWithoutExtension)?.let { " - pt${it.groupValues[1].toInt()}" } ?: ""
        val languageSuffix = subtitleLanguage?.let { ".$it" } ?: ""
        val fileExtension = if (file.extension.isNotEmpty()) ".${file.extension}" else ""
        return "$folder/$safeTitle$yearPart$idPart$mediaInfoSuffix$partSuffix$languageSuffix$fileExtension"
    }

    fun getSafeTitle(mediaResult: MediaItem): String {
        return getSafeTitle(mediaResult.title)
    }

    fun getSafeTitle(title: String): String {
        // a leading dot like in ".hack//G.U. Trilogy" would make a hidden file, the apostrophe is not part of a name in the library
        return title.replace("//", " ").replace(invalidFileCharsPattern, "").replace("'", "").trimStart('.', ' ')
    }

    /**
     * A name from a torrent or hoster as one file or folder name: no separators, no `..` and no leading dot, so it
     * can't point anywhere else in the remote.
     */
    fun getSafeName(name: String, fallback: String = "download"): String =
        getSafeTitle(name.replace('/', ' ').replace('\\', ' ')).trim().ifEmpty { fallback }

    /** A relative path from a torrent ("Folder/Sub/File.mkv") with every part made safe, `.`, `..` and empty parts left out. */
    fun getSafeRelativePath(path: String, fallback: String = "download"): String =
        path.split('/', '\\').filter { it.isNotBlank() && it != "." && it != ".." }.map { getSafeName(it, fallback) }
            .joinToString("/").ifEmpty { fallback }

    private fun getMediaTypeFolderString(mediaResult: MediaItem): String = when (mediaResult.type) {
        MediaType.Movie -> "Movies"
        MediaType.Series -> "Series-Shows"
        MediaType.Documentary -> "Documentaries"
        else -> "transfer"
    }
}
