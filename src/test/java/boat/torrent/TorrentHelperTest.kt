package boat.torrent

import boat.info.MediaType
import boat.torrent.TorrentHelper.evaluateRating
import boat.torrent.TorrentHelper.getNormalizedTorrentString
import boat.torrent.TorrentHelper.getNormalizedTorrentStringWithSpaces
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class TorrentHelperTest {
    @Test
    fun shouldCleanTorrentName() {
        val resultString = getNormalizedTorrentString("Test.Movie.2018.HDRip.x264.AC3.mp3-GROUP.mkv")
        Assertions.assertEquals("testmovie2018", resultString)
    }

    @Test
    fun shouldCleanTorrentNameSecondMovie() {
        val resultString = getNormalizedTorrentString("Test.Movie.2.2019.HDRip.x264.AC3.mp3.-GROUP.mkv")
        Assertions.assertEquals("testmovie22019", resultString)
    }

    @Test
    fun shouldCleanTorrentNameThirdMovie() {
        val resultString = getNormalizedTorrentString("Planet Planet (2019) [WEBRip] [1080p] [GROUP] [GROUP]")
        Assertions.assertEquals("planetplanet2019", resultString)
    }

    @Test
    fun shouldRateTorrentName() {
        // Given
        val torrent1 = Torrent("Test")
        val torrent2 = Torrent("Test")
        torrent1.name = "Test.Movie.2.2019.HDRip.x264.AC3.mp3.-GROUP.mkvs"
        torrent1.seeder = 1
        torrent1.leecher = 1
        torrent1.sizeInMB = 1000.0
        torrent2.name = "Test.Movie.2018.HDRip.x264.AC3.mp3.-GROUP.mkvs"
        torrent2.seeder = 2
        torrent2.leecher = 1
        torrent2.sizeInMB = 2000.0
        // When
        evaluateRating(torrent1, "Test Movie")
        evaluateRating(torrent2, "Test Movie")
        // Then
        Assertions.assertTrue(torrent1.searchRating < torrent2.searchRating)
    }

    @Test
    fun shouldRateTorrentNameLongVsShort() {
        // Given
        val torrent1 = Torrent("Test")
        val torrent2 = Torrent("Test")
        torrent1.name = "Planet Planet S01E01 (2019) [WEBRip] [1080p] [GROUP] [GROUP]"
        torrent1.seeder = 2
        torrent1.leecher = 1
        torrent1.sizeInMB = 2000.0
        torrent2.name = "Planet Planet (2019) [WEBRip] [1080p] [GROUP] [GROUP]"
        torrent2.seeder = 2
        torrent2.leecher = 1
        torrent2.sizeInMB = 2000.0
        // When
        evaluateRating(torrent1, "Planet Movie")
        evaluateRating(torrent2, "Planet Movie")
        // Then
        Assertions.assertTrue(torrent1.searchRating < torrent2.searchRating)
    }

    @Test
    fun shouldRateTorrentContainsMoreVsLess() {
        // Given
        val torrent1 = Torrent("Test")
        val torrent2 = Torrent("Test")
        torrent1.name = "Test Title Planet S01E01 (2019) [WEBRip] [1080p] [GROUP] [GROUP]"
        torrent1.seeder = 2
        torrent1.leecher = 1
        torrent1.sizeInMB = 2000.0
        torrent2.name = "Test Planet (2019) [WEBRip] [1080p] [GROUP] [GROUP]"
        torrent2.seeder = 2
        torrent2.leecher = 1
        torrent2.sizeInMB = 2000.0
        // When
        evaluateRating(torrent1, "Test Title")
        evaluateRating(torrent2, "Test Title")
        // Then
        Assertions.assertTrue(torrent1.searchRating > torrent2.searchRating)
    }

    @Test
    fun shouldRateTorrentContainsMoreThanSearchVsLess() {
        // Given
        val torrent1 = Torrent("Test")
        val torrent2 = Torrent("Test")
        torrent1.name = "Test Title Planet (2019) [WEBRip] [1080p] [GROUP] [GROUP]"
        torrent1.seeder = 2
        torrent1.leecher = 1
        torrent1.sizeInMB = 2000.0
        torrent2.name = "Test Title (2019) [WEBRip] [1080p] [GROUP] [GROUP]"
        torrent2.seeder = 2
        torrent2.leecher = 1
        torrent2.sizeInMB = 2000.0
        // When
        evaluateRating(torrent1, "Test Title")
        evaluateRating(torrent2, "Test Title")
        // Then
        Assertions.assertTrue(torrent1.searchRating < torrent2.searchRating)
    }

    @Test
    fun shouldRateTorrentNameShouldNotReportInfinityCloseness() {
        // Given
        val torrent1 = Torrent("Test")
        torrent1.name = "[FileTracker.pl] Planeta Ziemia - Planet Earth 2006 miniserial [720p.HDDVD.x264.AC3-ESiR][Narrator PL][Alusia]"
        torrent1.seeder = 1
        torrent1.leecher = 1
        torrent1.sizeInMB = 1000.0


        // When
        evaluateRating(torrent1, "planet earth 2006")
        // Then
        Assertions.assertTrue(torrent1.searchRating > 0)
    }

    //
    @Test
    fun shouldRateTorrentNameShouldDetectAllWordsIncludingYear() {
        // Given
        val torrent1 = Torrent("Test")
        torrent1.name = "Movie.Title.2020.2160p.AMZN.WEB-DL.x265.10bit.HDR10plus.DDP5.1-SWTYBLZ"
        torrent1.seeder = 1
        torrent1.leecher = 1
        torrent1.sizeInMB = 1000.0

        // When
        evaluateRating(torrent1, "movie title")
        // Then
        Assertions.assertTrue(torrent1.searchRating > 0)
        Assertions.assertTrue(torrent1.debugRating.contains("📅"))
    }

    @Test
    fun shouldRateTorrentNameShouldDetectAllWordsIncludingYear2() {
        // Given
        val torrent1 = Torrent("Test")
        torrent1.name = "Movie.Title.2020.2160p.DSNP.WEBRip.x265.10bit.HDR.DDP5.1.Atmos-SEMANTiCS"
        torrent1.seeder = 1
        torrent1.leecher = 1
        torrent1.sizeInMB = 1000.0

        // When
        evaluateRating(torrent1, "movie title")
        // Then
        Assertions.assertTrue(torrent1.searchRating > 0)
        Assertions.assertTrue(torrent1.debugRating.contains("📅"))
    }

    @Test
    fun shouldRateTorrentNameShouldDetectAllWordsWithoutYear() {
        // Given
        val torrent1 = Torrent("Test")
        torrent1.name = "Movie.Title.2160p.AMZN.WEB-DL.x265.10bit.HDR10plus.DDP5.1-SWTYBLZ"
        torrent1.seeder = 1
        torrent1.leecher = 1
        torrent1.sizeInMB = 1000.0

        // When
        evaluateRating(torrent1, "movie title")
        // Then
        Assertions.assertTrue(torrent1.searchRating > 0)
        Assertions.assertFalse(torrent1.debugRating.contains("📅"))
    }

    @Test
    fun shouldRateTorrentNameShouldDetectAllWordsWithExtraGarbage() {
        // Given
        val torrent1 = Torrent("Test")
        torrent1.name = "The.Movie.Of.books.2014.1080p,BluRay.H264.AAC.5.1.BADASSMEDIA"
        torrent1.seeder = 1
        torrent1.leecher = 1
        torrent1.sizeInMB = 1000.0

        // When
        evaluateRating(torrent1, "the movie of books")
        // Then
        Assertions.assertTrue(torrent1.searchRating > 0)
        Assertions.assertFalse(torrent1.debugRating.contains("\uD83D\uDD0D:2.40"))
    }

    @Test
    fun shouldRateTorrentNameShouldDetectAllWordsWithSpacingChallenge() {
        // Given
        val torrent1 = Torrent("Test")
        torrent1.name = "The Movie of Books (2014) 3D BrRip x264 - YIFY"
        torrent1.seeder = 1
        torrent1.leecher = 1
        torrent1.sizeInMB = 1000.0
        val torrent2 = Torrent("Test")
        torrent2.name = "The Movie of Books (2014) 3D BrRip x264-YIFY"
        torrent2.seeder = 1
        torrent2.leecher = 1
        torrent2.sizeInMB = 1000.0

        // When
        evaluateRating(torrent1, "the movie of books")
        evaluateRating(torrent2, "the movie of books")
        // Then
        Assertions.assertTrue(torrent1.searchRating == torrent2.searchRating)
    }

    @Test
    fun shouldRateTorrentEqually() {
        // Given
        val torrent1 = Torrent("Test")
        val torrent2 = Torrent("Test")
        torrent1.name = "Test Title S03E01 720p WEBRip x264-WAKETHEFUP [eztv]"
        torrent2.name = "Test Title S03E01 1080p WEBRip x264-WAKETHEFUP[rarbg]"
        // When
        evaluateRating(torrent1, "Test Title S03E01")
        evaluateRating(torrent2, "Test Title S03E01")
        // Then
        Assertions.assertEquals(torrent1.searchRating, torrent2.searchRating)
    }

    @Disabled
    @Test
    fun shouldCleanTorrentNameAnimeSeries() {
        val resultString = getNormalizedTorrentStringWithSpaces("Bla Blub (2004) (1080p FUNI Dual Audio WEB-DL KaiDubs)")
        Assertions.assertEquals("Paranoia Agent 2004", resultString)
    }

    @Test
    fun determineMovieFileType() {
        // Given
        val filesFromTorrent = listOf(TorrentFile(name = "Test.Movie.mkv"))
        // When
        val typeOfMedia = TorrentHelper.determineTypeOfMedia(filesFromTorrent)
        // THen
        assertThat(typeOfMedia).isEqualTo(TorrentType.MOVIES)
    }

    @Test
    fun shouldRemoveSpecialCharactersFromTitle() {
        assertThat(TorrentHelper.removeSpecialCharacters("Mission: Impossible - Fallout")).isEqualTo("Mission Impossible Fallout")
        assertThat(TorrentHelper.removeSpecialCharacters("Ocean's Eleven")).isEqualTo("Oceans Eleven")
        assertThat(TorrentHelper.removeSpecialCharacters("Die Ärzte: Live")).isEqualTo("Die Ärzte Live")
    }

    @Test
    fun shouldExtractNameFromDnIfDnIsTheLastParameter() {
        val torrent = Torrent.of("magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&tr=udp%3A%2F%2Ftracker.org%3A6969&dn=Some%20Movie%202020")
        Assertions.assertEquals("Some Movie 2020", TorrentHelper.extractTorrentName(torrent))
    }

    @Test
    fun shouldExtractNameWithOriginalCase() {
        val torrent = Torrent.of("magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&dn=Some.Movie.2020.1080p&tr=udp%3A%2F%2Ftracker.org%3A6969")
        Assertions.assertEquals("Some.Movie.2020.1080p", TorrentHelper.extractTorrentName(torrent))
    }

    @Test
    fun shouldNeverExtractMagnetUriAsName() {
        listOf(
            "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2",
            "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&tr=udp%3A%2F%2Ftracker.org%3A6969",
            "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&dn=&tr=udp%3A%2F%2Ftracker.org%3A6969",
            "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&tr=udp%3A%2F%2Ftracker.org%3A6969&dn=Name",
        ).forEach { magnetUri ->
            val name = TorrentHelper.extractTorrentName(Torrent.of(magnetUri))
            assertThat(name).describedAs(magnetUri).doesNotContain("magnet:").doesNotContain("btih").doesNotContain("?").doesNotContain("&")
        }
    }

    @Test
    fun shouldFallbackToHashIfMagnetUriHasNoName() {
        val torrent = Torrent.of("magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&tr=udp%3A%2F%2Ftracker.org%3A6969")
        Assertions.assertEquals("10e93389d579ba4538c68b8d297268a81620fae2", TorrentHelper.extractTorrentName(torrent))
    }

    @Test
    fun shouldNotUseMagnetUriAsNameEvenIfSetAsName() {
        val torrent = Torrent.of("magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&dn=Some.Movie.2020")
        torrent.name = "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2"
        Assertions.assertEquals("Some.Movie.2020", TorrentHelper.extractTorrentName(torrent))
    }

    @Test
    fun shouldExtractFileNameFromDirectDownloadUrl() {
        val torrent = Torrent.of("https://example.com/files/Some.Movie.2020.mkv?token=abc")
        Assertions.assertEquals("Some.Movie.2020.mkv", TorrentHelper.extractTorrentName(torrent))
    }

    @Test
    fun shouldDetectMagnetUriLikeNames() {
        Assertions.assertTrue(TorrentHelper.isMagnetUriLike("magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2"))
        Assertions.assertTrue(TorrentHelper.isMagnetUriLike(" MAGNET:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2"))
        Assertions.assertTrue(TorrentHelper.isMagnetUriLike("xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2"))
        Assertions.assertFalse(TorrentHelper.isMagnetUriLike("Some.Movie.2020.1080p"))
        Assertions.assertFalse(TorrentHelper.isMagnetUriLike("Magnetic.Fields.2020"))
        Assertions.assertFalse(TorrentHelper.isMagnetUriLike(null))
    }

    @Test
    fun shouldKeepRemoteNameIfUsable() {
        val torrent = Torrent("Premiumize")
        torrent.name = "Some.Movie.2020.1080p"
        Assertions.assertEquals("Some.Movie.2020.1080p", TorrentHelper.resolveDownloadName(torrent, "Other Name"))
    }

    @Test
    fun shouldResolveDownloadNameFromTransferNameIfRemoteNameIsMagnetUri() {
        val torrent = Torrent("Premiumize")
        torrent.name = "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2"
        torrent.magnetUri = "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2"
        Assertions.assertEquals("Some Movie 2020", TorrentHelper.resolveDownloadName(torrent, "Some Movie 2020"))
    }

    @Test
    fun shouldResolveDownloadNameFromTransferUriIfAllNamesAreMagnetUris() {
        val torrent = Torrent("Premiumize")
        torrent.name = "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2"
        val resolvedName = TorrentHelper.resolveDownloadName(
            torrent,
            "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2",
            "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&dn=Some.Movie.2020&tr=udp%3A%2F%2Ftracker.org%3A6969"
        )
        Assertions.assertEquals("Some.Movie.2020", resolvedName)
    }

    @Test
    fun shouldResolveDownloadNameToHashAsLastResort() {
        val torrent = Torrent("Premiumize")
        torrent.name = "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2"
        torrent.magnetUri = "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2"
        Assertions.assertEquals("10e93389d579ba4538c68b8d297268a81620fae2", TorrentHelper.resolveDownloadName(torrent))
    }
}
