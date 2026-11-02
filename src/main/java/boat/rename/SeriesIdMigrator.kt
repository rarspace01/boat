package boat.rename

import boat.info.MediaItem
import boat.info.TheMovieDataBaseService
import boat.info.TheTVDBService
import boat.utilities.StringUtil
import java.io.File
import java.nio.file.Files

/** What [SeriesIdMigrator] needs to know about a series, [OnlineSeriesIdLookup] asks TMDB and TVDB. */
interface SeriesIdLookup {
    /** The TVDB id TMDB has for the series, null if it has none or doesn't answer. */
    fun tvdbIdOf(tmdbId: String): String?

    fun tmdbSeries(tmdbId: String): MediaItem?

    fun tvdbSeries(tvdbId: String): MediaItem?
}

class OnlineSeriesIdLookup(private val tmdb: TheMovieDataBaseService, private val tvdb: TheTVDBService) : SeriesIdLookup {
    override fun tvdbIdOf(tmdbId: String) = tmdb.tvdbIdOfSeries(tmdbId)
    override fun tmdbSeries(tmdbId: String) = tmdb.findById(tmdbId, boat.info.MediaType.Series)
    override fun tvdbSeries(tvdbId: String) = tvdb.findById(tvdbId)
}

/**
 * Moves the shows of the series folder which carry a TMDB id (`<letter>/Title {tmdb-123}`) to their TVDB id
 * (`<letter>/Title {tvdb-456}`), TVDB is preferred for series since its seasons are numbered like the releases. Only
 * the show folder changes, the seasons and episodes in it stay as they are. If the TVDB folder exists already the files
 * are merged into it, a file which is there already stays in the TMDB folder and is listed.
 *
 * TMDB tells the TVDB id of a series. A show is only moved if TMDB and TVDB count the same number of seasons, else the
 * episodes would be in the wrong seasons (TMDB splits some series into two with their own seasons, TVDB has one with
 * all of them). If the counts differ the show is still moved when it has season folders and all of them exist in both
 * (e.g. seasons 1 to 3 on disk, TMDB has 3 and TVDB 4, only a new season is missing). Those which stay and the shows
 * without TVDB id are listed as `UNSURE` to fix by hand.
 */
class SeriesIdMigrator(private val lookup: SeriesIdLookup) {

    data class Move(val source: File, val target: File)

    data class Result(val moves: List<Move>, val unsure: List<String>, val collisions: List<File>)

    fun migrate(seriesFolder: File, logFile: File? = null, isTestRun: Boolean = true): Result {
        val log = StringBuilder()
        val shows = findTmdbShows(seriesFolder)
        val tvdbIds = shows.associateWith { (_, tmdbId) -> lookup.tvdbIdOf(tmdbId) }
        val tmdbIdsByTvdbId = tvdbIds.entries.filter { it.value != null }.groupBy({ it.value!! }, { it.key.second })
        val moves = mutableListOf<Move>()
        val unsure = mutableListOf<String>()
        val collisions = mutableListOf<File>()
        fun unsure(folder: File, reason: String) {
            unsure += "${folder.path}: $reason"
            log.append("UNSURE: ${folder.path}: $reason\n")
        }
        for (show in shows) {
            val (folder, tmdbId) = show
            val tvdbId = tvdbIds[show]
            if (tvdbId == null) {
                unsure(folder, "TMDB has no TVDB id for {tmdb-$tmdbId}")
                continue
            }
            val tmdbIdsOfTvdbSeries = tmdbIdsByTvdbId[tvdbId].orEmpty()
            if (tmdbIdsOfTvdbSeries.size > 1) {
                unsure(folder, "TMDB splits {tvdb-$tvdbId} into ${tmdbIdsOfTvdbSeries.joinToString { "{tmdb-$it}" }}")
                continue
            }
            val tvdbSeries = lookup.tvdbSeries(tvdbId)
            if (tvdbSeries == null) {
                unsure(folder, "TVDB doesn't know {tvdb-$tvdbId}")
                continue
            }
            val tmdbSeasons = lookup.tmdbSeries(tmdbId)?.seasonCount
            if (tmdbSeasons == null || tmdbSeasons != tvdbSeries.seasonCount && !seasonsOnDiskExistInBoth(folder, tmdbSeasons, tvdbSeries.seasonCount)) {
                unsure(folder, "the seasons differ, {tmdb-$tmdbId} has $tmdbSeasons, {tvdb-$tvdbId} has ${tvdbSeries.seasonCount}")
                continue
            }
            val title = LibraryPaths.getSafeTitle(tvdbSeries)
            val target = File(File(seriesFolder, StringUtil.deductFirstTorrentLetter(title)), "$title {tvdb-$tvdbId}")
            log.append("${if (target.exists()) "MERGE" else "MOVE"}: ${folder.path} → ${target.path}\n")
            moves += Move(folder, target)
            if (!isTestRun) {
                collisions += move(folder, target).onEach { log.append("COLLISION, left in place: ${it.path}\n") }
            }
        }
        log.append("${moves.size} shows ${if (isTestRun) "to move" else "moved"}, ${unsure.size} unsure, ${collisions.size} files left in place\n")
        logFile?.writeText(log.toString())
        return Result(moves, unsure, collisions)
    }

    /** True if the show has season folders and the highest one is not above the season count of either database. */
    private fun seasonsOnDiskExistInBoth(show: File, tmdbSeasons: Int, tvdbSeasons: Int?): Boolean {
        val highestSeason = show.listFiles().orEmpty().filter { it.isDirectory }
            .mapNotNull { seasonFolderPattern.matchEntire(it.name)?.groupValues?.get(1)?.toInt() }
            .filter { it > 0 }.maxOrNull() ?: return false
        return tvdbSeasons != null && highestSeason <= minOf(tmdbSeasons, tvdbSeasons)
    }

    /** The show folders with a TMDB id and the id, directly in [seriesFolder] or in its letter folders. */
    private fun findTmdbShows(seriesFolder: File): List<Pair<File, String>> {
        fun tmdbShow(folder: File) = tmdbShowPattern.matchEntire(folder.name)?.let { folder to it.groupValues[1] }
        return seriesFolder.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }.flatMap { folder ->
            tmdbShow(folder)?.let { listOf(it) }
                ?: folder.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }.mapNotNull { tmdbShow(it) }
        }
    }

    /** Moves the folder, merges it into an existing one. Returns the files which were there already and stay. */
    private fun move(source: File, target: File): List<File> {
        if (!target.exists()) {
            target.parentFile.mkdirs()
            val isMoved = try {
                Files.move(source.toPath(), target.toPath())
                true
            } catch (_: Exception) {
                // e.g. a mount which can't move folders, the files are moved one by one
                false
            }
            if (isMoved) return emptyList()
        }
        return merge(source, target)
    }

    private fun merge(source: File, target: File): List<File> {
        target.mkdirs()
        val collisions = source.listFiles().orEmpty().sortedBy { it.name }.flatMap { file ->
            val targetFile = File(target, file.name)
            when {
                file.isDirectory -> merge(file, targetFile)
                targetFile.exists() -> listOf(file)
                else -> {
                    Files.move(file.toPath(), targetFile.toPath())
                    emptyList()
                }
            }
        }
        if (source.listFiles().isNullOrEmpty()) source.delete()
        return collisions
    }

    companion object {
        private val seasonFolderPattern = "^Season (\\d+)$".toRegex(RegexOption.IGNORE_CASE)
        private val tmdbShowPattern = "^.+ \\{tmdb-(\\d+)}$".toRegex(RegexOption.IGNORE_CASE)
    }
}
