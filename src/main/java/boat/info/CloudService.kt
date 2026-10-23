package boat.info

import boat.torrent.TorrentFile
import boat.torrent.TorrentHelper.determineTypeOfMedia
import boat.torrent.TorrentHelper.prepareTorrentName
import boat.torrent.TorrentType
import boat.utilities.LoggerDelegate
import boat.utilities.PropertiesHelper
import boat.utilities.StringUtil
import org.springframework.beans.factory.annotation.Autowired
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

    private class TokenCheck(val nanos: Long, val isValid: Boolean)

    @Volatile
    private var lastTokenCheck: TokenCheck? = null
    private val tokenCheckLock = ReentrantLock()

    companion object {
        private val logger by LoggerDelegate()
        const val RCLONE_DIR = "RCLONEDIR"
        private val VALID_TOKEN_CHECK_NANOS = TimeUnit.MINUTES.toNanos(5)
        private val INVALID_TOKEN_CHECK_NANOS = TimeUnit.MINUTES.toNanos(1)
    }

    /**
     * A listing of the library, remembered for a minute (a valid token for 5), so an expired or late-ready token is still
     * noticed soon. Only one check runs at a time, while it waits for a hanging remote the others get the last answer.
     */
    val isCloudTokenValid: Boolean
        get() {
            val last = lastTokenCheck
            val now = nanoTime()
            if (last != null && now - last.nanos < (if (last.isValid) VALID_TOKEN_CHECK_NANOS else INVALID_TOKEN_CHECK_NANOS)) return last.isValid
            if (!tokenCheckLock.tryLock()) return last?.isValid ?: false
            try {
                val isValid = cloudFileService.getFilesInPath(buildDestinationPathWithTypeOfMediaWithoutSubFolders("A", TorrentType.MOVIES)).isNotEmpty()
                lastTokenCheck = TokenCheck(nanoTime(), isValid)
                return isValid
            } finally {
                tokenCheckLock.unlock()
            }
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

    fun findExistingFiles(searchName: String): List<String> = findExistingFilesForEach(listOf(searchName)).single()

    /**
     * The files of the library for each of [searchNames], all of them against one listing (a pasted list has
     * hundreds of entries). Whole words only, otherwise short titles like "It" or "Up" match nearly every file.
     */
    fun findExistingFilesForEach(searchNames: List<String>): List<List<String>> {
        if (!cloudFileService.isCacheFilled) return searchNames.map { emptyList() }
        val start = Instant.now()
        val allFiles by lazy { getAllFiles() }
        val basePath = PropertiesHelper.getProperty(RCLONE_DIR) ?: ""
        return searchNames.map { searchName ->
            val words = searchName.split(Regex("\\s")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return@map emptyList()
            val wordPatterns = words.map { Regex("(?iu)(?<![\\p{L}\\p{N}])" + Regex.escape(it) + "(?![\\p{L}\\p{N}])") }
            // the plain contains rules out nearly all files before the slower whole word patterns
            allFiles.filter { file -> words.all { file.contains(it, ignoreCase = true) } && wordPatterns.all { it.containsMatchIn(file) } }
                .map { it.replace(basePath, "") }
        }.also {
            logger.info("Find files for {} names took: {}", searchNames.size, Duration.between(start, Instant.now()))
        }
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