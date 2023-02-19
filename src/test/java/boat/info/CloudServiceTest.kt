package boat.info

import boat.torrent.Torrent
import boat.torrent.TorrentFile
import boat.utilities.HttpHelper
import boat.utilities.PropertiesHelper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

internal class CloudServiceTest {
    private var cloudService: CloudService? = null

    @BeforeEach
    fun beforeMethod() {
        cloudService = CloudService(CloudFileService(), TheFilmDataBaseService(HttpHelper()))
    }

    @EnabledIfEnvironmentVariable(named = "DISABLE_UPDATE_PROMPT", matches = "true")
    @Test
    fun searchForFilesWithYear() {
        // Given
        // When
        val files = cloudService!!.findExistingFiles("Plan 9 1959")
        // Then
        Assertions.assertTrue(files.size > 0)
    }

    @EnabledIfEnvironmentVariable(named = "DISABLE_UPDATE_PROMPT", matches = "true")
    @Test
    fun searchForFiles() {
        // Given
        // When
        val files = cloudService!!.findExistingFiles("Plan 9")
        // Then
        Assertions.assertTrue(files.size > 0)
    }

    @Test
    fun buildDestinationPathForSingleFileTorrentNoMatch() {
        // Given
        val torrentToBeDownloaded = Torrent("test")
        // When
        val destinationPath = cloudService!!.buildDestinationPath("Movie").second
        // Then
        Assertions.assertEquals(PropertiesHelper.getProperty("RCLONEDIR") + "/transfer/M/", destinationPath)
    }

    @Test
    fun buildDestinationPathForSingleFileTorrent() {
        // Given
        val torrentToBeDownloaded = Torrent("test")
        // When
        val destinationPath = cloudService!!.buildDestinationPath("Movie Title Xvid").second
        // Then
        Assertions.assertEquals(PropertiesHelper.getProperty("RCLONEDIR") + "/Movies/M/", destinationPath)
    }

    @Test
    fun buildDestinationPathForSingleFileTorrentWithNumber() {
        // Given
        val torrentToBeDownloaded = Torrent("test")
        // When
        val destinationPath = cloudService!!.buildDestinationPath("A 12 Number Title Xvid").second
        // Then
        Assertions.assertEquals(PropertiesHelper.getProperty("RCLONEDIR") + "/Movies/0-9/", destinationPath)
    }

    @Test
    fun buildDestinationPathForSingleFileTorrentExtended() {
        // Given
        val torrentToBeDownloaded = Torrent("test")
        // When
        val destinationPath = cloudService!!.buildDestinationPath("Movie Title 2008 2160p US BluRay REMUX HEVC DTS HD MA TrueHD 7 1 Atmos FGT").second
        // Then
        Assertions.assertEquals(PropertiesHelper.getProperty("RCLONEDIR") + "/Movies/M/", destinationPath)
    }

    @Test
    fun buildDestinationPathForSingleFileTorrentWithArticles() {
        // Given
        // When
        val destinationPath = cloudService!!.buildDestinationPath("A Movie Title 2008 2160p US BluRay REMUX HEVC DTS HD MA TrueHD 7 1 Atmos FGT").second
        // Then
        Assertions.assertEquals(PropertiesHelper.getProperty("RCLONEDIR") + "/Movies/M/", destinationPath)
    }

    @Test
    fun buildDestinationPathForMultiFileTorrentTest() {
        // Given
        // When
        val destinationPath = cloudService!!.buildDestinationPath("Test and Test [UNCENSORED] Season 1-3 [1080p] [5.1 MP3] [x265][FINAL]").second
        // Then
        Assertions.assertEquals(PropertiesHelper.getProperty("RCLONEDIR") + "/Series-Shows/T/Test.And.Test/", destinationPath)
    }

    @Test
    fun buildDestinationPathForSeriesTorrentTest() {
        // Given
        // When
        val destinationPath = cloudService!!.buildDestinationPath("Series.S01E02.480p.x264-mSD[tag].mkv").second
        // Then
        Assertions.assertEquals(PropertiesHelper.getProperty("RCLONEDIR") + "/Series-Shows/S/Series/", destinationPath)
    }

    @Test
    fun buildDestinationPathForSeriesLowerCaseTorrentTest() {
        // Given
        // When
        val destinationPath = cloudService!!.buildDestinationPath("series.S01E02.480p.x264-mSD[tag].mkv").second
        // Then
        Assertions.assertEquals(PropertiesHelper.getProperty("RCLONEDIR") + "/Series-Shows/S/Series/", destinationPath)
    }

    @Test
    fun buildDestinationPathWithQuoteTest() {
        // Given
        // When
        val destinationPath = cloudService!!.buildDestinationPath("\"series.S01E02.480p.x264-mSD[tag].mkv").second
        // Then
        Assertions.assertEquals(PropertiesHelper.getProperty("RCLONEDIR") + "/Series-Shows/S/Series/", destinationPath)
    }

    @Test
    fun buildDestinationPathForSeriesTorrentTestSubFolders() {
        // Given
        // When
        val destinationPath = cloudService!!.buildDestinationPath("Series.Name.S01E02.480p.x264-mSD[tag].mkv").second
        // Then
        Assertions.assertEquals(PropertiesHelper.getProperty("RCLONEDIR") + "/Series-Shows/S/Series.Name/", destinationPath)
    }

    @Test
    fun buildDestinationPathWithPDFTest() {
        // Given
        // When
        val destinationPath = cloudService!!.buildDestinationPath("series.S01E02.480p.x264-mSD[tag].pdf").second
        // Then
        Assertions.assertEquals(PropertiesHelper.getProperty("RCLONEDIR") + "/transfer/S/", destinationPath)
    }

    @Test
    fun buildDestinationPathSeriesWithFileListAndNonSeriesTorrentName() {
        // Given
        val tf1 = TorrentFile()
        tf1.name = "Series Name S01E01"
        val tf2 = TorrentFile()
        tf2.name = "Series Name S01E02"
        val torrent = Torrent("Test")
        torrent.name = "Could be a movie but is a series"
        // When
        val destinationPath = cloudService!!.buildDestinationPath(torrent.name, listOf(tf1, tf2)).second
        // Then
        Assertions.assertEquals(
            PropertiesHelper.getProperty("RCLONEDIR") + "/Series-Shows/C/Could.Be.A.Movie.But.Is.A.Series/",
            destinationPath
        )
    }

    @Test
    fun buildDestinationPathWithSeriesAndExtraTextAfterSeriesName() {
        // Given
        // When
        val destinationPath = cloudService!!.buildDestinationPath("series.S01E02.480p.x264-mSD[tag]").second
        // Then
        Assertions.assertEquals(PropertiesHelper.getProperty("RCLONEDIR") + "/Series-Shows/S/Series/", destinationPath)
    }

    @Test
    fun buildDestinationPathWithSeriesAndExtraTextAfterSeriesNameExtended() {
        // Given
        // When
        val destinationPath = cloudService!!.buildDestinationPath("series.S01E02.Test.480p.x264-mSD[tag]").second
        // Then
        Assertions.assertEquals(PropertiesHelper.getProperty("RCLONEDIR") + "/Series-Shows/S/Series/", destinationPath)
    }
}