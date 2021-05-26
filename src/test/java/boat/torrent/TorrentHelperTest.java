package boat.torrent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        TorrentHelper.evaluateRating(torrent1, "Planet Movie");
        TorrentHelper.evaluateRating(torrent2, "Planet Movie");
        // Then
        assertTrue(torrent1.searchRating < torrent2.searchRating);
    }

    @Test
    void shouldRateTorrentContainsMoreVsLess() {
        // Given
        Torrent torrent1 = new Torrent("Test");
        Torrent torrent2 = new Torrent("Test");
        torrent1.name = "Test Title Planet S01E01 (2019) [WEBRip] [1080p] [GROUP] [GROUP]";
        torrent1.seeder = 2;
        torrent1.leecher = 1;
        torrent1.lsize = 2000;

        torrent2.name = "Test Planet (2019) [WEBRip] [1080p] [GROUP] [GROUP]";
        torrent2.seeder = 2;
        torrent2.leecher = 1;
        torrent2.lsize = 2000;
        // When
        TorrentHelper.evaluateRating(torrent1, "Test Title");
        TorrentHelper.evaluateRating(torrent2, "Test Title");
        // Then
        assertTrue(torrent1.searchRating > torrent2.searchRating);
    }

    @Test
    void shouldRateTorrentContainsMoreThanSearchVsLess() {
        // Given
        Torrent torrent1 = new Torrent("Test");
        Torrent torrent2 = new Torrent("Test");
        torrent1.name = "Test Title Planet (2019) [WEBRip] [1080p] [GROUP] [GROUP]";
        torrent1.seeder = 2;
        torrent1.leecher = 1;
        torrent1.lsize = 2000;

        torrent2.name = "Test Title (2019) [WEBRip] [1080p] [GROUP] [GROUP]";
        torrent2.seeder = 2;
        torrent2.leecher = 1;
        torrent2.lsize = 2000;
        // When
        TorrentHelper.evaluateRating(torrent1, "Test Title");
        TorrentHelper.evaluateRating(torrent2, "Test Title");
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

    @Test
    void shouldRateTorrentNameShouldDetectAllWordsIncludingYear() {
        // Given
        Torrent torrent1 = new Torrent("Test");
        torrent1.name = "Movie.Title.2020.2160p.AMZN.WEB-DL.x265.10bit.HDR10plus.DDP5.1-SWTYBLZ";
        torrent1.seeder = 1;
        torrent1.leecher = 1;
        torrent1.lsize = 1000;


        // When
        TorrentHelper.evaluateRating(torrent1, "movie title");
        // Then
        assertTrue(torrent1.searchRating > 0);
        assertTrue(torrent1.debugRating.contains("📅"));
    }
    @Test
    void shouldRateTorrentNameShouldDetectAllWordsIncludingYear2() {
        // Given
        Torrent torrent1 = new Torrent("Test");
        torrent1.name = "Movie.Title.2020.2160p.DSNP.WEBRip.x265.10bit.HDR.DDP5.1.Atmos-SEMANTiCS";
        torrent1.seeder = 1;
        torrent1.leecher = 1;
        torrent1.lsize = 1000;


        // When
        TorrentHelper.evaluateRating(torrent1, "movie title");
        // Then
        assertTrue(torrent1.searchRating > 0);
        assertTrue(torrent1.debugRating.contains("📅"));
    }

    @Test
    void shouldRateTorrentNameShouldDetectAllWordsWithoutYear() {
        // Given
        Torrent torrent1 = new Torrent("Test");
        torrent1.name = "Movie.Title.2160p.AMZN.WEB-DL.x265.10bit.HDR10plus.DDP5.1-SWTYBLZ";
        torrent1.seeder = 1;
        torrent1.leecher = 1;
        torrent1.lsize = 1000;

        // When
        TorrentHelper.evaluateRating(torrent1, "movie title");
        // Then
        assertTrue(torrent1.searchRating > 0);
        assertFalse(torrent1.debugRating.contains("📅"));
    }

    @Test
    void shouldRateTorrentNameShouldDetectAllWordsWithExtraGarbage() {
        // Given
        Torrent torrent1 = new Torrent("Test");
        torrent1.name = "The.Movie.Of.books.2014.1080p,BluRay.H264.AAC.5.1.BADASSMEDIA";
        torrent1.seeder = 1;
        torrent1.leecher = 1;
        torrent1.lsize = 1000;

        // When
        TorrentHelper.evaluateRating(torrent1, "the movie of books");
        // Then
        assertTrue(torrent1.searchRating > 0);
        assertFalse(torrent1.debugRating.contains("\uD83D\uDD0D:2.40"));
    }

    @Test
    void shouldRateTorrentNameShouldDetectAllWordsWithSpacingChallenge() {
        // Given
        Torrent torrent1 = new Torrent("Test");
        torrent1.name = "The Movie of Books (2014) 3D BrRip x264 - YIFY";
        torrent1.seeder = 1;
        torrent1.leecher = 1;
        torrent1.lsize = 1000;

        // When
        TorrentHelper.evaluateRating(torrent1, "the movie of books");
        // Then
        assertTrue(torrent1.searchRating > 0);
        assertFalse(torrent1.debugRating.contains(":2.40"));
    }

    @Test
    void shouldRateTorrentEqually() {
        // Given
        Torrent torrent1 = new Torrent("Test");
        Torrent torrent2 = new Torrent("Test");
        torrent1.name = "Test Title S03E01 720p WEBRip x264-WAKETHEFUP [eztv]";

        torrent2.name = "Test Title S03E01 1080p WEBRip x264-WAKETHEFUP[rarbg]";
        // When
        TorrentHelper.evaluateRating(torrent1, "Test Title S03E01");
        TorrentHelper.evaluateRating(torrent2, "Test Title S03E01");
        // Then
        assertEquals(torrent1.searchRating, torrent2.searchRating);
    }

}