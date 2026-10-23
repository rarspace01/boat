package boat

import boat.info.MediaItem
import boat.info.MediaProxyService
import boat.info.MediaType
import boat.rename.EpisodeParser
import boat.rename.FileAttributes
import boat.rename.FileAttributes.isMovieFile
import boat.rename.JunkDetector
import boat.rename.JunkDetector.getSkipReason
import boat.rename.LibraryFolders
import boat.rename.LibraryFolders.isInExtrasFolder
import boat.rename.LibraryPaths
import boat.rename.MediaNameParser
import boat.rename.MovieMatcher
import boat.rename.MovieNameParser
import boat.rename.MovieNameParser.normalizeMovieTitle
import boat.rename.NameCleaner
import boat.rename.nonMediaExtensions as renameNonMediaExtensions
import boat.rename.paddingFilePattern as renamePaddingFilePattern
import boat.rename.remoteMediaIdPattern as renameRemoteMediaIdPattern
import boat.utilities.LoggerDelegate
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import kotlin.io.path.moveTo
import org.springframework.stereotype.Service

/**
 * Renames and moves the media files of a directory into the library. The work is done by the classes in
 * `boat.rename`, from the file name to the new path:
 * [JunkDetector] (names and files that are no title), [NameCleaner] (title of a name), [EpisodeParser] (seasons and
 * episodes), [FileAttributes] (resolution, codec, subtitle language), [LibraryFolders] (what the folders say),
 * [MediaNameParser] (the show of a series file), [MovieNameParser] (title and year of a movie), [MovieMatcher]
 * (finding the movie in the media proxy) and [LibraryPaths] (the new path).
 * The functions of those which are used from outside are available here as well.
 */
@Service
class ContentRenamer(val mediaProxyService: MediaProxyService) {

    private val logger by LoggerDelegate()

    private val movieMatcher = MovieMatcher(mediaProxyService)

    companion object {
        val remoteMediaIdPattern = renameRemoteMediaIdPattern
        val paddingFilePattern = renamePaddingFilePattern
        val nonMediaExtensions = renameNonMediaExtensions

        // Plex names extras behind a dash, "Uveki.Goba/Abaconep Tico-other.mkv"
        val plexExtraSuffixPattern = "(?i)-(?:behindthescenes|deleted|featurette|interview|scene|short|trailer|other)$".toRegex()
    }

    fun extractName(file: File): String = MediaNameParser.extractName(file)

    fun extractEpisode(file: File, mediaItem: MediaItem? = null): Int? = EpisodeParser.extractEpisode(file, mediaItem)

    fun isJunk(name: String): Boolean = JunkDetector.isJunk(name)

    fun removeEpisodeNumber(seriesName: String, file: File): String = LibraryFolders.removeEpisodeNumber(seriesName, file)

    fun extractSeason(file: File): Int? = EpisodeParser.extractSeason(file)

    fun getTargetFile(
        file: File,
        mediaResult: MediaItem,
        extractedName: String,
        season: Int?,
        episode: Int?,
        basePath: String? = null
    ): String = LibraryPaths.getTargetFile(file, mediaResult, extractedName, season, episode, basePath)

    fun extractYear(fullName: String): Int? = MediaNameParser.extractYear(fullName)

    fun getSafeTitle(mediaResult: MediaItem): String = LibraryPaths.getSafeTitle(mediaResult)

    fun getSafeTitle(title: String): String = LibraryPaths.getSafeTitle(title)

    fun isMovieInSeriesFolder(file: File): Boolean = LibraryFolders.isMovieInSeriesFolder(file)

    fun hasSeriesMarkers(file: File): Boolean = LibraryFolders.hasSeriesMarkers(file)

    fun extractSeasonAndEpisode(file: File, mediaItem: MediaItem): Pair<Int?, Int?> = EpisodeParser.extractSeasonAndEpisode(file, mediaItem)

    fun extractMovieNameAndYear(file: File): Pair<String, Int?> = MovieNameParser.extractMovieNameAndYear(file)

    fun removeReleaseGroups(name: String): String = NameCleaner.removeReleaseGroups(name)

    fun parseMovieName(rawName: String): Pair<String, Int?> = MovieNameParser.parseMovieName(rawName)

    fun extractResolution(file: File): String? = FileAttributes.extractResolution(file)

    fun extractMovieInSeriesNameAndYear(file: File): Pair<String, Int?> = MovieNameParser.extractMovieInSeriesNameAndYear(file)

    fun decodeIfEncoded(name: String): String = FileAttributes.decodeIfEncoded(name)

    /**
     * Renames and moves the media files below [path] into the library at [basePath] (default ~/pfdb/PFDB).
     * With [skipFilesWithRemoteId] (the default) files that already carry a remote media id like `{tmdb-123}` are left
     * alone, so no lookups are made for the files that were renamed before. Turn it off to check them again.
     */
    fun processFilesFromDirectory(
        path: String, logFile: File? = null, isTestRun: Boolean = true, basePath: String? = null, skipFilesWithRemoteId: Boolean = true
    ) {
        val writer = logFile?.let { BufferedWriter(FileWriter(it, true)) }
        try {
            processFilesFromDirectoryInternal(path, writer, isTestRun, basePath, HashSet(), skipFilesWithRemoteId)
        } finally {
            writer?.flush()
            writer?.close()
            mediaProxyService.saveCache()
        }
    }

    /**
     * [assignedTargets] are the targets of this run, a test run reports the files which would collide as well. True if
     * a file was moved out of [path] (or a folder below it); a folder which is empty afterwards is removed.
     */
    private fun processFilesFromDirectoryInternal(
        path: String, logWriter: BufferedWriter?, isTestRun: Boolean, basePath: String?, assignedTargets: MutableSet<String>,
        skipFilesWithRemoteId: Boolean
    ): Boolean {
        var movedAny = false
        val files = File(path).listFiles()
        files?.let { fileList ->
            for (file in fileList) {
                if (file.isFile) {
                    val fullName = file.absolutePath
                    var season = extractSeason(file)
                    var episode = extractEpisode(file)

                    val isSeriesPath = file.absolutePath.contains("/Series-Shows/", ignoreCase = true) ||
                            file.absolutePath.contains("\\Series-Shows\\", ignoreCase = true) ||
                            file.absolutePath.contains("/Series/", ignoreCase = true) ||
                            file.absolutePath.contains("/TV/", ignoreCase = true) ||
                            file.absolutePath.contains("/Shows/", ignoreCase = true) ||
                            file.absolutePath.contains("/Anime/", ignoreCase = true) ||
                            file.absolutePath.contains("/Cartoons/", ignoreCase = true)

                    val isMoviePath = file.absolutePath.contains("/Movies/", ignoreCase = true) ||
                            file.absolutePath.contains("\\Movies\\", ignoreCase = true) ||
                            file.absolutePath.contains("/Films/", ignoreCase = true) ||
                            file.absolutePath.contains("/Documentaries/", ignoreCase = true)

                    // the folder wins over season/episode detection, titles like "Apitig 13" would be detected as episode,
                    // only explicit series markers move a file from the movies folder to the series
                    val isSeriesInMoviePath = isMoviePath && !isSeriesPath && hasSeriesMarkers(file)
                    val isMovieInSeriesPath = isSeriesPath && !isMoviePath && isMovieInSeriesFolder(file)
                    val mediaIndicator = when {
                        isMovieInSeriesPath -> MediaType.Movie
                        isSeriesPath || isSeriesInMoviePath -> MediaType.Series
                        isMoviePath -> MediaType.Movie
                        episode != null || season != null -> MediaType.Series
                        else -> null
                    }

                    // extract Movie or TV show name from full path
                    val (extractedName, year) = if (isMovieInSeriesPath) {
                        extractMovieInSeriesNameAndYear(file)
                    } else if (mediaIndicator == MediaType.Movie) {
                        // a year from the future is part of a hash like [6D2027D6]
                        val currentYear = java.time.Year.now().value
                        extractMovieNameAndYear(file).let { (name, movieYear) -> Pair(name, movieYear ?: extractYear(fullName)?.takeIf { it <= currentYear }) }
                    } else {
                        val seriesName = if (isSeriesInMoviePath) removeEpisodeNumber(extractName(file), file) else extractName(file)
                        Pair(seriesName, extractYear(fullName))
                    }

                    val skipReason = getSkipReason(file)
                        ?: if (isMoviePath && (isInExtrasFolder(file) || plexExtraSuffixPattern.containsMatchIn(file.nameWithoutExtension))) "extras" else if (mediaIndicator == MediaType.Movie && !isMovieFile(file)) "no movie file" else null
                    if (skipReason != null) {
                        val logLine = "F:'$fullName'→'$extractedName'→'null' (Y: $year, S: $season, E: $episode)  skipped, $skipReason"
                        logWriter?.write(logLine + "\n")
                        continue
                    }

                    // already renamed files carry their remote media id, the one closest to the file wins
                    val remoteMediaId = remoteMediaIdPattern.findAll(fullName).lastOrNull()?.value
                    if (skipFilesWithRemoteId && remoteMediaId != null) {
                        logWriter?.write("F:'$fullName'→'$extractedName'→'null' (Y: $year, S: $season, E: $episode)  skipped, already has $remoteMediaId\n")
                        continue
                    }
                    val mediaResult = remoteMediaId?.let { mediaProxyService.findByRemoteMediaId(it, extractedName, mediaIndicator) }
                        ?: if (isMovieInSeriesPath && year == null) movieMatcher.searchMovieByTitle(extractedName)
                        else movieMatcher.searchMediaForFile(extractedName, year, mediaIndicator, (isSeriesPath && !isMovieInSeriesPath) || isSeriesInMoviePath)

                    if (mediaResult != null) {
                        val seasonAndEpisode = extractSeasonAndEpisode(file, mediaResult)
                        season = seasonAndEpisode.first
                        episode = seasonAndEpisode.second
                    }

                    val idString = mediaResult?.id ?: ""
                    val isSkippedMovieFile = mediaResult?.type == MediaType.Movie && !isMovieFile(file)
                    val isUnsureMovie = mediaResult?.type == MediaType.Movie && listOfNotNull(mediaResult.title, mediaResult.originalTitle).none {
                        normalizeMovieTitle(it) == normalizeMovieTitle(extractedName)
                    }
                    val logLine =
                        "F:'$fullName'→'$extractedName'→'$mediaResult' (Y: $year, S: $season, E: $episode) $idString" +
                                if (isSkippedMovieFile) " skipped, no movie file" else if (isUnsureMovie) " unsure, title differs" else ""
                    mediaResult?.takeUnless { isSkippedMovieFile }?.let {
                        val targetFile = getTargetFile(file, mediaResult, extractedName, season, episode, basePath)
                        // actual move
                        // Create the parent directories if they don't exist
                        val newFile = File(targetFile)
                        val isSameFile = file.toPath().equals(newFile.toPath())
                        if (isSameFile) assignedTargets.add(newFile.absolutePath)
                        // nothing is ever overwritten, neither a file of the library nor one moved before in this run, e.g.
                        // two editions of a movie, two releases of an episode or the same episode number from two notations
                        val isTargetTaken = !isSameFile && (newFile.exists() || !assignedTargets.add(newFile.absolutePath))
                        if (isTargetTaken) {
                            logWriter?.write("Not moved, target exists:\n'$file'\n'$newFile'\n")
                        } else if(!isTestRun && !isSameFile) {
                            newFile.parentFile?.mkdirs()
                            // Then move and rename
                            try {
                                file.toPath().moveTo(newFile.toPath(), overwrite = false)
                                movedAny = true
                                logWriter?.write("Moved:\n'$file'\n'$newFile'\n")
                            } catch (e:Exception) {
                                logger.error("Error moving file [{}]: {}", file, e.message)
                                logWriter?.write("Failed to move:\n'$file'\n'$newFile'\n")
                            }

                        }
                    }
                    logWriter?.write(logLine + "\n")
                } else {
                    if (processFilesFromDirectoryInternal(file.absolutePath, logWriter, isTestRun, basePath, assignedTargets, skipFilesWithRemoteId)) {
                        movedAny = true
                        // only folders the run emptied, not the empty letter folders of the library
                        if (file.listFiles()?.isEmpty() == true && file.delete()) logWriter?.write("Removed empty folder '$file'\n")
                    }
                    logger.debug("Processed directory: {}", file)
                }
            }
        }
        return movedAny
    }
}
