package pirateboat.torrent;

import org.junit.jupiter.api.Test;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

class TorrentHelperTest {

    @Test
    void shouldCleanTorrentName() {
        String resultString = TorrentHelper.getNormalizedTorrentString("Test.Movie.2018.HDRip.x264.AC3.mp3-GROUP.mkv");
        assertEquals("testmovie2018", resultString);
    }

    @Test
    void shouldCleanTorrentNameSecondMovie() {
        String resultString = TorrentHelper.getNormalizedTorrentString("Test.Movie.2.2019.HDRip.x264.AC3.mp3.-GROUP.mkv");
        assertEquals("testmovie22019", resultString);
    }

    @Test
    void shouldCleanTorrentNameThirdMovie() {
        String resultString = TorrentHelper.getNormalizedTorrentString("Planet Planet (2019) [WEBRip] [1080p] [GROUP] [GROUP]");
        assertEquals("planetplanet2019", resultString);
    }

    @Test
    void shouldRateTorrentName() {
        // Given
        Torrent torrent1 = new Torrent("Test");
        Torrent torrent2 = new Torrent("Test");
        torrent1.name = "Test.Movie.2.2019.HDRip.x264.AC3.mp3.-GROUP.mkvs";
        torrent1.seeder = 1;
        torrent1.leecher = 1;
        torrent1.lsize = 1000;

        torrent2.name = "Test.Movie.2018.HDRip.x264.AC3.mp3.-GROUP.mkvs";
        torrent2.seeder = 2;
        torrent2.leecher = 1;
        torrent2.lsize = 2000;
        // When
        TorrentHelper.evaluateRating(torrent1, "Test Movie");
        TorrentHelper.evaluateRating(torrent2, "Test Movie");
        // Then
        assertTrue(torrent1.searchRating < torrent2.searchRating);
    }

    @Test
    void shouldRateTorrentNameLongVsShort() {
        // Given
        Torrent torrent1 = new Torrent("Test");
        Torrent torrent2 = new Torrent("Test");
        torrent1.name = "Planet Planet S01E01 (2019) [WEBRip] [1080p] [GROUP] [GROUP]";
        torrent1.seeder = 2;
        torrent1.leecher = 1;
        torrent1.lsize = 2000;

        torrent2.name = "Planet Planet (2019) [WEBRip] [1080p] [GROUP] [GROUP]";
        torrent2.seeder = 2;
        torrent2.leecher = 1;
        torrent2.lsize = 2000;
        // When
        TorrentHelper.evaluateRating(torrent1, "Test Movie");
        TorrentHelper.evaluateRating(torrent2, "Test Movie");
        // Then
        assertTrue(torrent1.searchRating < torrent2.searchRating);
    }

    @Test
    void shouldRateTorrentNameShouldNotReportInfinityCloseness() {
        // Given
        Torrent torrent1 = new Torrent("Test");
        torrent1.name = "[FileTracker.pl] Planeta Ziemia - Planet Earth 2006 miniserial [720p.HDDVD.x264.AC3-ESiR][Narrator PL][Alusia]";
        torrent1.seeder = 1;
        torrent1.leecher = 1;
        torrent1.lsize = 1000;


        // When
        TorrentHelper.evaluateRating(torrent1, "planet earth 2006");
        // Then
        assertTrue(torrent1.searchRating > 0);
    }
    //

}