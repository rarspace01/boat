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
import kotlin.math.pow

/**
 * Finds the same movie or episode more than once in the (renamed) library and deletes the worse versions.
 *
 * Two files are the same if they have the same remote media id (`{tmdb-27205}`, `{tvdb-79008}`) and, for episodes, the
 * same season and episode (parts of a movie "- pt1" and of an episode "S01E06b" or "S01E01-E02" are different files).
 * Files without id, extras and everything which is not a video are never touched, so the library has to be renamed
 * first ([boat.ContentRenamer]).
 *
 * Which version is better is not taken from the file names (they often have no resolution or codec) but from the
 * video itself, read with ffprobe ([FfprobeVideoProbe]): the higher resolution first. At the same resolution the
 * version with the higher quality bitrate wins: its bitrate divided by what the codec needs for the same picture
 * quality ([CODEC_BITRATE_NEEDS], H.264 is 1.0, HEVC about 0.55, an old xvid file about 1.9), so a HEVC file with 2.2
 * Mbit/s is worth an H.264 file with 4 Mbit/s. The better codec has a head start of [betterCodecMarginPercent] for
 * every generation it is ahead (modern: AV1, HEVC, VP9; H.264 class; older), so it stays against a version of an
 * older generation which has up to 20% more quality bitrate, only above that the higher bitrate wins. The bitrate,
 * not the size: a bigger file of a longer video is not better. With [codecWeightPercent] below 100 the efficiency of
 * the codec counts less and the plain bitrate more (0: only the bitrate, which keeps the most material for a later
 * re-encode). The source (remux, web) and the language of the audio are not known, a German and an English version
 * of the same episode are duplicates to it.
 *
 * If the versions differ in length, the official runtime of TMDB/TVDB ([RuntimeLookup]) decides: the versions from 15% shorter
 * to 30% longer than it compete (whatever their differences among each other), shorter ones are incomplete and deleted, a longer one
 * (several episodes in one file) leaves the group undecided.
 *
 * A file which ffprobe can't read and which is under 1 MB is a failed download, it is deleted if a readable version
 * exists. A version shorter than half of the longest one is an incomplete file (or another cut) and is deleted too.
 *
 * A group is otherwise only decided if every version could be read, the codecs are known and they are about equally long (5%). Otherwise it is
 * [UnsureGroup] and nothing of it is deleted: a file which is much shorter might be cut or damaged, or a different
 * cut or a double episode. The subtitles next to a deleted version are moved to the version which stays if it has
 * none for that language, otherwise they are deleted with it.
 *
 * Run it as a test run first, it only writes what it would delete to the log. The log also tells how fast the probe is.
 */
class DuplicateCleaner(
    private val videoProbe: VideoProbe = FfprobeVideoProbe(),
    private val probeThreads: Int = DEFAULT_PROBE_THREADS,
    /** `DUPLICATE_CODEC_WEIGHT_PERCENT` in boat.cfg: how much the efficiency of the codec counts, 100 fully, 0 not at all. */
    private val codecWeightPercent: Int = codecWeightFromSettings(),
    /** `DUPLICATE_BETTER_CODEC_MARGIN_PERCENT` in boat.cfg: the head start of the better codec per generation, 0 for none. */
    private val betterCodecMarginPercent: Int = betterCodecMarginFromSettings(),
    /** The official runtime from TMDB/TVDB decides groups whose versions differ in length, none: only the lengths are compared. */
    private val runtimeLookup: RuntimeLookup? = null,
) {

    private val logger by LoggerDelegate()

    /** A video file of the library with what decides which version is the better one. */
    class Version(val file: File, val info: VideoInfo) {
        val resolutionClass: Int = resolutionClassOf(info)
        val bitrate: Long = info.bitrate ?: 0
        val size: Long = file.length()

        /**
         * The bitrate in H.264 terms (what an H.264 file would need for the quality of this one, [codecWeightPercent]
         * 100 is full), with the head start of the generation of the codec ([betterCodecMarginPercent]) on top.
         */
        fun score(codecWeightPercent: Int, betterCodecMarginPercent: Int): Double =
            bitrate / (bitrateNeededByCodec(info.codec) ?: 1.0).pow(codecWeightPercent / 100.0) *
                    (1 + betterCodecMarginPercent / 100.0).pow(codecGenerationOf(info.codec))

        fun description(): String = listOf(
            if (resolutionClass > 0) "${resolutionClass}p" else "?",
            info.codec ?: "?",
            info.bitrate?.let { String.format(Locale.ROOT, "%.1f Mbit/s", it / 1_000_000.0) } ?: "? Mbit/s",
            info.durationSeconds?.let { formatDuration(it) } ?: "?",
            String.format(Locale.ROOT, "%.2f GB", size / 1024.0 / 1024.0 / 1024.0),
        ).joinToString(", ")
    }

    /** [reasons] tells why a version of [remove] is not just the worse one: a broken or incomplete file. */
    class DuplicateGroup(val key: String, val keep: Version, val remove: List<Version>, val reasons: Map<File, String> = emptyMap())

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
                group.remove.forEach { loser -> deleteVersion(group.keep, loser, group.reasons[loser.file], isTestRun, writer) }
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
            // a few bytes are a failed download, nothing to compare, it goes if there is a readable version
            val stubs = files.filter { infos[it] == null && it.length() < MAX_STUB_BYTES }.takeIf { it.size < files.size } ?: emptyList()
            val candidates = files - stubs.toSet()
            val versions = candidates.mapNotNull { file -> infos[file]?.let { Version(file, it) } }
            val unreadable = candidates.filter { infos[it] == null }
            val official = if (runtimeLookup != null && versions.size > 1 && versions.all { it.info.durationSeconds != null } && differentLength(versions)) officialRuntimeSeconds(key) else null
            val matching = official?.let { runtime -> versions.filter { it.info.durationSeconds!! in runtime * (1 - RUNTIME_SHORTER)..runtime * (1 + RUNTIME_LONGER) } }.orEmpty()
            val tooLong = official?.let { runtime -> versions.filter { it.info.durationSeconds!! > runtime * (1 + RUNTIME_LONGER) } }.orEmpty()
            val byRuntime = matching.isNotEmpty()
            val longest = versions.mapNotNull { it.info.durationSeconds }.maxOrNull()
            val incomplete = when {
                byRuntime -> versions - matching.toSet() - tooLong.toSet()
                longest == null -> emptyList()
                else -> versions.filter { (it.info.durationSeconds ?: longest) < longest * MIN_COMPLETE_LENGTH }
            }
            val complete = if (byRuntime) matching else versions - incomplete.toSet()
            val reason = when {
                unreadable.isNotEmpty() -> "ffprobe could not read ${unreadable.joinToString { "'${it.name}'" }}"
                versions.any { it.info.durationSeconds == null } -> "the length of a version is not known"
                versions.any { bitrateNeededByCodec(it.info.codec) == null } ->
                    "the codec is not known (${versions.joinToString { it.info.codec ?: "?" }})"
                byRuntime && tooLong.isNotEmpty() ->
                    "longer than the official runtime of ${formatDuration(official!!)} (several episodes?) (${versions.joinToString(" / ") { formatDuration(it.info.durationSeconds!!) }})"
                !byRuntime && differentLength(complete) -> "the versions are not equally long (${versions.joinToString(" / ") { formatDuration(it.info.durationSeconds!!) }})"
                else -> null
            }
            if (reason != null) {
                unsure.add(UnsureGroup(key, files, reason, versions))
            } else {
                val ranked = complete.sortedWith(bestFirst)
                val broken = stubs.map { Version(it, VideoInfo(null, null, null, null, null)) }
                val reasons = stubs.associateWith { "broken file, not readable" } +
                        incomplete.associate { it.file to if (byRuntime) "shorter than the official runtime of ${formatDuration(official!!)}" else "incomplete, shorter than half of the longest version" }
                groups.add(DuplicateGroup(key, ranked.first(), ranked.drop(1) + incomplete + broken, reasons))
            }
        }
        return Result(groups, unsure, stats)
    }

    private val bestFirst: Comparator<Version> = compareByDescending<Version> { it.resolutionClass }
        .thenByDescending { it.score(codecWeightPercent, betterCodecMarginPercent) }
        .thenByDescending { it.size }
        .thenBy { it.file.path }

    /** The runtime in seconds from TMDB/TVDB of the movie or episode of a duplicate [key], null if unknown or a part of a movie or episode. */
    private fun officialRuntimeSeconds(key: String): Double? {
        val movie = movieKeyPattern.matchEntire(key)
        val minutes = if (movie != null) {
            if (movie.groupValues[2] != "0") return null
            runtimeLookup?.minutes(movie.groupValues[1], null, null)
        } else {
            val series = seriesKeyPattern.matchEntire(key) ?: return null
            runtimeLookup?.minutes(series.groupValues[1], series.groupValues[2].toInt(), series.groupValues[3].toInt())
        }
        return minutes?.times(60)
    }

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

    private fun deleteVersion(keep: Version, loser: Version, reason: String?, isTestRun: Boolean, writer: BufferedWriter?) {
        val keepBaseName = keep.file.nameWithoutExtension
        val loserBaseName = loser.file.nameWithoutExtension
        // versions with the same name and another extension (X.mkv, X.avi) share their subtitles, they stay
        val sidecars = if (loserBaseName == keepBaseName && loser.file.parentFile == keep.file.parentFile) emptyList() else sidecarsOf(loser.file)
        writer?.write("DELETE '${loser.file.path}' (${loser.description()})${reason?.let { " - $it" } ?: ""}${if (isTestRun) " [test run]" else ""}\n")
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
        const val DEFAULT_CODEC_WEIGHT_PERCENT = 100

        internal fun codecWeightFromSettings(): Int = codecWeightOf(PropertiesHelper.getProperty("DUPLICATE_CODEC_WEIGHT_PERCENT"))

        internal fun codecWeightOf(value: String?): Int = value?.trim()?.toIntOrNull()?.takeIf { it in 0..100 } ?: DEFAULT_CODEC_WEIGHT_PERCENT

        const val DEFAULT_BETTER_CODEC_MARGIN_PERCENT = 20

        internal fun betterCodecMarginFromSettings(): Int =
            betterCodecMarginOf(PropertiesHelper.getProperty("DUPLICATE_BETTER_CODEC_MARGIN_PERCENT"))

        internal fun betterCodecMarginOf(value: String?): Int =
            value?.trim()?.toIntOrNull()?.takeIf { it in 0..300 } ?: DEFAULT_BETTER_CODEC_MARGIN_PERCENT

        /**
         * How much bitrate a codec needs for the same picture quality, H.264 = 1.0. Rules of thumb from published
         * encoder comparisons (HEVC saves 40 to 50% against H.264, AV1 about 30% more than HEVC, MPEG-4 part 2 like xvid
         * and DivX needs nearly the double, MPEG-2 more than that), they depend on the encoder and the material.
         * By the `codec_name` of ffprobe.
         */
        val CODEC_BITRATE_NEEDS = mapOf(
            "av1" to 0.45,
            "hevc" to 0.55,
            "vp9" to 0.6,
            "h264" to 1.0,
            "vc1" to 1.15,
            "vp8" to 1.3,
            "wmv3" to 1.3,
            "theora" to 1.6,
            "mpeg4" to 1.9,
            "msmpeg4v3" to 2.2,
            "h263" to 2.2,
            "mpeg2video" to 2.3,
            "msmpeg4v2" to 2.5,
            "wmv2" to 2.5,
            "wmv1" to 2.8,
            "mpeg1video" to 3.5,
        )

        internal fun bitrateNeededByCodec(codec: String?): Double? = codec?.lowercase()?.let { CODEC_BITRATE_NEEDS[it] }

        /** 2 for the modern codecs (AV1, HEVC, VP9), 1 for the H.264 class, 0 for the older ones. */
        internal fun codecGenerationOf(codec: String?): Int = when (val needs = bitrateNeededByCodec(codec)) {
            null -> 1
            else -> if (needs <= 0.7) 2 else if (needs <= 1.3) 1 else 0
        }

        /** Reading a file which takes longer than this is written to the log (a slow disk, a damaged file). */
        private const val SLOW_PROBE_MILLIS = 10_000

        /**
         * A version from 15% shorter to 30% longer than the official runtime is the whole movie or episode (the
         * databases are not exact, a version may have a recap or credits). Even longer: several episodes in one file.
         */
        private const val RUNTIME_SHORTER = 0.15
        private const val RUNTIME_LONGER = 0.30

        private val movieKeyPattern = "movie \\{?((?:tmdb|tvdb)-\\d+)}? pt(\\d+)".toRegex()
        private val seriesKeyPattern = "series \\{?((?:tmdb|tvdb)-\\d+)}? S(\\d+)E(\\d+)".toRegex()

        /** A file which ffprobe can't read and which is smaller than this is a failed download. */
        private const val MAX_STUB_BYTES = 1024L * 1024

        /** A version shorter than this part of the longest one is an incomplete file (or another cut), it is deleted. */
        private const val MIN_COMPLETE_LENGTH = 0.5

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
