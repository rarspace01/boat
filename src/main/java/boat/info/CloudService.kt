package boat.info

import boat.torrent.TorrentFile
import boat.torrent.TorrentHelper.determineTypeOfMedia
import boat.torrent.TorrentHelper.prepareTorrentName
import boat.torrent.TorrentType
import boat.utilities.LoggerDelegate
import boat.utilities.PropertiesHelper
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import java.time.Duration
import java.time.Instant
import java.util.Arrays
import java.util.Locale
import java.util.stream.Collectors

@Service
class CloudService internal constructor(private val cloudFileService: CloudFileService, val theFilmDataBaseService: TheFilmDataBaseService) {

    companion object {
        private val logger by LoggerDelegate()
        const val RCLONE_DIR = "RCLONEDIR"
    }

    val isCloudTokenValid: Boolean
        get() = cloudFileService.getFilesInPath(buildDestinationPathWithTypeOfMediaWithoutSubFolders("A", TorrentType.MOVIES)).isNotEmpty()

    fun buildDestinationPath(torrentName: String?): String {
        val basePath = PropertiesHelper.getProperty(RCLONE_DIR)
        val preparedTorrentName = prepareTorrentName(torrentName)
        val typeOfMedia = determineTypeOfMedia(torrentName)
        val torrentNameFirstLetterDeducted = deductFirstTorrentLetter(preparedTorrentName)
        var optionalSeriesString = ""
        if (TorrentType.SERIES_SHOWS == typeOfMedia) {
            optionalSeriesString = deductSeriesNameFrom(preparedTorrentName) + "/"
        }
        return (basePath + "/" + typeOfMedia.type + "/" + torrentNameFirstLetterDeducted + "/"
                + optionalSeriesString)
    }

    private fun deductSeriesNameFrom(preparedTorrentName: String): String {
        return Arrays.stream(preparedTorrentName
            .lowercase(Locale.getDefault())
            .trim { it <= ' ' }
            .replace("s[0-9]+e[0-9]+.*".toRegex(), "")
            .replace("season[.\\s]?[0-9-]+.*".toRegex(), "")
            .trim { it <= ' ' }
            .replace("\\s+".toRegex(), ".")
            .split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )
            .map { str: String? -> StringUtils.capitalize(str) }
            .collect(Collectors.joining("."))
    }

    fun buildDestinationPathWithTypeOfMediaWithoutSubFolders(
        torrentName: String?,
        typeOfMedia: TorrentType
    ): String {
        val basePath = PropertiesHelper.getProperty(RCLONE_DIR)
        val preparedTorrentName = prepareTorrentName(torrentName)
        val firstTorrentLetter = deductFirstTorrentLetter(preparedTorrentName)
        return basePath + "/" + typeOfMedia.type + "/" + firstTorrentLetter + "/"
    }

    private fun deductFirstTorrentLetter(preparedTorrentName: String): String {
        // replace Umlauts
        var preparedTorrentName = preparedTorrentName
        preparedTorrentName = preparedTorrentName.replace("[äÄ]".toRegex(), "A")
        preparedTorrentName = preparedTorrentName.replace("[öÖ]".toRegex(), "O")
        preparedTorrentName = preparedTorrentName.replace("[üÜ]".toRegex(), "Ü")

        // take only name infront of year
        val split = preparedTorrentName.split("[1-2][0-9]{3}".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (split.size > 0) {
            preparedTorrentName = split[0]
        }
        // remove articles
        preparedTorrentName = preparedTorrentName.replace("(a[ .]|the[ .]|der[ .])".toRegex(), "")
        //
        preparedTorrentName = preparedTorrentName.trim { it <= ' ' }
        preparedTorrentName = preparedTorrentName.replace("[\".]".toRegex(), "")
        if (preparedTorrentName.length > 0) {
            preparedTorrentName = preparedTorrentName.substring(0, 1)
        }
        preparedTorrentName = preparedTorrentName.replace("[\\W]".toRegex(), "?")
        preparedTorrentName = preparedTorrentName.replace("[0-9]".toRegex(), "0-9")
        preparedTorrentName = preparedTorrentName.uppercase(Locale.getDefault())
        //
        return preparedTorrentName
    }

    fun findExistingFiles(searchName: String): List<String> {
        val start = Instant.now()
        val strings = searchName.split(Regex("\\s")).dropLastWhile { it.isEmpty() }
        return getAllFiles().filter { fileString: String ->
            strings.all {
                fileString.lowercase().contains(it.lowercase())
            }
        }.also {
            logger.info("Find files took: ${Duration.between(start,Instant.now())}")
        }
    }

    fun getAllFiles():List<String> {
        return "abcdefghijklmnopqrstuvwxyz+0".split(Regex("")).filter { it.isNotEmpty() }.map { searchName: String ->
            TorrentType.values().map {
                val destinationPath = buildDestinationPathWithTypeOfMediaWithoutSubFolders(searchName, it)
                logger.info("Searching for: [$searchName] with $it in $destinationPath")
                cloudFileService.getFilesInPath(destinationPath)
            }.flatten()
        }.flatten()
    }

    fun buildDestinationPath(name: String?, filesFromTorrent: List<TorrentFile>): String {
        val typeOfMedia = determineTypeOfMedia(name)
        logger.info("Deducted from Name: $typeOfMedia")
        val mediaItems = theFilmDataBaseService.search(name)
        logger.info("Deducted from TFDB: $mediaItems")
        return if (TorrentType.TRANSFER == typeOfMedia) {
            val torrentType: TorrentType = determineTypeOfMedia(filesFromTorrent)
            buildDestinationPathWithTypeOfMedia(name, torrentType)
        } else {
            buildDestinationPath(name)
        }
    }

    private fun buildDestinationPathWithTypeOfMedia(name: String?, torrentType: TorrentType): String {
        val basePath = PropertiesHelper.getProperty(RCLONE_DIR)
        val preparedTorrentName = prepareTorrentName(name)
        val torrentNameFirstLetterDeducted = deductFirstTorrentLetter(preparedTorrentName)
        var optionalSeriesString = ""
        if (TorrentType.SERIES_SHOWS == torrentType) {
            optionalSeriesString = deductSeriesNameFrom(preparedTorrentName) + "/"
        }
        return (basePath + "/" + torrentType.type + "/" + torrentNameFirstLetterDeducted + "/"
                + optionalSeriesString)
    }

}