package boat.info

import boat.torrent.Torrent
import boat.torrent.TorrentFile
import boat.utilities.HttpHelper
import boat.utilities.PropertiesHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

internal class CloudServiceTest {
    private var cloudService: CloudService? = null

    @BeforeEach
    fun beforeMethod() {
        val httpHelper = HttpHelper()
        cloudService = CloudService(
            CloudFileService(),
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )
    }

    @EnabledIfEnvironmentVariable(named = "DISABLE_UPDATE_PROMPT", matches = "true")
    @Test
    fun searchForFilesWithYear() {
        // Given
        // When
        val files = cloudService!!.findExistingFiles("Plan 9 1959")
        // Then
        assertThat(files).isNotEmpty()
    }

    @EnabledIfEnvironmentVariable(named = "DISABLE_UPDATE_PROMPT", matches = "true")
    @Test
    fun searchForFiles() {
        // Given
        // When
        val files = cloudService!!.findExistingFiles("Plan 9")
        // Then
        assertThat(files).isNotEmpty()
    }

    @Test
    fun buildDestinationPathForSingleFileTorrentNoMatch() {
        // Given
        val torrentToBeDownloaded = Torrent("test")
        // When
        val destinationPath = cloudService!!.buildDestinationPath("Movie", listOf(TorrentFile(name = "movie.pdf"))).second
        // Then
        assertThat(destinationPath).isEqualTo(PropertiesHelper.getProperty("RCLONEDIR") + "/transfer/M/")
    }

    @Test
    fun buildDestinationPathForSingleFileTorrent() {
        // Given
        val torrentToBeDownloaded = Torrent("test")
        // When
        val destinationPath = cloudService!!.buildDestinationPath("Movie Title Xvid", listOf(TorrentFile(name = "xvid.xvid"))).second
        // Then
        assertThat(destinationPath).isEqualTo(PropertiesHelper.getProperty("RCLONEDIR") + "/Movies/M/",)
    }

    @Test
    fun buildDestinationPathForSingleFileTorrentWithNumber() {
        // Given
        val torrentToBeDownloaded = Torrent("test")
        // When
        val destinationPath = cloudService!!.buildDestinationPath("A 12 Number Title Xvid", listOf(TorrentFile(name = "xvid.xvid"))).second
        // Then
        assertThat(destinationPath).isEqualTo(PropertiesHelper.getProperty("RCLONEDIR") + "/Movies/0-9/")
    }

    @Test
    fun buildDestinationPathForSingleFileTorrentExtended() {
        // Given
        val torrentToBeDownloaded = Torrent("test")
        // When
        val destinationPath = cloudService!!.buildDestinationPath("Movie Title 2008 2160p US BluRay REMUX HEVC DTS HD MA TrueHD 7 1 Atmos FGT", listOf(TorrentFile(name = "xvid.xvid"))).second
        // Then
        assertThat(destinationPath).isEqualTo(PropertiesHelper.getProperty("RCLONEDIR") + "/Movies/M/")
    }

    @Test
    fun buildDestinationPathForSingleFileTorrentWithArticles() {
        // Given
        // When
        val destinationPath = cloudService!!.buildDestinationPath("A Movie Title 2008 2160p US BluRay REMUX HEVC DTS HD MA TrueHD 7 1 Atmos FGT", listOf(TorrentFile(name = "xvid.xvid"))).second
        // Then
        assertThat(destinationPath).isEqualTo(PropertiesHelper.getProperty("RCLONEDIR") + "/Movies/M/")
    }

    @Test
    fun buildDestinationPathForMultiFileTorrentTest() {
        // Given
        // When
        val destinationPath = cloudService!!.buildDestinationPath("Test and Test [UNCENSORED] Season 1-3 [1080p] [5.1 MP3] [x265][FINAL]", listOf(TorrentFile(name = "Season 1-3 xvid.xvid"))).second
        // Then
        assertThat(destinationPath).isEqualTo(PropertiesHelper.getProperty("RCLONEDIR") + "/Series-Shows/T/Test.And.Test/")
    }

    @Test
    fun buildDestinationPathForSeriesTorrentTest() {
        // Given
        // When
        val destinationPath = cloudService!!.buildDestinationPath("Series.S01E02.480p.x264-mSD[tag].mkv", listOf(TorrentFile(name = "S01E02xvid.xvid"))).second
        // Then
        assertThat(destinationPath).isEqualTo(PropertiesHelper.getProperty("RCLONEDIR") + "/Series-Shows/S/Series/")
    }

    @Test
    fun buildDestinationPathForSeriesLowerCaseTorrentTest() {
        // Given
        // When
        val destinationPath = cloudService!!.buildDestinationPath("series.S01E02.480p.x264-mSD[tag].mkv", listOf(TorrentFile(name = "S01E02xvid.xvid"))).second
        // Then
        assertThat(destinationPath).isEqualTo(PropertiesHelper.getProperty("RCLONEDIR") + "/Series-Shows/S/Series/")
    }

    @Test
    fun buildDestinationPathWithQuoteTest() {
        // Given
        // When
        val destinationPath = cloudService!!.buildDestinationPath("\"series.S01E02.480p.x264-mSD[tag].mkv", listOf(TorrentFile(name = "S01E02xvid.xvid"))).second
        // Then
        assertThat(destinationPath).isEqualTo(PropertiesHelper.getProperty("RCLONEDIR") + "/Series-Shows/S/Series/")
    }

    @Test
    fun buildDestinationPathForSeriesTorrentTestSubFolders() {
        // Given
        // When
        val destinationPath = cloudService!!.buildDestinationPath("Series.Name.S01E02.480p.x264-mSD[tag].mkv", listOf(TorrentFile(name = "S01E02xvid.xvid"))).second
        // Then
        assertThat(destinationPath).isEqualTo(PropertiesHelper.getProperty("RCLONEDIR") + "/Series-Shows/S/Series.Name/")
    }

    @Test
    fun buildDestinationPathWithPDFTest() {
        // Given
        // When
        val destinationPath = cloudService!!.buildDestinationPath("series.S01E02.480p.x264-mSD[tag].pdf", listOf(TorrentFile(name = "S01E02xvid.pdf"))).second
        // Then
        assertThat(destinationPath).isEqualTo(PropertiesHelper.getProperty("RCLONEDIR") + "/transfer/S/")
    }

    @Test
    fun buildDestinationPathSeriesWithFileListAndNonSeriesTorrentName() {
        // Given
        val tf1 = TorrentFile()
        tf1.name = "Series Name S01E01.mkv"
        val tf2 = TorrentFile()
        tf2.name = "Series Name S01E02.mkv"
        val torrent = Torrent("Test")
        torrent.name = "Could be a movie but is a series"
        // When
        val destinationPath = cloudService!!.buildDestinationPath(torrent.name, listOf(tf1, tf2)).second
        // Then
        assertThat(destinationPath).isEqualTo(
            PropertiesHelper.getProperty("RCLONEDIR") + "/Series-Shows/C/Could.Be.A.Movie.But.Is.A.Series/"
        )
    }

    @Test
    fun buildDestinationPathWithSeriesAndExtraTextAfterSeriesName() {
        // Given
        // When
        val destinationPath = cloudService!!.buildDestinationPath("series.S01E02.480p.x264-mSD[tag]", listOf(TorrentFile(name = "S01E02xvid.xvid"))).second
        // Then
        assertThat(destinationPath).isEqualTo(PropertiesHelper.getProperty("RCLONEDIR") + "/Series-Shows/S/Series/")
    }

    @Test
    fun buildDestinationPathWithSeriesAndExtraTextAfterSeriesNameExtended() {
        // Given
        // When
        val destinationPath = cloudService!!.buildDestinationPath("series.S01E02.Test.480p.x264-mSD[tag]", listOf(TorrentFile(name = "S01E02xvid.xvid"))).second
        // Then
        assertThat(destinationPath).isEqualTo(PropertiesHelper.getProperty("RCLONEDIR") + "/Series-Shows/S/Series/")
    }
}