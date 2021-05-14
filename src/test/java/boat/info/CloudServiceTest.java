package boat.info;

import java.util.List;

import boat.torrent.Torrent;
import boat.torrent.TorrentFile;
import boat.utilities.PropertiesHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudServiceTest {

    private CloudService cloudService;

    @BeforeEach
    public void beforeMethod() {
        cloudService = new CloudService(new CloudFileService());
    }

    @EnabledIfEnvironmentVariable(named = "DISABLE_UPDATE_PROMPT",matches = "true")
    @Test
    void searchForFilesWithYear() {
        // Given
        // When
        List<String> files = cloudService.findExistingFiles("Plan 9 1959");
        // Then
        assertTrue(files.size() > 0);
    }

    @EnabledIfEnvironmentVariable(named = "DISABLE_UPDATE_PROMPT",matches = "true")
    @Test
    void searchForFiles() {
        // Given
        // When
        List<String> files = cloudService.findExistingFiles("Plan 9");
        // Then
        assertTrue(files.size() > 0);
    }

    @Test
    void buildDestinationPathForSingleFileTorrentNoMatch() {
        // Given
        Torrent torrentToBeDownloaded = new Torrent("test");
        // When
        String destinationPath = cloudService.buildDestinationPath("Movie");
        // Then
        assertEquals(PropertiesHelper.getProperty("rclonedir") + "/transfer/M/", destinationPath);
    }

    @Test
    void buildDestinationPathForSingleFileTorrent() {
        // Given
        Torrent torrentToBeDownloaded = new Torrent("test");
        // When
        String destinationPath = cloudService.buildDestinationPath("Movie Title Xvid");
        // Then
        assertEquals(PropertiesHelper.getProperty("rclonedir") + "/Movies/M/", destinationPath);
    }

    @Test
    void buildDestinationPathForSingleFileTorrentWithNumber() {
        // Given
        Torrent torrentToBeDownloaded = new Torrent("test");
        // When
        String destinationPath = cloudService.buildDestinationPath("A 12 Number Title Xvid");
        // Then
        assertEquals(PropertiesHelper.getProperty("rclonedir") + "/Movies/0-9/", destinationPath);
    }

    @Test
    void buildDestinationPathForSingleFileTorrentExtended() {
        // Given
        Torrent torrentToBeDownloaded = new Torrent("test");
        // When
        String destinationPath = cloudService.buildDestinationPath("Movie Title 2008 2160p US BluRay REMUX HEVC DTS HD MA TrueHD 7 1 Atmos FGT");
        // Then
        assertEquals(PropertiesHelper.getProperty("rclonedir") + "/Movies/M/", destinationPath);
    }

    @Test
    void buildDestinationPathForSingleFileTorrentWithArticles() {
        // Given
        // When
        String destinationPath = cloudService.buildDestinationPath("A Movie Title 2008 2160p US BluRay REMUX HEVC DTS HD MA TrueHD 7 1 Atmos FGT");
        // Then
        assertEquals(PropertiesHelper.getProperty("rclonedir") + "/Movies/M/", destinationPath);
    }

    @Test
    void buildDestinationPathForMultiFileTorrentTest() {
        // Given
        // When
        String destinationPath = cloudService.buildDestinationPath("Test and Test [UNCENSORED] Season 1-3 [1080p] [5.1 MP3] [x265][FINAL]");
        // Then
        assertEquals(PropertiesHelper.getProperty("rclonedir") + "/Series-Shows/T/Test.And.Test/", destinationPath);
    }

    @Test
    void buildDestinationPathForSeriesTorrentTest() {
        // Given
        // When
        String destinationPath = cloudService.buildDestinationPath("Series.S01E02.480p.x264-mSD[tag].mkv");
        // Then
        assertEquals(PropertiesHelper.getProperty("rclonedir") + "/Series-Shows/S/Series/", destinationPath);
    }

    @Test
    void buildDestinationPathForSeriesLowerCaseTorrentTest() {
        // Given
        // When
        String destinationPath = cloudService.buildDestinationPath("series.S01E02.480p.x264-mSD[tag].mkv");
        // Then
        assertEquals(PropertiesHelper.getProperty("rclonedir") + "/Series-Shows/S/Series/", destinationPath);
    }

    @Test
    void buildDestinationPathWithQuoteTest() {
        // Given
        // When
        String destinationPath = cloudService.buildDestinationPath("\"series.S01E02.480p.x264-mSD[tag].mkv");
        // Then
        assertEquals(PropertiesHelper.getProperty("rclonedir") + "/Series-Shows/S/Series/", destinationPath);
    }

    @Test
    void buildDestinationPathForSeriesTorrentTestSubFolders() {
        // Given
        // When
        String destinationPath = cloudService.buildDestinationPath("Series.Name.S01E02.480p.x264-mSD[tag].mkv");
        // Then
        assertEquals(PropertiesHelper.getProperty("rclonedir") + "/Series-Shows/S/Series.Name/", destinationPath);
    }

    @Test
    void buildDestinationPathWithPDFTest() {
        // Given
        // When
        String destinationPath = cloudService.buildDestinationPath("series.S01E02.480p.x264-mSD[tag].pdf");
        // Then
        assertEquals(PropertiesHelper.getProperty("rclonedir") + "/transfer/S/", destinationPath);
    }

    @Test
    void buildDestinationPathSeriesWithFileListAndNonSeriesTorrentName() {
        // Given
        TorrentFile tf1 = new TorrentFile();
        tf1.name = "Series Name S01E01";
        TorrentFile tf2 = new TorrentFile();
        tf2.name = "Series Name S01E02";
        final Torrent torrent = new Torrent("Test");
        torrent.name = "Could be a movie but is a series";
        // When
        String destinationPath = cloudService.buildDestinationPath(torrent.name, List.of(tf1, tf2));
        // Then
        assertEquals(PropertiesHelper.getProperty("rclonedir") + "/Series-Shows/C/Could.Be.A.Movie.But.Is.A.Series/",
            destinationPath);
    }

}