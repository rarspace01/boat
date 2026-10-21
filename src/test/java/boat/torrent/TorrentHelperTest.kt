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

    @Test
    fun shouldExtractTorrentSizeInMB() {
        Assertions.assertEquals(1536.0, TorrentHelper.extractTorrentSizeFromString("1.5 GB"))
        Assertions.assertEquals(1536.0, TorrentHelper.extractTorrentSizeFromString("1.5 GiB"))
        Assertions.assertEquals(700.0, TorrentHelper.extractTorrentSizeFromString("700 MB"))
        Assertions.assertEquals(700.0, TorrentHelper.extractTorrentSizeFromString("700.9 MiB"))
        Assertions.assertEquals(2048.0, TorrentHelper.extractTorrentSizeFromString("<b>2</b> GB"))
        Assertions.assertEquals(1024.0 * 1024.0, TorrentHelper.extractTorrentSizeFromString("1 TB"))
        Assertions.assertEquals(1.5 * 1024.0 * 1024.0, TorrentHelper.extractTorrentSizeFromString("1.5 TiB"))
        Assertions.assertEquals(1536.0, TorrentHelper.extractTorrentSizeFromString(Torrent("Test").apply { size = "1.5 GiB" }))
    }

    @Test
    fun shouldExtractZeroTorrentSizeFromUnparsableString() {
        Assertions.assertEquals(0.0, TorrentHelper.extractTorrentSizeFromString(""))
        Assertions.assertEquals(0.0, TorrentHelper.extractTorrentSizeFromString("unknown"))
        Assertions.assertEquals(0.0, TorrentHelper.extractTorrentSizeFromString("big GB"))
    }

    @Test
    fun shouldFormatByteCountHumanReadable() {
        assertThat(TorrentHelper.humanReadableByteCountBinary(0)).isEqualTo("0 B")
        assertThat(TorrentHelper.humanReadableByteCountBinary(1023)).isEqualTo("1023 B")
        assertThat(TorrentHelper.humanReadableByteCountBinary(-1)).isEqualTo("-1 B")
        // the decimal separator depends on the default locale
        assertThat(TorrentHelper.humanReadableByteCountBinary(1024)).matches("1[.,]00 KiB")
        assertThat(TorrentHelper.humanReadableByteCountBinary(1536)).matches("1[.,]50 KiB")
        assertThat(TorrentHelper.humanReadableByteCountBinary(-1024)).matches("-1[.,]00 KiB")
        assertThat(TorrentHelper.humanReadableByteCountBinary(1024L * 1024)).matches("1[.,]00 MiB")
        assertThat(TorrentHelper.humanReadableByteCountBinary(1024L * 1024 * 1024)).matches("1[.,]00 GiB")
        assertThat(TorrentHelper.humanReadableByteCountBinary(Long.MAX_VALUE)).matches("8[.,]00 EiB")
        assertThat(TorrentHelper.humanReadableByteCountBinary(Long.MIN_VALUE)).matches("-8[.,]00 EiB")
    }

    @Test
    fun shouldFormatDuration() {
        assertThat(TorrentHelper.formatDuration(java.time.Duration.ZERO)).isEqualTo("D: 0hrs 0min 0sec")
        assertThat(TorrentHelper.formatDuration(java.time.Duration.ofSeconds(3725))).isEqualTo("D: 1hrs 2min 5sec")
        assertThat(TorrentHelper.formatDuration(java.time.Duration.ofDays(2).plusHours(3).plusMinutes(4).plusSeconds(5)))
            .isEqualTo("D: 2days 3hrs 4min 5sec")
    }

    @Test
    fun shouldBlocklistBadQualityAndExplicitTorrents() {
        Assertions.assertTrue(TorrentHelper.isBlocklisted(Torrent("Test").apply { name = "Movie.2020.HDCAM.x264" }))
        Assertions.assertTrue(TorrentHelper.isBlocklisted(Torrent("Test").apply { name = "Movie.2020.TS.x264" }))
        Assertions.assertTrue(TorrentHelper.isBlocklisted(Torrent("Test").apply { name = "Movie 2020 TELESYNC" }))
        Assertions.assertTrue(TorrentHelper.isBlocklisted(Torrent("Test").apply { name = "Some XXX title" }))
    }

    @Test
    fun shouldNotBlocklistGoodTorrentsContainingBadQualityLetters() {
        Assertions.assertFalse(TorrentHelper.isBlocklisted(Torrent("Test").apply { name = "Movie.2020.1080p.BluRay.x264" }))
        Assertions.assertFalse(TorrentHelper.isBlocklisted(Torrent("Test").apply { name = "Scam.2020.1080p.WEB" }))
        Assertions.assertFalse(TorrentHelper.isBlocklisted(Torrent("Test").apply { name = "Cats.2019.1080p.WEB" }))
    }

    @Test
    fun shouldValidateTorrents() {
        val validTorrent = Torrent("Test").apply {
            name = "Movie.2020.1080p.BluRay"
            sizeInMB = 1000.0
            magnetUri = "magnet:?xt=urn:btih:abc"
        }
        Assertions.assertTrue(TorrentHelper.isValidTorrent(validTorrent))
        Assertions.assertFalse(TorrentHelper.isValidTorrent(validTorrent.copy(sizeInMB = 0.0)))
        Assertions.assertFalse(TorrentHelper.isValidTorrent(validTorrent.copy(magnetUri = " ")))
        Assertions.assertFalse(TorrentHelper.isValidTorrent(validTorrent.copy(name = "Movie.2020.HDCAM")))
    }

    @Test
    fun shouldBuildMagnetUriFromHash() {
        Assertions.assertEquals(
            "magnet:?xt=urn:btih:abc&dn=My+Movie+%281999%29&tr=http://tracker.opentrackr.org:1337/announce",
            TorrentHelper.buildMagnetUriFromHash("abc", "My Movie (1999)")
        )
    }

    @Test
    fun shouldDetermineTypeOfMediaFromFiles() {
        Assertions.assertEquals(TorrentType.TRANSFER, TorrentHelper.determineTypeOfMedia(emptyList<TorrentFile>()))
        Assertions.assertEquals(
            TorrentType.SERIES_SHOWS,
            TorrentHelper.determineTypeOfMedia(listOf(TorrentFile(name = "Show.S01E01.mkv"), TorrentFile(name = "Show.S01E02.mkv"), TorrentFile(name = "info.nfo")))
        )
        Assertions.assertEquals(
            TorrentType.MOVIES,
            TorrentHelper.determineTypeOfMedia(listOf(TorrentFile(name = "Movie.2020.1080p.mkv"), TorrentFile(name = "sample.txt")))
        )
        Assertions.assertEquals(
            TorrentType.TRANSFER,
            TorrentHelper.determineTypeOfMedia(listOf(TorrentFile(name = "Book.pdf"), TorrentFile(name = "Cover.jpg")))
        )
    }

    @Test
    fun shouldDetermineTypeOfMediaFromFileName() {
        Assertions.assertEquals(TorrentType.MOVIES, TorrentHelper.determineTypeOfMedia("Movie.2020.1080p.mkv"))
        Assertions.assertEquals(TorrentType.MOVIES, TorrentHelper.determineTypeOfMedia("movie 2020 1080p mp4"))
        Assertions.assertEquals(TorrentType.MOVIES, TorrentHelper.determineTypeOfMedia("Movie.2020.TS"))
        Assertions.assertEquals(TorrentType.SERIES_SHOWS, TorrentHelper.determineTypeOfMedia("Show.S01E01.720p.MKV"))
        Assertions.assertEquals(TorrentType.SERIES_SHOWS, TorrentHelper.determineTypeOfMedia("Show Season 1 Episode 1.avi"))
        Assertions.assertEquals(TorrentType.TRANSFER, TorrentHelper.determineTypeOfMedia("Book.pdf"))
    }

    @Test
    fun shouldNotDetermineNamesEndingLikeAnExtensionAsMedia() {
        Assertions.assertEquals(TorrentType.TRANSFER, TorrentHelper.determineTypeOfMedia("Cats"))
        Assertions.assertEquals(TorrentType.TRANSFER, TorrentHelper.determineTypeOfMedia("Looney Tunes Shorts"))
        Assertions.assertEquals(TorrentType.TRANSFER, TorrentHelper.determineTypeOfMedia("Remov"))
        Assertions.assertEquals(TorrentType.TRANSFER, TorrentHelper.determineTypeOfMedia("Notes.txt"))
    }

    @Test
    fun shouldPercentEncodeHashBytesWithoutPlusForSpace() {
        val bytes = byteArrayOf(0x41, 0x20, 0x00, 0xff.toByte(), 0x7e, 0x2b, 0x2d, 0x30)

        Assertions.assertEquals("A%20%00%FF~%2B-0", TorrentHelper.urlEncode(bytes))
        Assertions.assertEquals("", TorrentHelper.urlEncode(ByteArray(0)))
    }
}
