package pirateboat.info;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pirateboat.torrent.Torrent;
import pirateboat.utilities.PropertiesHelper;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloudServiceTest {

    private CloudService cloudService;

    @BeforeEach
    public void beforeMethod() {
        cloudService = new CloudService();
    }


    @Test
    void buildDestinationPathForSingleFileTorrent() {
        // Given
        Torrent torrentToBeDownloaded = new Torrent("test");
        torrentToBeDownloaded.name = "Movie Title 2008 2160p US BluRay REMUX HEVC DTS HD MA TrueHD 7 1 Atmos FGT";
        // When
        String destinationPath = cloudService.buildDestinationPath(torrentToBeDownloaded);
        // Then
        assertEquals(PropertiesHelper.getProperty("rclonedir") + "/Movies/M/", destinationPath);
    }

    @Test
    void buildDestinationPathForSingleFileTorrentWithArticles() {
        // Given
        Torrent torrentToBeDownloaded = new Torrent("test");
        torrentToBeDownloaded.name = "A Movie Title 2008 2160p US BluRay REMUX HEVC DTS HD MA TrueHD 7 1 Atmos FGT";
        // When
        String destinationPath = cloudService.buildDestinationPath(torrentToBeDownloaded);
        // Then
        assertEquals(PropertiesHelper.getProperty("rclonedir") + "/Movies/M/", destinationPath);
    }

    @Test
    void buildDestinationPathForMultiFileTorrentTest() {
        // Given
        Torrent torrentToBeDownloaded = new Torrent("test");
        torrentToBeDownloaded.name = "Test and Test [UNCENSORED] Season 1-3 [1080p] [5.1 MP3] [x265][FINAL]";
        // When
        String destinationPath = cloudService.buildDestinationPath(torrentToBeDownloaded);
        // Then
        assertEquals(PropertiesHelper.getProperty("rclonedir") + "/Series-Shows/T/", destinationPath);
    }

    @Test
    void buildDestinationPathForSeriesTorrentTest() {
        // Given
        Torrent torrentToBeDownloaded = new Torrent("test");
        torrentToBeDownloaded.name = "Series.S01E02.480p.x264-mSD[tag].mkv";
        // When
        String destinationPath = cloudService.buildDestinationPath(torrentToBeDownloaded);
        // Then
        assertEquals(PropertiesHelper.getProperty("rclonedir") + "/Series-Shows/S/", destinationPath);
    }
}