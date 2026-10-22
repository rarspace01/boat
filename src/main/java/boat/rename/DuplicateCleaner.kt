package boat.rename

import boat.rename.FileAttributes.isMovieExtra
import boat.rename.FileAttributes.subtitleExtensions
import boat.rename.FileAttributes.videoExtensions
import boat.utilities.LoggerDelegate
import boat.utilities.PropertiesHelper
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Collections
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Finds the same movie or episode more than once in the (renamed) library and deletes the worse versions.
 *
 * Two files are the same if they have the same remote media id (`{tmdb-27205}`, `{tvdb-79008}`) and, for episodes, the
 * same season and episode (parts of a movie "- pt1" and of an episode "S01E06b" or "S01E01-E02" are different files).
 * Files without id, extras and everything which is not a video are never touched, so the library has to be renamed
 * first ([boat.ContentRenamer]).
 *
 * Which version is better is not taken from the file names (they often have no resolution or codec) but from the
 * video itself, read with ffprobe ([FfprobeVideoProbe]): the higher resolution first. Then, with [Ranking.BITRATE_FIRST]
 * (the default) the higher bitrate, which keeps the most material for a later re-encode, and the better codec (AV1/HEVC
 * before H.264 before the old ones) only if that is the same. With [Ranking.CODEC_FIRST] the codec counts before the
 * bitrate. The bitrate, not the size: a bigger file of a longer video is not better. The source (remux, web) and the
 * language of the audio are not known, a German and an English version of the same episode are duplicates to it.
 *
 * A group is only decided if every version could be read and they are about equally long (5%). Otherwise it is
 * [UnsureGroup] and nothing of it is deleted: a file which is much shorter might be cut or damaged, or a different
 * cut or a double episode. The subtitles next to a deleted version are moved to the version which stays if it has
 * none for that language, otherwise they are deleted with it.
 *
 * Run it as a test run first, it only writes what it would delete to the log. The log also tells how fast the probe is.
 */
class DuplicateCleaner(
    private val videoProbe: VideoProbe = FfprobeVideoProbe(),
    private val probeThreads: Int = DEFAULT_PROBE_THREADS,
    private val ranking: Ranking = Ranking.fromSettings(),
) {

    /** What decides between versions of the same resolution. */
    enum class Ranking(val setting: String) {
        /** Keeps the most data, e.g. to re-encode it later. */
        BITRATE_FIRST("bitrate"),

        /** Prefers the newer codec, which is smaller at the same quality. */
        CODEC_FIRST("codec");

        companion object {
            /** `DUPLICATE_RANKING` in boat.cfg: `bitrate` (default) or `codec`. */
            fun fromSettings(): Ranking = of(PropertiesHelper.getProperty("DUPLICATE_RANKING"))

            internal fun of(value: String?): Ranking =
                entries.firstOrNull { it.setting.equals(value?.trim(), ignoreCase = true) } ?: BITRATE_FIRST
        }
    }

    private val logger by LoggerDelegate()

    /** A video file of the library with what decides which version is the better one. */
    class Version(val file: File, val info: VideoInfo) {
        val resolutionClass: Int = resolutionClassOf(info)
        val codecRank: Int = codecRankOf(info.codec)
        val bitrate: Long = info.bitrate ?: 0
        val size: Long = file.length()

        fun description(): String = listOf(
            if (resolutionClass > 0) "${resolutionClass}p" else "?",
            info.codec ?: "?",
            info.bitrate?.let { String.format(Locale.ROOT, "%.1f Mbit/s", it / 1_000_000.0) } ?: "? Mbit/s",
            info.durationSeconds?.let { formatDuration(it) } ?: "?",
            String.format(Locale.ROOT, "%.2f GB", size / 1024.0 / 1024.0 / 1024.0),
        ).joinToString(", ")
    }

    class DuplicateGroup(val key: String, val keep: Version, val remove: List<Version>)

    /** Files which are the same by name, but can't be decided: [reason] says why. Nothing of it is deleted. */
    class UnsureGroup(val key: String, val files: List<File>, val reason: String, val versions: List<Version>)

    /** How long ffprobe took for the files of a run. */
    class ProbeStats(val files: Int, val failed: Int, val threads: Int, val wallMillis: Long, val durationsMillis: List<Long>) {
        private val sorted = durationsMillis.sorted()
        val averageMillis: Long get() = if (sorted.isEmpty()) 0 else sorted.sum() / sorted.size
        val medianMillis: Long get() = sorted.getOrElse(sorted.size / 2) { 0 }
        val p95Millis: Long get() = sorted.getOrElse((sorted.size * 95 / 100).coerceAtMost(sorted.size - 1)) { 0 }
        val maxMillis: Long get() = sorted.lastOrNull() ?: 0
        val filesPerSecond: Double get() = if (wallMillis == 0L) 0.0 else files * 1000.0 / wallMillis

        override fun toString(): String = String.format(
            Locale.ROOT,
            "ffprobe: %d files in %.1f s with %d threads (%.1f files/s), per file avg %d ms, median %d ms, 95%% under %d ms, max %d ms, %d could not be read",
            files, wallMillis / 1000.0, threads, filesPerSecond, averageMillis, medianMillis, p95Millis, maxMillis, failed
        )
    }

    class Result(val groups: List<DuplicateGroup>, val unsureGroups: List<UnsureGroup>, val probeStats: ProbeStats)

    /**
     * Looks for duplicates below [path] and deletes the worse versions, unless [isTestRun]. What happens is written to
     * [logFile] if there is one.
     */
    fun processFilesFromDirectory(path: String, logFile: File? = null, isTestRun: Boolean = true): Result {
        val result = findDuplicates(path)
        val writer = logFile?.let { BufferedWriter(FileWriter(it, true)) }
        try {
            writer?.write("${result.probeStats}\n")
            result.unsureGroups.forEach { unsure ->
                writer?.write("UNSURE ${unsure.key}: ${unsure.reason}\n")
                unsure.files.forEach { file ->
                    val version = unsure.versions.firstOrNull { it.file == file }
                    writer?.write("  '${file.path}'${version?.let { " (${it.description()})" } ?: ""}\n")
                }
            }
            result.groups.forEach { group ->
                writer?.write("Duplicates of ${group.key}:\n")
                writer?.write("KEEP '${group.keep.file.path}' (${group.keep.description()})\n")
                group.remove.forEach { loser -> deleteVersion(group.keep, loser, isTestRun, writer) }
            }
            writer?.write(
                "${result.groups.size} duplicates found, ${result.groups.sumOf { it.remove.size }} worse versions" +
                        (if (isTestRun) " (test run, nothing was deleted)" else " deleted") +
                        ", ${result.unsureGroups.size} groups not decided\n"
            )
        } finally {
            writer?.flush()
            writer?.close()
        }
        return result
    }

    /** The files which are there more than once below [path], with the version to keep, and the groups which can't be decided. */
    fun findDuplicates(path: String): Result {
        val root = File(path)
        val sameByName = if (!root.isDirectory) emptyMap() else root.walkTopDown().filter { it.isFile }
            .mapNotNull { file -> duplicateKey(file)?.let { key -> key to file } }
            .groupBy({ it.first }, { it.second })
            .filter { it.value.size > 1 }
        val (infos, stats) = probeAll(sameByName.values.flatten())
        logger.info("{}", stats)

        val groups = mutableListOf<DuplicateGroup>()
        val unsure = mutableListOf<UnsureGroup>()
        sameByName.toSortedMap().forEach { (key, files) ->
            val versions = files.mapNotNull { file -> infos[file]?.let { Version(file, it) } }
            val unreadable = files.filter { infos[it] == null }
            val reason = when {
                unreadable.isNotEmpty() -> "ffprobe could not read ${unreadable.joinToString { "'${it.name}'" }}"
                versions.any { it.info.durationSeconds == null } -> "the length of a version is not known"
                differentLength(versions) -> "the versions are not equally long (${versions.joinToString(" / ") { formatDuration(it.info.durationSeconds!!) }})"
                else -> null
            }
            if (reason != null) {
                unsure.add(UnsureGroup(key, files, reason, versions))
            } else {
                val ranked = versions.sortedWith(bestFirst)
                groups.add(DuplicateGroup(key, ranked.first(), ranked.drop(1)))
            }
        }
        return Result(groups, unsure, stats)
    }

    private val bestFirst: Comparator<Version> = when (ranking) {
        Ranking.BITRATE_FIRST -> compareByDescending<Version> { it.resolutionClass }.thenByDescending { it.bitrate }.thenByDescending { it.codecRank }
        Ranking.CODEC_FIRST -> compareByDescending<Version> { it.resolutionClass }.thenByDescending { it.codecRank }.thenByDescending { it.bitrate }
    }.thenByDescending { it.size }.thenBy { it.file.path }

    private fun differentLength(versions: List<Version>): Boolean {
        val lengths = versions.map { it.info.durationSeconds!! }
        return lengths.max() > lengths.min() * (1 + MAX_LENGTH_DIFFERENCE)
    }

    /** Reads all files with [probeThreads] threads at once, with the time ffprobe took. */
    private fun probeAll(files: List<File>): Pair<Map<File, VideoInfo>, ProbeStats> {
        val infos = ConcurrentHashMap<File, VideoInfo>()
        val durations = Collections.synchronizedList(mutableListOf<Long>())
        val done = AtomicInteger()
        val start = System.nanoTime()
        val executor = Executors.newFixedThreadPool(probeThreads.coerceAtLeast(1))
        try {
            executor.invokeAll(files.map { file ->
                Callable {
                    val begin = System.nanoTime()
                    videoProbe.probe(file)?.let { infos[file] = it }
                    val millis = (System.nanoTime() - begin) / 1_000_000
                    if (millis > SLOW_PROBE_MILLIS) logger.warn("reading {} took {} ms", file, millis)
                    durations.add(millis)
                    val count = done.incrementAndGet()
                    if (count % 500 == 0) logger.info("probed {} of {} files", count, files.size)
                }
            })
        } finally {
            executor.shutdown()
        }
        val wallMillis = (System.nanoTime() - start) / 1_000_000
        return infos to ProbeStats(files.size, files.size - infos.size, probeThreads.coerceAtLeast(1), wallMillis, durations.toList())
    }

    private fun deleteVersion(keep: Version, loser: Version, isTestRun: Boolean, writer: BufferedWriter?) {
        val keepBaseName = keep.file.nameWithoutExtension
        val loserBaseName = loser.file.nameWithoutExtension
        // versions with the same name and another extension (X.mkv, X.avi) share their subtitles, they stay
        val sidecars = if (loserBaseName == keepBaseName && loser.file.parentFile == keep.file.parentFile) emptyList() else sidecarsOf(loser.file)
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
        const val DEFAULT_PROBE_THREADS = 4

        /** Reading a file which takes longer than this is written to the log (a slow disk, a damaged file). */
        private const val SLOW_PROBE_MILLIS = 10_000

        /** Versions which differ more in length are not the same content. */
        private const val MAX_LENGTH_DIFFERENCE = 0.05

        private val standardHeights = listOf(4320, 2160, 1440, 1080, 720, 576, 480, 360, 240)

        /**
         * The class of the picture (1080 for 1920x1080, but also for a cropped 1920x800): the picture is as high as a
         * 16:9 picture of this width, or as it really is if that is more.
         */
        internal fun resolutionClassOf(info: VideoInfo): Int {
            val height = maxOf(info.height ?: 0, ((info.width ?: 0) * 9 / 16))
            return standardHeights.firstOrNull { height >= it * 0.9 } ?: if (height > 0) height else 0
        }

        /** The newer codecs are better at the same resolution. */
        internal fun codecRankOf(codec: String?): Int = when (codec?.lowercase()) {
            "av1", "hevc" -> 3
            "h264", "vp9" -> 2
            "mpeg4", "msmpeg4v3", "wmv3", "vc1", "mpeg2video", "mpeg1video" -> 1
            else -> 0
        }

        private fun formatDuration(seconds: Double): String {
            val total = seconds.toLong()
            return String.format(Locale.ROOT, "%d:%02d:%02d", total / 3600, total / 60 % 60, total % 60)
        }

        private val episodePattern = "(?i)(?<![a-z0-9])S(\\d{1,3})E(\\d{1,4})(-E\\d{1,4}|[a-z])?(?![0-9a-z])".toRegex()

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
