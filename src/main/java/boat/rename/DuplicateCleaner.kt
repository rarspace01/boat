package boat.rename

import boat.rename.FileAttributes.extractCodec
import boat.rename.FileAttributes.extractResolution
import boat.rename.FileAttributes.isMovieExtra
import boat.rename.FileAttributes.subtitleExtensions
import boat.rename.FileAttributes.videoExtensions
import boat.utilities.LoggerDelegate
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Locale

/**
 * Finds the same movie or episode more than once in the (renamed) library and deletes the worse versions.
 *
 * Two files are the same if they have the same remote media id (`{tmdb-27205}`, `{tvdb-79008}`) and, for episodes, the
 * same season and episode (parts of a movie "- pt1" and of an episode "S01E06b" or "S01E01-E02" are different files).
 * Files without id, extras and everything which is not a video are never touched, so the library has to be renamed
 * first ([boat.ContentRenamer]).
 *
 * The better version has, in this order: the higher resolution, the better codec (AV1/HEVC before H.264 before the
 * old ones), the bigger file. The name of the library file only tells resolution and codec, there is no source (remux,
 * web) in it. If all are the same the first name is kept. The subtitles next to a deleted version are moved to the
 * version which stays if it has none for that language, otherwise they are deleted with it.
 *
 * Does not know about the language of a version: a German and an English version of the same episode are duplicates
 * to it. Run it as a test run first, it only writes what it would delete to the log.
 */
class DuplicateCleaner {

    private val logger by LoggerDelegate()

    /** A video file of the library with what decides which version is the better one. */
    class Version(val file: File) {
        val height: Int = heightOf(extractResolution(file))
        val codecRank: Int = codecRankOf(extractCodec(file))
        val size: Long = file.length()

        fun description(): String = listOfNotNull(extractResolution(file) ?: "?", extractCodec(file) ?: "?").joinToString(" ") +
                ", " + String.format(Locale.ROOT, "%.2f GB", size / 1024.0 / 1024.0 / 1024.0)
    }

    class DuplicateGroup(val key: String, val keep: Version, val remove: List<Version>)

    /**
     * Looks for duplicates below [path] and deletes the worse versions, unless [isTestRun]. Returns the groups which
     * were found, what happens is written to [logFile] if there is one.
     */
    fun processFilesFromDirectory(path: String, logFile: File? = null, isTestRun: Boolean = true): List<DuplicateGroup> {
        val groups = findDuplicates(path)
        val writer = logFile?.let { BufferedWriter(FileWriter(it, true)) }
        try {
            groups.forEach { group ->
                writer?.write("Duplicates of ${group.key}:\n")
                writer?.write("KEEP '${group.keep.file.path}' (${group.keep.description()})\n")
                group.remove.forEach { loser ->
                    deleteVersion(group.keep, loser, isTestRun, writer)
                }
            }
            writer?.write("${groups.size} duplicates found, ${groups.sumOf { it.remove.size }} worse versions" +
                    if (isTestRun) " (test run, nothing was deleted)\n" else " deleted\n")
        } finally {
            writer?.flush()
            writer?.close()
        }
        return groups
    }

    /** The files which are there more than once below [path], with the version to keep. */
    fun findDuplicates(path: String): List<DuplicateGroup> {
        val root = File(path)
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown().filter { it.isFile }
            .mapNotNull { file -> duplicateKey(file)?.let { key -> key to file } }
            .groupBy({ it.first }, { it.second })
            .filter { it.value.size > 1 }
            .map { (key, files) ->
                val ranked = files.map { Version(it) }.sortedWith(BEST_FIRST)
                DuplicateGroup(key, ranked.first(), ranked.drop(1))
            }
            .sortedBy { it.key }
            .toList()
    }

    private fun deleteVersion(keep: Version, loser: Version, isTestRun: Boolean, writer: BufferedWriter?) {
        val sidecars = sidecarsOf(loser.file)
        val keepBaseName = keep.file.nameWithoutExtension
        val loserBaseName = loser.file.nameWithoutExtension
        writer?.write("DELETE '${loser.file.path}' (${loser.description()})${if (isTestRun) " [test run]" else ""}\n")
        if (!isTestRun) {
            loser.file.delete()
        }
        sidecars.forEach { sidecar ->
            val rest = sidecar.name.removePrefix(loserBaseName)
            val target = File(keep.file.parentFile, keepBaseName + rest)
            val isSubtitle = sidecar.extension.lowercase() in subtitleExtensions
            if (isSubtitle && !target.exists()) {
                writer?.write("MOVE '${sidecar.path}' -> '${target.path}'${if (isTestRun) " [test run]" else ""}\n")
                if (!isTestRun && !sidecar.renameTo(target)) {
                    logger.warn("could not move {} to {}", sidecar, target)
                }
            } else {
                writer?.write("DELETE '${sidecar.path}'${if (isTestRun) " [test run]" else ""}\n")
                if (!isTestRun) sidecar.delete()
            }
        }
        if (!isTestRun) deleteEmptyFolders(loser.file.parentFile, keep.file.parentFile)
    }

    /**
     * The files which belong to a video: same name and another extension or a language in front of it
     * ("Movie.1080p.x264.en.srt" for "Movie.1080p.x264.mkv"). A file belongs to the video with the longest name, so
     * the subtitles of "Movie.1080p.x264.mkv" aren't taken for those of "Movie.1080p.mkv".
     */
    private fun sidecarsOf(video: File): List<File> {
        val folder = video.parentFile ?: return emptyList()
        val files = folder.listFiles()?.filter { it.isFile } ?: return emptyList()
        val baseNames = files.filter { it.extension.lowercase() in videoExtensions }.map { it.nameWithoutExtension }
        val baseName = video.nameWithoutExtension
        return files.filter { file ->
            file != video && file.extension.lowercase() !in videoExtensions && file.name.startsWith("$baseName.") &&
                    baseNames.filter { file.name.startsWith("$it.") }.maxByOrNull { it.length } == baseName
        }
    }

    private fun deleteEmptyFolders(start: File?, stopAt: File?) {
        var folder = start
        while (folder != null && folder != stopAt && folder.isDirectory && folder.list()?.isEmpty() == true) {
            logger.info("RMDIR {}", folder)
            if (!folder.delete()) return
            folder = folder.parentFile
        }
    }

    companion object {
        private val BEST_FIRST = compareByDescending<Version> { it.height }
            .thenByDescending { it.codecRank }
            .thenByDescending { it.size }
            .thenBy { it.file.path }

        private val episodePattern = "(?i)(?<![a-z0-9])S(\\d{1,3})E(\\d{1,4})(-E\\d{1,4}|[a-z])?(?![0-9a-z])".toRegex()

        /** 1080p -> 1080, 1080i -> 1080, 4k -> 2160, unknown -> 0 */
        internal fun heightOf(resolution: String?): Int = when {
            resolution == null -> 0
            resolution.equals("2k", ignoreCase = true) -> 1080
            resolution.equals("4k", ignoreCase = true) -> 2160
            resolution.equals("8k", ignoreCase = true) -> 4320
            else -> resolution.dropLast(1).toIntOrNull() ?: 0
        }

        /** The newer codecs are better at the same resolution. */
        internal fun codecRankOf(codec: String?): Int = when (codec?.lowercase()) {
            "av1", "hevc", "x265", "h265" -> 3
            "x264", "h264", "avc", "vp9" -> 2
            "xvid", "divx" -> 1
            else -> 0
        }

        /**
         * What makes video files the same movie or episode, null for everything which is not compared: no video, no
         * remote media id, extras, files outside of the movies and shows of the library.
         */
        internal fun duplicateKey(file: File): String? {
            if (file.extension.lowercase() !in videoExtensions) return null
            val folders = generateSequence(file.parentFile) { it.parentFile }.map { it.name }.toList()
            return when {
                "Movies" in folders -> movieKey(file)
                "Series-Shows" in folders -> episodeKey(file)
                else -> null
            }
        }

        private fun movieKey(file: File): String? {
            if (isMovieExtra(file)) return null
            val id = remoteMediaIdPattern.findAll(file.name).lastOrNull()?.value?.lowercase() ?: return null
            val part = LibraryPaths.moviePartPattern.find(file.nameWithoutExtension)?.groupValues?.get(1)?.toInt() ?: 0
            return "movie $id pt$part"
        }

        private fun episodeKey(file: File): String? {
            // the id is in the name of the folder of the show
            val id = remoteMediaIdPattern.findAll(file.parentFile?.absolutePath ?: return null).lastOrNull()?.value?.lowercase() ?: return null
            val match = episodePattern.find(file.nameWithoutExtension) ?: return null
            val season = match.groupValues[1].toInt()
            val episode = match.groupValues[2].toInt()
            return "series $id S%02dE%02d%s".format(season, episode, match.groupValues[3].lowercase())
        }
    }
}
