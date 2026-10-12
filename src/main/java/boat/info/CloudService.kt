package boat.info

import boat.torrent.TorrentFile
import boat.torrent.TorrentHelper
import boat.torrent.TorrentHelper.determineTypeOfMedia
import boat.torrent.TorrentHelper.prepareTorrentName
import boat.torrent.TorrentType
import boat.utilities.LoggerDelegate
import boat.utilities.PropertiesHelper
import boat.utilities.StringUtil
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.stream.Collectors

@Service
class CloudService internal constructor(
    private val cloudFileService: CloudFileService,
    private val mediaProxyService: MediaProxyService
) {

    companion object {
        private val logger by LoggerDelegate()
        const val RCLONE_DIR = "RCLONEDIR"
    }

    val isCloudTokenValid: Boolean =
        cloudFileService.getFilesInPath(buildDestinationPathWithTypeOfMediaWithoutSubFolders("A", TorrentType.MOVIES))
            .isNotEmpty()

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
        if (!cloudFileService.isCacheFilled) return emptyList()
        val start = Instant.now()
        val strings = searchName.split(Regex("\\s")).dropLastWhile { it.isEmpty() }
        return getAllFiles().filter { fileString: String ->
            strings.all {
                fileString.lowercase().contains(it.lowercase())
            }
        }.also {
            logger.info("Find files took: ${Duration.between(start, Instant.now())}")
        }.map { fileString: String -> fileString.replace(PropertiesHelper.getProperty(RCLONE_DIR) ?: "", "") }
    }

    fun getAllFiles(): List<String> {
        return "abcdefghijklmnopqrstuvwxyz+0".split(Regex("")).filter { it.isNotEmpty() }.map { searchName: String ->
            TorrentType.entries.map {
                val destinationPath = buildDestinationPathWithTypeOfMediaWithoutSubFolders(searchName, it)
                logger.info("Searching for: [$searchName] with $it in $destinationPath")
                cloudFileService.getFilesInPath(destinationPath)
            }.flatten()
        }.flatten()
    }

    fun buildDestinationPath(name: String?, filesFromTorrent: List<TorrentFile>): Pair<String, String> {
        val spacedName = name?.replace(".", " ") ?: ""
        val normalizedName = TorrentHelper.getNormalizedTorrentStringWithSpaces(spacedName)
        val typeOfMedia = determineTypeOfMedia(normalizedName)
        val mediaItems = mediaProxyService.search(normalizedName)
        val torrentTypeFromFile: TorrentType = determineTypeOfMedia(filesFromTorrent)
        logger.info("Deducted from Name: [$typeOfMedia] from Filename:[$torrentTypeFromFile] from TFDB:[$mediaItems]")
//        return if (TorrentType.TRANSFER == typeOfMedia) {
        return buildDestinationPathWithTypeOfMedia(name, torrentTypeFromFile)
//        } else {
//            buildDestinationPath(name)
//        }
    }

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