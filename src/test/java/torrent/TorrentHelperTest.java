package torrent;

import org.junit.jupiter.api.Test;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

class TorrentHelperTest {

    @Test
    void shouldCleanTorrentName() {
        String resultString = TorrentHelper.getNormalizedTorrentString("Test.Movie.2018.HDRip.x264.AC3.mp3-GROUP.mkv");
        assertEquals(resultString, "testmovie2018");
    }

    @Test
    void shouldCleanTorrentNameSecondMovie() {
        String resultString = TorrentHelper.getNormalizedTorrentString("Test.Movie.2.2019.HDRip.x264.AC3.mp3.-GROUP.mkvs");
        assertEquals(resultString, "testmovie22019");
    }

    @Test
    void shouldRateTorrentName() {
        // Given
        Torrent torrent1 = new Torrent();
        Torrent torrent2 = new Torrent();
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

}