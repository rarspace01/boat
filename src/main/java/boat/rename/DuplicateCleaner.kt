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
import java.util.concurrent.atomic.AtomicLong
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
 * A file which ffprobe rejects (twice, every file which can't be read is read once more) is broken, it is deleted if a
 * readable version exists. A file which ffprobe could not read for another reason (a timeout, an I/O error of the
 * mount) may be fine, its group is [UnsureGroup]. A version shorter than half of the longest one is an incomplete file (or another cut) and is deleted too.
 *
 * Episodes whose file names have different titles in front of the episode number ("Batman TAS.S01E02" and "The New
 * Batman Adventures.S01E02" in the same show) are not the same episode, such a group is [UnsureGroup].
 *
 * Shifted episode numbers: if the versions of a decided episode differ in length by more than [SHIFT_MIN_DIFFERENCE]
 * and one of them has (to the second, [SHIFT_TOLERANCE_SECONDS]) the length of a version of exactly one other episode of
 * the same season (incomplete files and lengths which many episodes have don't count), one of the files is probably numbered differently (S06E01 of one release is S06E04 of the other). The
 * official runtime doesn't help there, all episodes have about the same one. Such a group is [UnsureGroup].
 *
 * A group is otherwise only decided if the codecs are known and they are about equally long (5%, a PAL speed-up of 4% or other credits differ by that much; more is another cut or episode, a mislabeled number). Otherwise it is
 * [UnsureGroup] and nothing of it is deleted: a file which is much shorter might be cut or damaged, or a different
 * cut or a double episode. The subtitles next to a deleted version are moved to the version which stays if it has
 * none for that language, otherwise they are deleted with it.
 *
 * Run it as a test run first, it only writes what it would delete to the log. The log also tells how fast the probe is.
 */
class DuplicateCleaner(
    private val videoProbe: VideoProbe = FfprobeVideoProbe(),
    /** `DUPLICATE_PROBE_THREADS` in boat.cfg: how many files ffprobe reads at once. */
    private val probeThreads: Int = probeThreadsFromSettings(),
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
        logger.info("{} groups of files which exist more than once, reading {} files", sameByName.size, sameByName.values.sumOf { it.size })
        val (results, stats) = probeAll(sameByName.values.flatten())
        val infos = results.mapNotNull { (file, result) -> (result as? ProbeResult.Read)?.let { file to it.info } }.toMap()
        logger.info("{}", stats)
        logger.info("deciding {} groups of files which exist more than once", sameByName.size)
        val decideStart = System.nanoTime()
        var decided = 0
        var lookups = 0
        var lookupMillis = 0L

        val groups = mutableListOf<DuplicateGroup>()
        val unsure = mutableListOf<UnsureGroup>()
        sameByName.toSortedMap().forEach { (key, files) ->
            if (++decided % 250 == 0) {
                logger.info("decided {} of {} groups after {} s, {} runtime lookups so far", decided, sameByName.size, (System.nanoTime() - decideStart) / 1_000_000_000, lookups)
            }
            val titles = files.mapNotNull { episodeTitleOf(it) }.distinctBy { normalizedTitle(it) }
            if (titles.size > 1) {
                unsure.add(UnsureGroup(key, files, "different titles (${titles.joinToString(" / ") { "'$it'" }})", files.mapNotNull { file -> infos[file]?.let { Version(file, it) } }))
                return@forEach
            }
            // a file which ffprobe rejects is broken, it goes if there is a readable version. One it could not read (a
            // timeout, an I/O error) might be fine, its group is not decided.
            val broken = files.filter { results[it] is ProbeResult.Broken }.takeIf { files.any { file -> infos[file] != null } } ?: emptyList()
            val candidates = files - broken.toSet()
            val versions = candidates.mapNotNull { file -> infos[file]?.let { Version(file, it) } }
            val unreadable = candidates.filter { infos[it] == null }
            val official = if (runtimeLookup != null && versions.size > 1 && versions.all { it.info.durationSeconds != null } && differentLength(versions)) {
                val begin = System.nanoTime()
                officialRuntimeSeconds(key).also {
                    lookups++
                    lookupMillis += (System.nanoTime() - begin) / 1_000_000
                    if (lookups % 25 == 0) logger.info("{} runtime lookups, {} ms on average, last: {}", lookups, lookupMillis / lookups, key)
                }
            } else null
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
                unreadable.isNotEmpty() -> "ffprobe could not read ${unreadable.joinToString { "'${it.name}' (${results[it]?.let(::probeProblem) ?: "?"})" }}"
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
                val brokenVersions = broken.map { Version(it, VideoInfo(null, null, null, null, null)) }
                val reasons = broken.associateWith { "broken file, not readable (${results[it]?.let(::probeProblem)})" } +
                        incomplete.associate { it.file to if (byRuntime) "shorter than the official runtime of ${formatDuration(official!!)}" else "incomplete, shorter than half of the longest version" }
                groups.add(DuplicateGroup(key, ranked.first(), ranked.drop(1) + incomplete + brokenVersions, reasons))
            }
        }
        val (consistent, shifted) = groups.partition { group -> shiftedNumbering(group, sameByName, infos) == null }
        shifted.forEach { group ->
            unsure.add(UnsureGroup(group.key, (listOf(group.keep) + group.remove).map { it.file }, shiftedNumbering(group, sameByName, infos)!!, listOf(group.keep) + group.remove))
        }
        groups.clear()
        groups.addAll(consistent)
        logger.info(
            "decided {} groups, {} not decided, in {} s ({} runtime lookups, {} s)", groups.size, unsure.size,
            (System.nanoTime() - decideStart) / 1_000_000_000, lookups, lookupMillis / 1000
        )
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

    /**
     * Tells which version of the [group] has the length of a version of exactly one other episode of the same season
     * and not the one of the other versions of its own episode, null if the numbering is not suspicious. Incomplete
     * files (under half of the longest version) don't count, and neither does a length which many episodes have
     * (the episodes of a cartoon are all about equally long).
     */
    private fun shiftedNumbering(group: DuplicateGroup, sameByName: Map<String, List<File>>, infos: Map<File, VideoInfo>): String? {
        val own = seriesKeyPattern.matchEntire(group.key) ?: return null
        val versions = completeVersions(listOf(group.keep) + group.remove.filter { it.info.durationSeconds != null })
        val lengths = versions.mapNotNull { it.info.durationSeconds }
        if (lengths.size < 2 || lengths.max() <= lengths.min() * (1 + SHIFT_MIN_DIFFERENCE)) return null
        val otherEpisodes = sameByName.mapNotNull { (otherKey, files) ->
            val other = seriesKeyPattern.matchEntire(otherKey)
            if (other == null || otherKey == group.key || other.groupValues[1] != own.groupValues[1] || other.groupValues[2] != own.groupValues[2]) null
            else files.filter { infos[it]?.durationSeconds != null }.let { readable ->
                val longest = readable.maxOfOrNull { infos.getValue(it).durationSeconds!! } ?: 0.0
                readable.filter { infos.getValue(it).durationSeconds!! >= longest * MIN_COMPLETE_LENGTH }
            }
        }
        for (version in versions) {
            val length = version.info.durationSeconds!!
            val matching = otherEpisodes.filter { files -> files.any { kotlin.math.abs(infos.getValue(it).durationSeconds!! - length) <= SHIFT_TOLERANCE_SECONDS } }
            if (matching.size == 1) {
                val match = matching.single().first { kotlin.math.abs(infos.getValue(it).durationSeconds!! - length) <= SHIFT_TOLERANCE_SECONDS }
                return "the episode numbering is probably shifted: '${version.file.name}' (${formatDuration(length)}) has the length of '${match.name}'"
            }
        }
        return null
    }

    private fun completeVersions(versions: List<Version>): List<Version> {
        val longest = versions.mapNotNull { it.info.durationSeconds }.maxOrNull() ?: return emptyList()
        return versions.filter { (it.info.durationSeconds ?: 0.0) >= longest * MIN_COMPLETE_LENGTH }
    }

    private fun differentLength(versions: List<Version>): Boolean {
        val lengths = versions.map { it.info.durationSeconds!! }
        return lengths.max() > lengths.min() * (1 + MAX_LENGTH_DIFFERENCE)
    }

    /**
     * Reads all files with [probeThreads] threads at once, with the time ffprobe took. A file which can't be read is
     * read once more after the others, a hiccup of the mount must not make a good file look broken.
     */
    private fun probeAll(files: List<File>): Pair<Map<File, ProbeResult>, ProbeStats> {
        val (results, stats) = probeOnce(files)
        val again = files.filter { results[it] !is ProbeResult.Read }
        if (again.isEmpty()) return results to stats
        logger.info("reading {} files which could not be read once more", again.size)
        val (retried, retryStats) = probeOnce(again)
        val merged = results + retried.mapValues { (file, result) ->
            // broken only if ffprobe rejects it both times
            if (result is ProbeResult.Broken && results[file] is ProbeResult.Failed) results.getValue(file) else result
        }
        return merged to ProbeStats(
            files.size, merged.values.count { it !is ProbeResult.Read }, stats.threads, stats.wallMillis + retryStats.wallMillis,
            stats.durationsMillis + retryStats.durationsMillis
        )
    }

    private fun probeOnce(files: List<File>): Pair<Map<File, ProbeResult>, ProbeStats> {
        val results = ConcurrentHashMap<File, ProbeResult>()
        val durations = Collections.synchronizedList(mutableListOf<Long>())
        val done = AtomicInteger()
        val start = System.nanoTime()
        val lastProgress = AtomicLong(start)
        val executor = Executors.newFixedThreadPool(probeThreads.coerceAtLeast(1))
        try {
            executor.invokeAll(files.map { file ->
                Callable {
                    val begin = System.nanoTime()
                    results[file] = videoProbe.probe(file)
                    val millis = (System.nanoTime() - begin) / 1_000_000
                    if (millis > SLOW_PROBE_MILLIS) logger.warn("reading {} took {} ms", file, millis)
                    durations.add(millis)
                    val count = done.incrementAndGet()
                    val now = System.nanoTime()
                    val last = lastProgress.get()
                    if (now - last >= PROGRESS_INTERVAL_NANOS && lastProgress.compareAndSet(last, now)) {
                        val seconds = (now - start) / 1_000_000_000.0
                        val rate = count / seconds
                        logger.info(
                            "probed {} of {} files ({}%) after {} s, {} files/s, about {} s to go",
                            count, files.size, count * 100 / files.size, seconds.toLong(),
                            String.format(Locale.ROOT, "%.1f", rate), ((files.size - count) / rate).toLong()
                        )
                    }
                }
            })
        } finally {
            executor.shutdown()
        }
        val wallMillis = (System.nanoTime() - start) / 1_000_000
        return results to ProbeStats(files.size, results.values.count { it !is ProbeResult.Read }, probeThreads.coerceAtLeast(1), wallMillis, durations.toList())
    }

    private fun probeProblem(result: ProbeResult): String = when (result) {
        is ProbeResult.Read -> "read"
        is ProbeResult.Broken -> result.reason
        is ProbeResult.Failed -> result.reason
    }

    private fun deleteVersion(keep: Version, loser: Version, reason: String?, isTestRun: Boolean, writer: BufferedWriter?) {
        val keepBaseName = keep.file.nameWithoutExtension
        val loserBaseName = loser.file.nameWithoutExtension
        // versions with the same name and another extension (X.mkv, X.avi) share their subtitles, they stay
        val sidecars = if (loserBaseName == keepBaseName && loser.file.parentFile == keep.file.parentFile) emptyList() else sidecarsOf(loser.file)
        // the file which is still there keeps its subtitles
        if (!isTestRun && !loser.file.delete()) {
            logger.warn("could not delete {}", loser.file)
            writer?.write("FAILED to delete '${loser.file.path}' (${loser.description()})${reason?.let { " - $it" } ?: ""}\n")
            return
        }
        writer?.write("DELETE '${loser.file.path}' (${loser.description()})${reason?.let { " - $it" } ?: ""}${if (isTestRun) " [test run]" else ""}\n")
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
                if (!isTestRun && !sidecar.delete()) {
                    logger.warn("could not delete {}", sidecar)
                    writer?.write("FAILED to delete '${sidecar.path}'\n")
                } else {
                    writer?.write("DELETE '${sidecar.path}'${if (isTestRun) " [test run]" else ""}\n")
                }
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
        /** More than about 8 threads don't read faster: on the rclone mount of the library it is the mount (one core) which is the limit, not ffprobe. */
        const val DEFAULT_PROBE_THREADS = 8

        internal fun probeThreadsFromSettings(): Int = probeThreadsOf(PropertiesHelper.getProperty("DUPLICATE_PROBE_THREADS"))

        internal fun probeThreadsOf(value: String?): Int = value?.trim()?.toIntOrNull()?.takeIf { it in 1..64 } ?: DEFAULT_PROBE_THREADS

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

        /** How often the progress of the reading is written to the log. */
        private const val PROGRESS_INTERVAL_NANOS = 10_000_000_000L

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

        /** A version shorter than this part of the longest one is an incomplete file (or another cut), it is deleted. */
        private const val MIN_COMPLETE_LENGTH = 0.5

        /** Versions of an episode which differ more than this in length and match another episode of the season by length are suspected to be numbered differently. */
        private const val SHIFT_MIN_DIFFERENCE = 0.03
        private const val SHIFT_TOLERANCE_SECONDS = 2.0

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

        /** The title in front of the episode number of the file name ("Batman TAS" of "Batman TAS.S01E02.1080p.mp4"), null if there is none. */
        internal fun episodeTitleOf(file: File): String? {
            val name = file.nameWithoutExtension
            return episodePattern.find(name)?.let { name.substring(0, it.range.first) }?.trim(' ', '.', '-', '_')?.takeIf { it.isNotEmpty() }
        }

        /** The title without case, punctuation and a leading "the", so "The.Crow.Girl" and "The Crow Girl" are the same. */
        private fun normalizedTitle(title: String): String =
            title.lowercase().replace("&", " and ").replace("[^\\p{L}\\p{N}]+".toRegex(), " ").trim().removePrefix("the ")

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
