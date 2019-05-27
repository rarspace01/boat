package torrent;

import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;

class TorrentHelperTest {

    @Test
    void shouldCleanTorrentName() {
        String resultString = TorrentHelper.getNormalizedTorrentString("Test.Movie.2018.HDRip.x264.AC3.mp3 -GROUP.mkv");
        assertEquals(resultString, "testmovie2018");
    }

    @Test
    void shouldCleanTorrentNameSecondMovie() {
        String resultString = TorrentHelper.getNormalizedTorrentString("Test.Movie.2.2019.HDRip.x264.AC3.mp3. -GROUP.mkvs");
        assertEquals(resultString, "testmovie22019");
    }

}