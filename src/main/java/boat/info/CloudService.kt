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

    fun findExistingFiles(searchName: String): List<String> {
        val start = Instant.now()
        // whole words only, otherwise short titles like "It" or "Up" match nearly every file and an empty search all
        val wordPatterns = searchName.split(Regex("\\s")).filter { it.isNotEmpty() }
            .map { Regex("(?iu)(?<![\\p{L}\\p{N}])" + Regex.escape(it) + "(?![\\p{L}\\p{N}])") }
        if (wordPatterns.isEmpty()) return emptyList()
        return getAllFiles().filter { fileString: String ->
            wordPatterns.all { it.containsMatchIn(fileString) }
        }.also {
            logger.info("Find files took: ${Duration.between(start, Instant.now())}")
        }.map { fileString: String -> fileString.replace(PropertiesHelper.getProperty(RCLONE_DIR) ?: "", "") }
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