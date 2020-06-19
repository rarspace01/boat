package pirateboat.info;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pirateboat.torrent.Torrent;
import pirateboat.utilities.PropertiesHelper;

import static org.junit.jupiter.api.Assertions.*;

class CloudServiceTest {

    private CloudService cloudService;

    @BeforeEach
    public void beforeMethod(){
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
        assertEquals(PropertiesHelper.getProperty("rclonedir")+"/Movies/M/",destinationPath);
    }

    @Test
    void buildDestinationPathForSingleFileTorrentWithArticles() {
        // Given
        Torrent torrentToBeDownloaded = new Torrent("test");
        torrentToBeDownloaded.name = "A Movie Title 2008 2160p US BluRay REMUX HEVC DTS HD MA TrueHD 7 1 Atmos FGT";
        // When
        String destinationPath = cloudService.buildDestinationPath(torrentToBeDownloaded);
        // Then
        assertEquals(PropertiesHelper.getProperty("rclonedir")+"/Movies/M/",destinationPath);
    }
}