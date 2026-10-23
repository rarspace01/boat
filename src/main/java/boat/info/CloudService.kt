package boat.info

import boat.torrent.TorrentFile
import boat.torrent.TorrentHelper.determineTypeOfMedia
import boat.torrent.TorrentHelper.prepareTorrentName
import boat.torrent.TorrentType
import boat.utilities.LoggerDelegate
import boat.utilities.PropertiesHelper
import boat.utilities.StringUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.stream.Collectors

@Service
class CloudService internal constructor(
    private val cloudFileService: CloudFileService,
    private val nanoTime: () -> Long,
) {
    @Autowired
    constructor(cloudFileService: CloudFileService) : this(cloudFileService, System::nanoTime)

    private class FolderCheck(val nanos: Long, val isReady: Boolean)

    @Volatile
    private var lastFolderCheck: FolderCheck? = null
    private val folderCheckLock = ReentrantLock()

    companion object {
        private val logger by LoggerDelegate()
        const val RCLONE_DIR = "RCLONEDIR"
        private val FOLDER_READY_NANOS = TimeUnit.DAYS.toNanos(1)
        private val FOLDER_RETRY_NANOS = TimeUnit.MINUTES.toNanos(1)
    }

    /**
     * Whether the target folder (`RCLONEDIR`) can be listed. Checked on the start, a success is kept for a day (so an
     * expired token is noticed). A failure is checked again after a minute, so a remote which is late or was fixed
     * doesn't need a restart. Only one check runs at a time, while it waits for a hanging remote the others get the last answer.
     */
    val isTargetFolderReady: Boolean
        get() {
            val last = lastFolderCheck
            val now = nanoTime()
            if (last != null && now - last.nanos < (if (last.isReady) FOLDER_READY_NANOS else FOLDER_RETRY_NANOS)) return last.isReady
            if (!folderCheckLock.tryLock()) return last?.isReady ?: false
            try {
                val path = PropertiesHelper.getProperty(RCLONE_DIR)
                val isReady = !path.isNullOrBlank() && cloudFileService.isDirectoryReachable(path)
                if (isReady) logger.info("target folder [{}] is ready", path) else logger.error("target folder [{}] can't be listed, nothing is downloaded until it can", path)
                lastFolderCheck = FolderCheck(nanoTime(), isReady)
                return isReady
            } finally {
                folderCheckLock.unlock()
            }
        }

    @EventListener(ApplicationReadyEvent::class)
    fun verifyTargetFolderOnStart() {
        isTargetFolderReady
    }

    private fun deductSeriesNameFrom(preparedTorrentName: String): String {
        return Arrays.stream(
            preparedTorrentName
                .lowercase(Locale.getDefault())
                .trim { it <= ' ' }
                .replace("s[0-9]+e[0-9]+.*".toRegex(), "")
                .replace("season[.\\s]?[0-9-]+.*".toRegex(), "")
                .trim { it <= ' ' }
                .replace("\\s+".toRegex(), ".")
                .split("\\.".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()
        )
            .map { str: String -> StringUtils.capitalize(str) }
            .collect(Collectors.joining("."))
    }

    fun buildDestinationPathWithTypeOfMediaWithoutSubFolders(
        torrentName: String?,
        typeOfMedia: TorrentType
    ): String {
        val basePath = PropertiesHelper.getProperty(RCLONE_DIR)
        val preparedTorrentName = prepareTorrentName(torrentName)
        val firstTorrentLetter = StringUtil.deductFirstTorrentLetter(preparedTorrentName)
        return basePath + "/" + typeOfMedia.type + "/" + firstTorrentLetter + "/"
    }

    /** The files of the library for a name, whole words only, otherwise short titles like "It" or "Up" match nearly every file. */
    fun findExistingFiles(searchName: String): List<String> {
        val start = Instant.now()
        val words = searchName.split(Regex("\\s")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val wordPatterns = words.map { Regex("(?iu)(?<![\\p{L}\\p{N}])" + Regex.escape(it) + "(?![\\p{L}\\p{N}])") }
        val basePath = PropertiesHelper.getProperty(RCLONE_DIR) ?: ""
        // the plain contains rules out nearly all files before the slower whole word patterns
        return getAllFiles().filter { file -> words.all { file.contains(it, ignoreCase = true) } && wordPatterns.all { it.containsMatchIn(file) } }
            .map { it.replace(basePath, "") }
            .also { logger.info("Find files took: {}", Duration.between(start, Instant.now())) }
    }

    fun getAllFiles(): List<String> {
        return "abcdefghijklmnopqrstuvwxyz+0".split(Regex("")).filter { it.isNotEmpty() }.map { searchName: String ->
            TorrentType.entries.map {
                val destinationPath = buildDestinationPathWithTypeOfMediaWithoutSubFolders(searchName, it)
                logger.debug("Searching for: [$searchName] with $it in $destinationPath")
                cloudFileService.getFilesInPath(destinationPath)
            }.flatten()
        }.flatten()
    }

    /** The folder of a download without a matched title, by the kind of files in it. */
    fun buildDestinationPath(name: String?, filesFromTorrent: List<TorrentFile>): Pair<String, String> =
        buildDestinationPathWithTypeOfMedia(name, determineTypeOfMedia(filesFromTorrent))

    private fun buildDestinationPathWithTypeOfMedia(name: String?, torrentType: TorrentType): Pair<String, String> {
        val basePath = PropertiesHelper.getProperty(RCLONE_DIR)
        val preparedTorrentName = prepareTorrentName(name)
        val torrentNameFirstLetterDeducted = StringUtil.deductFirstTorrentLetter(preparedTorrentName)
        var optionalSeriesString = ""
        if (TorrentType.SERIES_SHOWS == torrentType) {
            optionalSeriesString = deductSeriesNameFrom(preparedTorrentName) + "/"
        }
        return Pair(
            torrentNameFirstLetterDeducted,
            (basePath + "/" + torrentType.type + "/" + torrentNameFirstLetterDeducted + "/"
                    + optionalSeriesString)
        )
    }

}