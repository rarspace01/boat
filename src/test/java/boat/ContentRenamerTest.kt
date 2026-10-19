package boat
import org.assertj.core.api.Assertions.assertThat

import boat.info.MediaItem
import boat.info.MediaProxyService
import boat.info.MediaType
import boat.info.TheMovieDataBaseService
import boat.info.TheTVDBService
import boat.utilities.HttpHelper
import org.junit.jupiter.api.Test
import java.io.File

class ContentRenamerTest {

    @Test
    fun testExtraction() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        val testPaths = listOf(
            "~/data/TV/The.Mandalorian.S01E01.1080p.mkv",
            "~/data/TV/Stranger.Things.S03.2019/Stranger.Things.S03E05.mkv",
            "~/data/Movies/Inception.2010.720p.mkv",
            "~/data/TV/The.Boys.S02E08.mkv",
            "~/data/Movies/Interstellar.2014.1080p.mkv",
            "~/data/TV/Dark.S01.GERMAN.1080p/Dark.S01E01.mkv",
            "~/data/TV/Chernobyl.S01.1080p/Chernobyl.E01.mkv",
            "~/Series-Shows/0-9/1000 Ways to Die/Season 1/1000.Ways.to.Die.S01E01.Life Will Kill You.avi",
            "~/Series-Shows/0-9/1000.Lb.Sisters/1000-lb.Sisters S01E01.720p WEBRip x264-KOMPOST.mkv",
            "~/Series-Shows/B/BATMAN The Animated Series (1992-1999) - COMPLETE Season 1-6, TV S01-S06 and 2 Movies - 1080p BluRay x264/Season 1 (1992-93)/Batman T.A.S - S01 E01 - The Cat and The Claw, Part 1 (1080p - BluRay).mp4",
            "~/Series-Shows/S/A.Shop.For.Killers.Korean.S01.2024.Hevc33/A.Shop.for.Killers.KOREAN.S01E01.2024.1080p.WEBRip.x265.HEVC-kkolev33.mkv",
            "~/Series-Shows/S/Salamander.(2012)/Season 1/Salamander (2012) - S01E01 - Episode 1 (1080p WEB-DL x265 r00t).mkv",
            "~/Series-Shows/M/La Meilleure Version de Moi-même/La.Meilleure.Version.De.Moi-Meme.S01E01.FRENCH.WEB-DL.XviD-ZT.avi",
            "~/Series-Shows/M/Magnet:?xt=urn:btih:52e24d7fe3557a389121c7d89d0e039122028d31&/The.Rookie.S04E01.Life.and.Death.1080p.10bit.AMZN.WEB-DL.DDP5.1.HEVC-Vyndros.mkv",
            "~/Series-Shows/#/.Hack Legend Of The Twilight/[Exiled-Destiny]_Hack_Legend_Of_The_Twilight_Ep01_(1E372EE5).mkv",
            "~/Series-Shows/#/.Hack Sign 1-28+Extras/[V-A]_hack_SIGN_-_01_[BB605406].mkv",
            "~/Series-Shows/#/_HACK/ROOTS/hack_roots-24.avi",
            "~/Series-Shows/0-9/071c.Wars.The.Bad.Batch/071c-Star.Wars.The.Bad.Batch.S01E11.1080p.WEB.H264-EXPLOIT[ettv].torrent.mkv",
            "~/Series-Shows/0-9/24.Complete.With.Subtitles/24 Season 2/24 - [2x01] - Day 2  8 00 A.M. - 9 00 A.M.mkv",
            "~/Series-Shows/0-9/24.Complete.With.Subtitles/24 Season 2/24 - [2x03] - Day 2  10 00 A.M. - 11 00 A.M.srt",
            "~/Series-Shows/S/Star.Trek.Discovery.S04E01.1080p.web.x264-vxt.mkv",
            "~/Series-Shows/A/AEON FLUX (1991-1995) - Complete Animated TV Series and 2005 Movie - 720p x264/Aeon Flux - complete animated series/aeon flux dvd 1.mkv",
            "~/Series-Shows/#/.Hack Legend Of The Twilight/[Exiled-Destiny]_Hack_Legend_Of_The_Twilight_Ep01_(1E372EE5).mkv",
            "~/Series-Shows/#/.Hack Sign 1-28+Extras/[V-A]_hack_SIGN_-_01_[BB605406].mkv",
            "~/Series-Shows/#/_HACK/ROOTS/hack_roots-24.avi",
            "~/Series-Shows/#/_HACK/Legend_o_T/hack-Legend of the Twilight 01 - The Legendary Hero.avi",
            "~/Series-Shows/C/Come.Fly.With.Me.S01/teneighty-cfwm.s01e01.mkv",
            "~/Series-Shows/D/Dragon Ball Super/Dragon Ball Super - Completo [WEB-DL - 1080p]/Dragon%20Ball%20Super%20-%20001%20%5B1080p%5D.mkv",
            "~/Series-Shows/0-9/071c.Wars.The.Bad.Batch/071c-Star.Wars.The.Bad.Batch.S01E11.1080p.WEB.H264-EXPLOIT[ettv].torrent.mkv",
            "~/Series-Shows/0-9/90.Day.Fiance.Happily.Ever.After.No.Turning.Back/www.Torrenting.com - 90.Day.Fiance.Happily.Ever.After.S03E08.No.Turning.Back.XviD-AFG.avi",
            "~/Series-Shows/A/Adventure Time/Season 1/01.01 - Slumber Party Panic.mp4",
            "~/Series-Shows/A/Avatar - The Last Airbender [1080p]/Book 1; Water/101 - The Boy in the Iceberg.mp4",
            "~/Series-Shows/D/Duckman/Season 1/Duckman 101 - I, Duckman.mkv",
            "~/Series-Shows/D/Dragon Ball Super/Dragon Ball Super - Completo [WEB-DL - 1080p]/Dragon%20Ball%20Super%20-%20101%20%5B1080p%5D.mkv",
            "~/Series-Shows/Z/Zombieland%20Saga%20S02%201080p%20BDRip%2010%20bits%20DD%20x265-EMBER.torrent/S02E12-The Greatest SAGA in History [F9FE3939].mkv",
            "~/Series-Shows/R/Reservation Dogs/Reservation.Dogs.S01.1080p.WEBRip.x265-KONTRAST/Subs/Reservation.Dogs.S01E07.1080p.WEBRip.x265-KONTRAST/6_spa.srt",
            "~/Series-Shows/#/_HACK/SIGN/HS28.avi",
            "~/Series-Shows/0-9/24.Complete.With.Subtitles/24 Season 1/24 Season 1/24 - S01xE11.mp4"
        )

        println("\n--- Testing Extraction Logic ---")
        testPaths.forEach { fullName ->
            //File(fullName).createNewFile()
            val file = File(fullName)
            val name = contentRenamer.extractName(file)
            val year = contentRenamer.extractYear(fullName)
            val season = contentRenamer.extractSeason(file)
            val episode = contentRenamer.extractEpisode(file)
            System.out.println("[DEBUG_LOG] F: $fullName")
            System.out.println("[DEBUG_LOG]  -> Name: $name, Year: $year, Season: $season, Episode: $episode")
            if (fullName.contains("teneighty-cfwm")) {
                assertThat(name).isEqualTo("Come Fly With Me")
            }
            if (fullName.contains("Dragon%20Ball%20Super")) {
                assertThat(name).isEqualTo("Dragon Ball Super")
            }
            if (fullName.contains("071c-Star.Wars.The.Bad.Batch")) {
                assertThat(name).isEqualTo("Star Wars The Bad Batch")
            }
            if (fullName.contains("www.Torrenting.com")) {
                assertThat(name).isEqualTo("90 Day Fiance Happily Ever After")
            }
            if (fullName.contains("Adventure Time")) {
                assertThat(name).isEqualTo("Adventure Time")
            }
            if (fullName.contains("Avatar - The Last Airbender")) {
                assertThat(name).isEqualTo("Avatar - The Last Airbender")
            }
            if (fullName.contains("Duckman")) {
                assertThat(name).isEqualTo("Duckman")
                assertThat(season).isEqualTo(1)
                assertThat(episode).isEqualTo(1)
            }
            if (fullName.contains("101 - The Boy in the Iceberg.mp4")) {
                val ep = contentRenamer.extractEpisode(file)
                val se = contentRenamer.extractSeason(file)
                assertThat(se).isEqualTo(1)
                assertThat(ep).isEqualTo(1)
            }
            if (fullName.contains("Dragon Ball Super - 101 [1080p].mkv")) {
                val ep = contentRenamer.extractEpisode(file)
                assertThat(ep).isEqualTo(101)
            }
            if (fullName.contains("Reservation Dogs")) {
                assertThat(name).isEqualTo("Reservation Dogs")
                assertThat(season).isEqualTo(1)
                assertThat(episode).isEqualTo(7)
            }
            if (fullName.contains("Zombieland%20Saga")) {
                assertThat(name).isEqualTo("Zombieland Saga")
            }
            if (fullName.contains("SIGN/HS28.avi")) {
                assertThat(name).isEqualToIgnoringCase("HACK SIGN")
                assertThat(episode).isEqualTo(28)
            }
            if (fullName.contains("24 - S01xE11.mp4")) {
                assertThat(name).isEqualTo("24")
                assertThat(season).isEqualTo(1)
                assertThat(episode).isEqualTo(11)
            }
        }
    }

    @Test
    fun testIsJunk() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        // Junk names
        assertThat(contentRenamer.isJunk("")).isTrue()
        assertThat(contentRenamer.isJunk("   ")).isTrue()
        assertThat(contentRenamer.isJunk("A")).isTrue()
        assertThat(contentRenamer.isJunk("#")).isTrue()
        assertThat(contentRenamer.isJunk("0-9")).isTrue()
        assertThat(contentRenamer.isJunk("TV")).isTrue()
        assertThat(contentRenamer.isJunk("Series-Shows")).isTrue()
        assertThat(contentRenamer.isJunk("Downloads")).isTrue()
        assertThat(contentRenamer.isJunk("Season 1")).isTrue()
        assertThat(contentRenamer.isJunk("Season 02")).isTrue()
        assertThat(contentRenamer.isJunk("Disc 1")).isTrue()
        assertThat(contentRenamer.isJunk("CD 1")).isTrue()
        assertThat(contentRenamer.isJunk("DVD 1")).isTrue()
        assertThat(contentRenamer.isJunk("Vol 1")).isTrue()
        assertThat(contentRenamer.isJunk("Volume 2")).isTrue()
        assertThat(contentRenamer.isJunk("Book 1")).isTrue()
        assertThat(contentRenamer.isJunk("Extras")).isTrue()
        assertThat(contentRenamer.isJunk("Featurettes")).isTrue()
        assertThat(contentRenamer.isJunk("Sample")).isTrue()
        assertThat(contentRenamer.isJunk("Samples")).isTrue()
        assertThat(contentRenamer.isJunk("Trailer")).isTrue()
        assertThat(contentRenamer.isJunk("Trailers")).isTrue()
        assertThat(contentRenamer.isJunk("Subs")).isTrue()
        assertThat(contentRenamer.isJunk("Subtitles")).isTrue()
        assertThat(contentRenamer.isJunk("UK")).isTrue()
        assertThat(contentRenamer.isJunk("US")).isTrue()
        assertThat(contentRenamer.isJunk("UK Season 05")).isTrue()
        assertThat(contentRenamer.isJunk("UK Season 05 [2007]")).isTrue()
        assertThat(contentRenamer.isJunk("6 spa")).isTrue()
        assertThat(contentRenamer.isJunk("6_spa")).isTrue()
        assertThat(contentRenamer.isJunk("1080p")).isTrue()
        assertThat(contentRenamer.isJunk("720p")).isTrue()
        assertThat(contentRenamer.isJunk("x264")).isTrue()
        assertThat(contentRenamer.isJunk("x265")).isTrue()
        assertThat(contentRenamer.isJunk("RARBG")).isTrue()
        assertThat(contentRenamer.isJunk("101")).isTrue()
        assertThat(contentRenamer.isJunk("01.01")).isTrue()
        assertThat(contentRenamer.isJunk("1x01")).isTrue()
        assertThat(contentRenamer.isJunk("S01E01")).isTrue()
        assertThat(contentRenamer.isJunk("Deleted Scenes")).isTrue()
        assertThat(contentRenamer.isJunk("Alternate Ending")).isTrue()
        assertThat(contentRenamer.isJunk("Alternate Endings")).isTrue()
        assertThat(contentRenamer.isJunk("Special Features")).isTrue()
        assertThat(contentRenamer.isJunk("HS22")).isTrue()
        assertThat(contentRenamer.isJunk("E01")).isTrue()
        assertThat(contentRenamer.isJunk("EP01")).isTrue()
        assertThat(contentRenamer.isJunk("Outtakes")).isTrue()
        assertThat(contentRenamer.isJunk("Bloopers")).isTrue()
        assertThat(contentRenamer.isJunk("Gag Reel")).isTrue()
        assertThat(contentRenamer.isJunk("Behind The Scenes")).isTrue()
        assertThat(contentRenamer.isJunk("Making Of")).isTrue()
        assertThat(contentRenamer.isJunk("Screentests")).isTrue()
        assertThat(contentRenamer.isJunk("Covers and Fonts")).isTrue()
        assertThat(contentRenamer.isJunk("Original Scripts")).isTrue()
        assertThat(contentRenamer.isJunk("http://example.com")).isTrue()
        assertThat(contentRenamer.isJunk("magnet:?xt=urn:btih:...")).isTrue()

        // Legitimate titles (not junk)
        assertThat(contentRenamer.isJunk("Dark")).isFalse()
        assertThat(contentRenamer.isJunk("Lost")).isFalse()
        assertThat(contentRenamer.isJunk("The Mandalorian")).isFalse()
        assertThat(contentRenamer.isJunk("Inception")).isFalse()
        assertThat(contentRenamer.isJunk("Chernobyl")).isFalse()
        assertThat(contentRenamer.isJunk("Star Wars The Bad Batch")).isFalse()
        assertThat(contentRenamer.isJunk("Avatar - The Last Airbender")).isFalse()
        assertThat(contentRenamer.isJunk("Reservation Dogs")).isFalse()
        assertThat(contentRenamer.isJunk("24 Complete With Subtitles")).isFalse()
        assertThat(contentRenamer.isJunk("9-1-1")).isFalse()
        assertThat(contentRenamer.isJunk("9 1 1")).isFalse()
        assertThat(contentRenamer.isJunk("9-1-1: Lone Star")).isFalse()
    }

    @Test
    fun testConservativeExtractionAndAuxiliaryFiles() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        // Auxiliary files in show folders resolve to the show name
        val angelStunts = File("~/Series-Shows/A/Angel Complete Series Season 1,2,3,4,5 + Extras, Subs/Extras/Season 2/Featurettes/Stunts.mkv")
        assertThat(contentRenamer.extractName(angelStunts)).startsWith("Angel")

        val stargateTrailer = File("~/Series-Shows/S/Stargate SG1 Ultimate Fan Collection/Season 1/Extras/Season 2 Trailer.mp4")
        assertThat(contentRenamer.extractName(stargateTrailer)).startsWith("Stargate SG1")

        val stargateFeaturette = File("~/Series-Shows/S/Stargate SG1 Ultimate Fan Collection/Season 1/Extras/CostumeDesignFeaturette.mp4")
        assertThat(contentRenamer.extractName(stargateFeaturette)).startsWith("Stargate SG1")

        val twentyFourExtra = File("~/Series-Shows/0-9/24.Complete.With.Subtitles/24 Season 1/24 EXTRAS/Alternate Ending.mp4")
        assertThat(contentRenamer.extractName(twentyFourExtra)).isEqualTo("24")
        assertThat(contentRenamer.extractSeason(twentyFourExtra)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(twentyFourExtra)).isNull()

        val twentyFourInterview = File("~/Series-Shows/0-9/24.Complete.With.Subtitles/24 Season 1/24 EXTRAS/Kiefer Sutherland on season 1 of 24.mp4")
        assertThat(contentRenamer.extractName(twentyFourInterview)).isEqualTo("24")
        assertThat(contentRenamer.extractSeason(twentyFourInterview)).isEqualTo(1)

        val nineOneOneFile = File("~/Series-Shows/0-9/9-1-1/9-1-1.S05E06.1080p.WEB.H264-CAKES[TGx].mkv")
        assertThat(contentRenamer.extractName(nineOneOneFile)).isEqualTo("9-1-1")
        assertThat(contentRenamer.extractSeason(nineOneOneFile)).isEqualTo(5)
        assertThat(contentRenamer.extractEpisode(nineOneOneFile)).isEqualTo(6)

        val hackSignFile = File("~/Series-Shows/#/_HACK/SIGN/HS22.avi")
        assertThat(contentRenamer.extractName(hackSignFile)).isEqualToIgnoringCase("HACK SIGN")
        assertThat(contentRenamer.extractSeason(hackSignFile)).isNull()
        assertThat(contentRenamer.extractEpisode(hackSignFile)).isEqualTo(22)

        val hackSignExtrasFile = File("~/Series-Shows/#/.Hack Sign 1-28+Extras/[V-A]_hack_SIGN_-_05_[E3229E6C].mkv")
        assertThat(contentRenamer.extractName(hackSignExtrasFile)).isEqualTo("Hack Sign")
        assertThat(contentRenamer.extractSeason(hackSignExtrasFile)).isNull()
        assertThat(contentRenamer.extractEpisode(hackSignExtrasFile)).isEqualTo(5)

        val hackLegendTwilightFile = File("~/Series-Shows/#/_HACK/Legend_o_T/hack-Legend of the Twilight 08 - The Solitary Knight.avi")
        assertThat(contentRenamer.extractName(hackLegendTwilightFile)).isEqualTo("hack-Legend of the Twilight")
        assertThat(contentRenamer.extractSeason(hackLegendTwilightFile)).isNull()
        assertThat(contentRenamer.extractEpisode(hackLegendTwilightFile)).isEqualTo(8)

        val hackRootsFile = File("~/Series-Shows/#/_HACK/ROOTS/hack_roots-21.avi")
        assertThat(contentRenamer.extractName(hackRootsFile)).isEqualTo("HACK ROOTS")
        assertThat(contentRenamer.extractSeason(hackRootsFile)).isNull()
        assertThat(contentRenamer.extractEpisode(hackRootsFile)).isEqualTo(21)

        // Standalone junk files or files in generic folders with no show name should NOT extract a wrong name (return empty string)
        val standaloneSample = File("~/Downloads/Sample.mkv")
        assertThat(contentRenamer.extractName(standaloneSample)).isEqualTo("")

        val standaloneFeaturette = File("~/Downloads/Featurettes/Stunts.mkv")
        assertThat(contentRenamer.extractName(standaloneFeaturette)).isEqualTo("")

        val standaloneTrailer = File("~/Downloads/Extras/Season 2 Trailer.mp4")
        assertThat(contentRenamer.extractName(standaloneTrailer)).isEqualTo("")

        val standaloneSubs = File("~/Downloads/Subs/6_spa.srt")
        assertThat(contentRenamer.extractName(standaloneSubs)).isEqualTo("")

        val standaloneCodec = File("~/Downloads/1080p.x264.mkv")
        assertThat(contentRenamer.extractName(standaloneCodec)).isEqualTo("")

        val standaloneRARBG = File("~/Downloads/RARBG.mp4")
        assertThat(contentRenamer.extractName(standaloneRARBG)).isEqualTo("")
    }

    @Test
    fun testGetSafeTitle() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        assertThat(contentRenamer.getSafeTitle(MediaItem("Star Wars: The Bad Batch"))).isEqualTo("Star Wars The Bad Batch")
        assertThat(contentRenamer.getSafeTitle(MediaItem("Face/Off"))).isEqualTo("FaceOff")
        assertThat(contentRenamer.getSafeTitle(MediaItem("What If...?"))).isEqualTo("What If...")
        assertThat(contentRenamer.getSafeTitle(MediaItem("Back\\Slash"))).isEqualTo("BackSlash")
        assertThat(contentRenamer.getSafeTitle(MediaItem("Title*With\"Dangerous<Chars>And|Pipes"))).isEqualTo("TitleWithDangerousCharsAndPipes")
        assertThat(contentRenamer.getSafeTitle(MediaItem("1000-lb Sisters (2020)"))).isEqualTo("1000-lb Sisters (2020)")
        assertThat(contentRenamer.getSafeTitle("Simple Title")).isEqualTo("Simple Title")
        assertThat(contentRenamer.getSafeTitle("This//Hack")).isEqualTo("This Hack")
    }

    @Test
    fun testExtractSeasonAndEpisode() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )
        val seriesItem = MediaItem(title = "Breaking Bad", type = boat.info.MediaType.Series, id = "{tvdb-81189}")
        seriesItem.seasonCount = 5
        val movieItem = MediaItem(title = "Inception", year = 2010, type = boat.info.MediaType.Movie, id = "{tmdb-27205}")

        assertThat(contentRenamer.extractSeasonAndEpisode(File("Breaking.Bad.S02E03.mkv"), seriesItem)).isEqualTo(Pair(2, 3))
        // episode without season defaults to season 1
        assertThat(contentRenamer.extractSeasonAndEpisode(File("Breaking Bad - 05.mkv"), seriesItem)).isEqualTo(Pair(1, 5))
        // absolute numbering within the season count keeps its season
        assertThat(contentRenamer.extractSeasonAndEpisode(File("Breaking Bad - 305.mkv"), seriesItem)).isEqualTo(Pair(3, 5))
        assertThat(contentRenamer.extractSeasonAndEpisode(File("Breaking Bad - 105.mkv"), seriesItem)).isEqualTo(Pair(1, 5))
        // beyond the season count it stays an absolute episode
        assertThat(contentRenamer.extractSeasonAndEpisode(File("Breaking Bad - 905.mkv"), seriesItem)).isEqualTo(Pair(1, 905))
        // movies have no season/episode
        assertThat(contentRenamer.extractSeasonAndEpisode(File("Inception.2010.E01.mkv"), movieItem)).isEqualTo(Pair(null, null))
    }

    @Test
    fun testGetTargetFile() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )
        val seriesItem = MediaItem(title = "The Mandalorian", type = boat.info.MediaType.Series, id = "12345")

        // Season 1, Episode 1 -> S01E01
        val target1 = contentRenamer.getTargetFile(File("mandalorian.mkv"), seriesItem, "The Mandalorian", 1, 1)
        assertThat(target1).contains("/Season 1/The Mandalorian.S01E01.mkv")

        // Season 1, Episode 101 -> S01E101
        val target2 = contentRenamer.getTargetFile(File("mandalorian.mkv"), seriesItem, "The Mandalorian", 1, 101)
        assertThat(target2).contains("/Season 1/The Mandalorian.S01E101.mkv")

        // Season null, Episode 5 -> S01E05
        val target3 = contentRenamer.getTargetFile(File("mandalorian.mkv"), seriesItem, "The Mandalorian", null, 5)
        assertThat(target3).contains("/The Mandalorian.S01E05.mkv")

        // Season 10, Episode 24 -> S10E24
        val target4 = contentRenamer.getTargetFile(File("mandalorian.mkv"), seriesItem, "The Mandalorian", 10, 24)
        assertThat(target4).contains("/Season 10/The Mandalorian.S10E24.mkv")

        // Movie (no season, no episode)
        val movieItem = MediaItem(title = "Inception", type = boat.info.MediaType.Movie, id = "67890")
        val targetMovie = contentRenamer.getTargetFile(File("inception.mkv"), movieItem, "Inception", null, null)
        assertThat(targetMovie).endsWith("/Movies/I/Inception 67890.mkv")

        // Movie with year, resolution and codec
        val inceptionItem = MediaItem(title = "Inception", year = 2010, type = boat.info.MediaType.Movie, id = "{tmdb-27205}")
        val movieFolder = File("Inception.2010.1080p.BluRay.x264-SPARKS")
        assertThat(contentRenamer.getTargetFile(File(movieFolder, "sparks-inception.mkv"), inceptionItem, "Inception", null, null))
            .endsWith("/Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.mkv")

        // Movie subtitles with language code follow the movie name
        assertThat(contentRenamer.getTargetFile(File(movieFolder, "Inception.2010.1080p.en.srt"), inceptionItem, "Inception", null, null))
            .endsWith("/Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.en.srt")
        assertThat(contentRenamer.getTargetFile(File(movieFolder, "Inception.ger.forced.srt"), inceptionItem, "Inception", null, null))
            .endsWith("/Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.ger.forced.srt")

        // Movie subtitles without language code and extras keep their name, so they don't overwrite the movie
        assertThat(contentRenamer.getTargetFile(File(File(movieFolder, "Subs"), "2_English.srt"), inceptionItem, "Inception", null, null))
            .endsWith("/Movies/I/2_English.srt")
        assertThat(contentRenamer.getTargetFile(File(File(movieFolder, "Sample"), "sparks-inception-sample.mkv"), inceptionItem, "Inception", null, null))
            .endsWith("/Movies/I/sparks-inception-sample.mkv")
        assertThat(contentRenamer.getTargetFile(File(File(movieFolder, "Featurettes"), "Dreams.mkv"), inceptionItem, "Inception", null, null))
            .endsWith("/Movies/I/Dreams.mkv")

        // Multi part movies get a part suffix
        assertThat(contentRenamer.getTargetFile(File(movieFolder, "inception.cd2.mkv"), inceptionItem, "Inception", null, null))
            .endsWith("/Movies/I/Inception (2010) {tmdb-27205}.1080p.x264 - pt2.mkv")

        // Movies ignore season/episode
        assertThat(contentRenamer.getTargetFile(File(movieFolder, "sparks-inception.mkv"), inceptionItem, "Inception", 1, 5))
            .endsWith("/Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.mkv")

        // 9-1-1 Series with resolution and codec
        val nineOneOneItem = MediaItem(title = "9-1-1", type = boat.info.MediaType.Series, id = "{tvdb-334586}")
        val targetNineOneOne = contentRenamer.getTargetFile(File("9-1-1.S05E06.1080p.WEB.H264-CAKES[TGx].mkv"), nineOneOneItem, "9-1-1", 5, 6)
        assertThat(targetNineOneOne).endsWith("/Series-Shows/0-9/9-1-1 {tvdb-334586}/Season 5/9-1-1.S05E06.1080p.h264.mkv")

        // Buffy Series with resolution and codec
        val buffyItem = MediaItem(title = "Buffy the Vampire Slayer", type = boat.info.MediaType.Series, id = "{tvdb-70327}")
        val targetBuffy = contentRenamer.getTargetFile(
            File("Buffy.The.Vampire.Slayer.S08E07.No.Future.For.You.(2).MOTION.COMIC.720p.WEB-DL.AC3-5.1.x264-DEMENTA.mkv"),
            buffyItem,
            "Buffy The Vampire Slayer",
            8,
            7
        )
        assertThat(targetBuffy).endsWith("/Series-Shows/B/Buffy the Vampire Slayer {tvdb-70327}/Season 8/Buffy The Vampire Slayer.S08E07.720p.x264.mkv")

        // The Big Bang Theory Series (Folder should be B not T)
        val tbbtItem = MediaItem(title = "The Big Bang Theory", type = boat.info.MediaType.Series, id = "{tvdb-80379}")
        val targetTbbt = contentRenamer.getTargetFile(File("The Big Bang Theory S02E08.mkv"), tbbtItem, "The Big Bang Theory", 2, 8)
        assertThat(targetTbbt).endsWith("/Series-Shows/B/The Big Bang Theory {tvdb-80379}/Season 2/The Big Bang Theory.S02E08.mkv")

        // .hack//SIGN series
        val hackSignItem = MediaItem(title = ".hack//SIGN", type = boat.info.MediaType.Series, id = "{tmdb-333800}")
        val targetHackSign = contentRenamer.getTargetFile(File("HS22.avi"), hackSignItem, "SIGN", 1, 22)
        assertThat(targetHackSign).endsWith("/Series-Shows/H/.hack SIGN {tmdb-333800}/Season 1/SIGN.S01E22.avi")

        // Extra with season but no episode
        val extraItem = MediaItem(title = "24", type = boat.info.MediaType.Series, id = "{tvdb-1234}")
        val targetExtra = contentRenamer.getTargetFile(File("Alternate Ending.mp4"), extraItem, "24", 1, null)
        assertThat(targetExtra).endsWith("/Series-Shows/0-9/24 {tvdb-1234}/Season 1/Alternate Ending.mp4")

        // Extra with series media type but no season and no episode (keep original file name in series folder)
        val hackSignExtra = File("~/Series-Shows/#/.Hack Sign 1-28+Extras/[V-A]_hack_SIGN_-_Textless_Ending_[934E6984].mkv")
        val targetHackSignExtra = contentRenamer.getTargetFile(hackSignExtra, hackSignItem, "Hack Sign", null, null)
        assertThat(targetHackSignExtra).endsWith("/Series-Shows/H/.hack SIGN {tmdb-333800}/[V-A]_hack_SIGN_-_Textless_Ending_[934E6984].mkv")
    }

    @Test
    fun testBugsBunnyAndBlacklistExtraction() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        // Bugs Bunny file
        val bugsBunnyFile = File("~/Series-Shows/B/Bugs Bunny Pack (1938-2004)/Bugs Bunny - (Ep. 23) - Super-Rabbit.avi")
        val bugsName = contentRenamer.extractName(bugsBunnyFile)
        val bugsEpisode = contentRenamer.extractEpisode(bugsBunnyFile)
        assertThat(bugsName).isEqualTo("Bugs Bunny")
        assertThat(bugsEpisode).isEqualTo(23)

        // The Blacklist file
        val blacklistFile = File("~/Series-Shows/B/The Blacklist/Season 1/The Blacklist Episode 14 - Madeline Pratt.mp4")
        val blacklistName = contentRenamer.extractName(blacklistFile)
        val blacklistEpisode = contentRenamer.extractEpisode(blacklistFile)
        val blacklistSeason = contentRenamer.extractSeason(blacklistFile)
        assertThat(blacklistName).isEqualTo("The Blacklist")
        assertThat(blacklistSeason).isEqualTo(1)
        assertThat(blacklistEpisode).isEqualTo(14)

        // Black Butler file
        val blackButlerFile = File("~/Series-Shows/B/Black Butler/Black Butler Season 2 [1080p]/Black Butler 02.mkv")
        val blackButlerName = contentRenamer.extractName(blackButlerFile)
        val blackButlerEpisode = contentRenamer.extractEpisode(blackButlerFile)
        val blackButlerSeason = contentRenamer.extractSeason(blackButlerFile)
        assertThat(blackButlerName).isEqualTo("Black Butler")
        assertThat(blackButlerSeason).isEqualTo(2)
        assertThat(blackButlerEpisode).isEqualTo(2)
    }

    @Test
    fun testUmbrellaFranchiseExtraction() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        // /Series-Shows/#/_HACK/SIGN/HS28.avi -> "HACK SIGN"
        val hackSignFile = File("~/Series-Shows/#/_HACK/SIGN/HS28.avi")
        val hackSignName = contentRenamer.extractName(hackSignFile)
        val hackSignEpisode = contentRenamer.extractEpisode(hackSignFile)
        assertThat(hackSignName).isEqualToIgnoringCase("HACK SIGN")
        assertThat(hackSignEpisode).isEqualTo(28)

        // /Series-Shows/S/Star Trek/Discovery/Season 1/01.mkv -> "Star Trek Discovery"
        val starTrekFile = File("~/Series-Shows/S/Star Trek/Discovery/Season 1/01.mkv")
        val starTrekName = contentRenamer.extractName(starTrekFile)
        println("[DEBUG_LOG] starTrekName: '$starTrekName'")
        assertThat(starTrekName).isEqualToIgnoringCase("Star Trek Discovery")

        // Boundary check: single-letter folder 'S' and category 'Series-Shows' are not included
        val boundaryFile = File("~/Series-Shows/S/Salamander/Season 1/01.mkv")
        val boundaryName = contentRenamer.extractName(boundaryFile)
        assertThat(boundaryName).isEqualToIgnoringCase("Salamander")
    }

    @Test
    fun testTwentyFourExtraction() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        val file1 = File("~/Series-Shows/0-9/24.Complete.With.Subtitles/24 Season 1/24 Season 1/24 - S01xE11.mp4")
        assertThat(contentRenamer.extractName(file1)).isEqualTo("24")
        assertThat(contentRenamer.extractSeason(file1)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(file1)).isEqualTo(11)

        val file2 = File("~/Series-Shows/0-9/24.Complete.With.Subtitles/24 Season 2/24 - [2x01] - Day 2  8 00 A.M. - 9 00 A.M.mkv")
        assertThat(contentRenamer.extractName(file2)).isEqualTo("24")
        assertThat(contentRenamer.extractSeason(file2)).isEqualTo(2)
        assertThat(contentRenamer.extractEpisode(file2)).isEqualTo(1)

        val file3 = File("~/Series-Shows/0-9/24.Complete.With.Subtitles/24 Season 1/24 EXTRAS/Alternate Ending.mp4")
        assertThat(contentRenamer.extractName(file3)).isEqualTo("24")
        assertThat(contentRenamer.extractSeason(file3)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(file3)).isNull()

        val file4 = File("~/Series-Shows/0-9/24.Complete.With.Subtitles/24 Season 5/24 - Day 5 - [5x18].mkv")
        assertThat(contentRenamer.extractName(file4)).isEqualTo("24")
        assertThat(contentRenamer.extractSeason(file4)).isEqualTo(5)
        assertThat(contentRenamer.extractEpisode(file4)).isEqualTo(18)

        val file5 = File("~/Series-Shows/0-9/24.Complete.With.Subtitles/24 Season 5/24 - Day 5 - [5x18].srt")
        assertThat(contentRenamer.extractName(file5)).isEqualTo("24")
        assertThat(contentRenamer.extractSeason(file5)).isEqualTo(5)
        assertThat(contentRenamer.extractEpisode(file5)).isEqualTo(18)
    }

    @Test
    fun testThreeDigitEpisodeDisambiguation() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        // Tier 1: Inside Season / Book folders, 101/204 resolves to S01E01 / S02E04
        val avatarFile = File("~/Series-Shows/A/Avatar - The Last Airbender [1080p]/Book 1; Water/101 - The Boy in the Iceberg.mp4")
        assertThat(contentRenamer.extractSeason(avatarFile)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(avatarFile)).isEqualTo(1)

        val duckmanFile = File("~/Series-Shows/D/Duckman/Season 1/Duckman 101 - I, Duckman.mkv")
        assertThat(contentRenamer.extractSeason(duckmanFile)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(duckmanFile)).isEqualTo(1)

        val duckmanS2 = File("~/Series-Shows/D/Duckman/Season 2/Duckman 203 - Cellar Beware.mkv")
        assertThat(contentRenamer.extractSeason(duckmanS2)).isEqualTo(2)
        assertThat(contentRenamer.extractEpisode(duckmanS2)).isEqualTo(3)

        // Tier 2: Zero-padded absolute episodes (001, 101) in anime / long-running shows
        val dbSuper001 = File("~/Series-Shows/D/Dragon Ball Super/Dragon Ball Super - Completo [WEB-DL - 1080p]/Dragon Ball Super - 001 [1080p].mkv")
        assertThat(contentRenamer.extractEpisode(dbSuper001)).isEqualTo(1)

        val dbSuper101 = File("~/Series-Shows/D/Dragon Ball Super/Dragon Ball Super - Completo [WEB-DL - 1080p]/Dragon Ball Super - 101 [1080p].mkv")
        assertThat(contentRenamer.extractEpisode(dbSuper101)).isEqualTo(101)

        // Tier 3: Metadata verification with optional seasonCount
        val seriesWithSeasonCount = MediaItem(title = "Test Show", type = boat.info.MediaType.Series).apply {
            seasonCount = 3
        }
        val standaloneShowFile = File("~/Series-Shows/T/Test Show/Test Show - 105.mkv")
        val episodeWithMeta = contentRenamer.extractEpisode(standaloneShowFile, seriesWithSeasonCount)
        assertThat(episodeWithMeta).isEqualTo(5)
    }

    @Test
    fun testWhiteCollarExtraction() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        val whiteCollarFile = File("~/Series-Shows/W/White.Collar.(2009)/Season 2 (AMZN WEB-DL)/White Collar (2009) - S02E05 - Unfinished Business (1080p AMZN WEB-DL x265 Silence).mkv")
        val name = contentRenamer.extractName(whiteCollarFile)
        val year = contentRenamer.extractYear(whiteCollarFile.path)
        val season = contentRenamer.extractSeason(whiteCollarFile)
        val episode = contentRenamer.extractEpisode(whiteCollarFile)

        assertThat(name).isEqualTo("White Collar")
        assertThat(year).isEqualTo(2009)
        assertThat(season).isEqualTo(2)
        assertThat(episode).isEqualTo(5)
    }

    @Test
    fun testAgentsOfShieldAndDotPatternsExtraction() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        val shieldFile1 = File("~/Series-Shows/A/Agents of S.H.I.E.L.D/Agents of S.H.I.E.L.D. - S06E11 - From the Ashes.mkv.mkv")
        val name1 = contentRenamer.extractName(shieldFile1)
        val season1 = contentRenamer.extractSeason(shieldFile1)
        val episode1 = contentRenamer.extractEpisode(shieldFile1)
        val year1 = contentRenamer.extractYear(shieldFile1.path)

        assertThat(name1).isEqualTo("Agents of SHIELD")
        assertThat(season1).isEqualTo(6)
        assertThat(episode1).isEqualTo(11)
        assertThat(year1).isNull()

        val shieldFile2 = File("~/Series-Shows/A/Agents of S.H.I.E.L.D/Marvels.Agents.Of.S.H.I.E.L.D.S06E01.1080p.AMZN.WEB-DL.DDP5.1.H.264-T6D.mkv")
        val name2 = contentRenamer.extractName(shieldFile2)
        val season2 = contentRenamer.extractSeason(shieldFile2)
        val episode2 = contentRenamer.extractEpisode(shieldFile2)

        assertThat(name2).isEqualTo("Marvels Agents Of SHIELD")
        assertThat(season2).isEqualTo(6)
        assertThat(episode2).isEqualTo(1)

        // Verify other dot patterns do not fail
        val dotPatternFile = File("~/Series-Shows/A/A.Beautiful.Mind.2001.1080p.mkv")
        val dotName = contentRenamer.extractName(dotPatternFile)
        assertThat(dotName).isEqualTo("A Beautiful Mind")
    }

    @Test
    fun testFailedExtractionCasesFromLog() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        // 1. Numeric show titles (The 100)
        val the100File1 = File("~/Series-Shows/0-9/The.100.S07/The.100.S07E01.720p.AMZN.WEBRip.x264-GalaxyTV.mkv")
        val the100Name1 = contentRenamer.extractName(the100File1)
        assertThat(the100Name1).isEqualTo("The 100")
        assertThat(contentRenamer.extractSeason(the100File1)).isEqualTo(7)
        assertThat(contentRenamer.extractEpisode(the100File1)).isEqualTo(1)

        val the100File2 = File("~/Series-Shows/0-9/The.100/Season 1/The.100.s01e01.LostFilm.avi")
        assertThat(contentRenamer.extractName(the100File2)).isEqualTo("The 100")

        // 2. Year handling (1000 Ways to Die / 1000-lb Sisters)
        val waysToDieFile = File("~/Series-Shows/0-9/1000 Ways to Die/Season 1/1000.Ways.to.Die.S01E01.Life Will Kill You.avi")
        assertThat(contentRenamer.extractName(waysToDieFile)).isEqualTo("1000 Ways to Die")
        assertThat(contentRenamer.extractYear(waysToDieFile.path)).isNull()
        assertThat(contentRenamer.extractSeason(waysToDieFile)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(waysToDieFile)).isEqualTo(1)

        val lbSistersFile = File("~/Series-Shows/0-9/1000.Lb.Sisters/1000-lb.Sisters S01E01.720p WEBRip x264-KOMPOST.mkv")
        assertThat(contentRenamer.extractName(lbSistersFile)).isEqualTo("1000-lb Sisters")
        assertThat(contentRenamer.extractYear(lbSistersFile.path)).isNull()

        // 3. Extras, Specials, Featurettes, and Bonus content
        val xFilesSpots = File("~/Series-Shows/X/The X-Files/Extras/Season 01 Extras/TV Spots/TV Spot (1).mp4")
        assertThat(contentRenamer.extractName(xFilesSpots)).isEqualTo("The X-Files")

        val officePsas = File("~/Series-Shows/O/The Office (US)/The Office (US) Season 1-9/Fake PSAs/The Office - Fake PSA 01.mkv")
        assertThat(contentRenamer.extractName(officePsas)).isEqualTo("The Office US")

        val shaunSheep = File("~/Series-Shows/S/Shaun.The.Sheep.(2007).Repack/Featurettes/The Farmers Llamas - Llamas shorts.mkv")
        assertThat(contentRenamer.extractName(shaunSheep)).isEqualTo("Shaun The Sheep")

        val angerManagement = File("~/Series-Shows/A/Anger.Management.(2011).2/Season 2/S02E36 - Charlie and the Grad Student.mp4")
        assertThat(contentRenamer.extractName(angerManagement)).isEqualTo("Anger Management")

        // season as year with mini myth specials, which must not overwrite the regular episodes
        val mythBusters = File("~/Series-Shows/M/Mythbusters 2003-2009/MythBusters 2003 (Disc 352p re-tvrip)/MythBusters 2003E01 Pilot 1 Jet-Assisted Chevy, Pop Rocks and Soda.mp4")
        assertThat(contentRenamer.extractName(mythBusters)).isEqualTo("Mythbusters")
        assertThat(contentRenamer.extractSeason(mythBusters)).isEqualTo(2003)
        assertThat(contentRenamer.extractEpisode(mythBusters)).isEqualTo(1)
        val mythBustersMiniMyth = File("~/Series-Shows/M/Mythbusters 2003-2009/MythBusters 2004 (Disc 352p re-tvrip)/MythBusters 2004X01 Egg-uinox MiniMyth.mp4")
        assertThat(contentRenamer.extractName(mythBustersMiniMyth)).isEqualTo("Mythbusters")
        assertThat(contentRenamer.extractSeason(mythBustersMiniMyth)).isEqualTo(2004)
        assertThat(contentRenamer.extractEpisode(mythBustersMiniMyth)).isNull()
        val mythBustersEpisodeMiniMyth = File("~/Series-Shows/M/Mythbusters 2003-2009/MythBusters 2004 (Disc 352p re-tvrip)/MythBusters 2004E06X01 Breakstep Bridge Revisit MiniMyth.mp4")
        assertThat(contentRenamer.extractSeason(mythBustersEpisodeMiniMyth)).isEqualTo(2004)
        assertThat(contentRenamer.extractEpisode(mythBustersEpisodeMiniMyth)).isNull()
        val mythBustersSpecial = File("~/Series-Shows/M/Mythbusters 2003-2009/MythBusters 2008 (Disc 352p re-tvrip)/MythBusters 2008X01 Shark Week Special 2.mp4")
        assertThat(contentRenamer.extractEpisode(mythBustersSpecial)).isNull()

        // season and episode glued to the title, confirmed by the season folder
        val shameless = File("~/Series-Shows/S/Shameless.US.Season.S09.x265.405p-we/Shameless914 Found.mkv")
        assertThat(contentRenamer.extractName(shameless)).isEqualTo("Shameless")
        assertThat(contentRenamer.extractSeason(shameless)).isEqualTo(9)
        assertThat(contentRenamer.extractEpisode(shameless)).isEqualTo(14)

        assertThat(contentRenamer.extractName(File("~/Series-Shows/L/Lost/Lost The Complete Series Season 1,2,3,4,5,6 + English Subs/Bonus Features/Season 1/Lost Revealed/Bloopers From The Set.mkv"))).isEqualTo("Lost")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/T/Twin.Peaks.(1990)/Featurettes/Season 1/The Log Lady/The Log Lady 01.mkv"))).isEqualTo("Twin Peaks")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/K/Key.And.Peele/Key.And.Peele.S01.COMPLETE.BluRay.x264.AAC.2.0-LeRalouf [UNCENSORED]/subtitles/english_SDH/Key.And.Peele.S01E01.Series.Premiere.BluRay.x264.AAC.2.0-LeRalouf.srt"))).isEqualTo("Key And Peele")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/J/Jessica.Jones.3.(2019)/Jessica Jones - S03E01 - A.K.A The Perfect Burger.mkv"))).isEqualTo("Jessica Jones")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/T/Takeshis.Castle.1/[K.o.R] Takeshis Castle - 101 [h264][AAC][2698BC02].mkv"))).isEqualTo("Takeshis Castle")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/H/Harley.Quinn.(2019-2020).1.S01.And.Birds.Of.Prey.Movie.Web264/Season 1 (2019-20)/Harley Quinn - S01 E01 - Til Death Do Us Part (1080p - DCU Web-DL).mp4"))).isEqualTo("Harley Quinn")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/T/The Thick of It/The Thick Of It - Series 3/3x01.mp4"))).isEqualTo("The Thick Of It")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/F/Full Metal Panic/Full Metal Panic/Full Metal Panic 10 Run, Running,Ran.mkv"))).isEqualTo("Full Metal Panic")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/N/No_Game_No_Life_English_Dub_720p_1-12_Complete/No Game No Life 10 Blue Rose.mp4"))).isEqualTo("No Game No Life")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/S/Serial_Experiments_Lain_(1520x1080_Blu-Ray_FLAC)/[Coalgirls]_Serial_Experiments_Lain_10_(1520x1080_Blu-Ray_FLAC)_[C1160A23].mkv"))).isEqualTo("Serial Experiments Lain")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/C/Crest of the Stars (Seikai no Monshou) (1999) [kuchikirukia]/01. Crest of the Stars (Seikai no Monshou) [DVD 480p Hi10P AAC AC3 dual-audio][kuchikirukia].mkv"))).isEqualTo("Crest of the Stars Seikai no Monshou")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/G/Gigantor.(us.Dub)/Gigantor (US Dub)/Season 1/1-01 Gigantor (US) S01E01 Struggle at the South Pole.m4v"))).isEqualTo("Gigantor")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/E/Eden of the East/Eden_of_The_East_01-11_(x264,720p,ger.sub)/Eden of the East - 01 - I've Found Myself a Prince [gg-thora].mkv"))).isEqualTo("Eden of the East")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/N/Nikita.(2010-2013).(seasons.1,2,3,4.S0104).And.Movies/2b. Season 2 (2011-12)/NIKITA - S02 E01 - Game Change (720p BluRay).mp4"))).isEqualTo("Nikita")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/E/The.Event.S01/teneighty-the.event.s01e01.mkv"))).isEqualTo("The Event")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/M/Money Heist/Money Heist Season 2 Complete English HD 720p/(PlayHD.ooo) Money Heist S02E01 English HD 720p x264 AAC.mp4"))).isEqualTo("Money Heist")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/H/The Hitchhikers Guide To The Galaxy - Remastered Mini Series 1981 1080p [H264-mp4]/Subtitles - [SRT-VobSub-XML]/The Hitchhikers Guide To The Galaxy - Mini-Series 1981 Episode 1 English.srt"))).isEqualTo("The Hitchhikers Guide To The Galaxy")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/S/Space Battleship Yamato 2199/Space Battleship Yamato 2199 NCOP01.mkv"))).startsWith("Space Battleship Yamato 2199")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/S/Star Trek/Star Trek Deep Space Nine - Complete Series - 576P - Multi Subs - DVDrip - X265-HEVC - O69/Season 1/Star Trek Deep Space Nine - Season 1 - DVD Extras/Deep Space Nine Sketchbook.mkv"))).doesNotContain("Sketchbook")

        val bcsStoryboard = File("~/Series-Shows/B/Better.Call.Saul.(2015)/Featurettes/Season 4/Storyboard Comparison/Piñata.mkv")
        assertThat(contentRenamer.extractName(bcsStoryboard)).isEqualTo("Better Call Saul")

        val bsgRecap = File("~/Series-Shows/B/Battlestar Galactica/Battlestar Galactica 720p Complete 2003-2012 Extras Subs/TV Recap Specials/BSG - Catch The Frak Up.mkv")
        assertThat(contentRenamer.extractName(bsgRecap)).isEqualTo("Battlestar Galactica")

        val rickMortyExtras = File("~/Series-Shows/R/Rick and Morty/Rick and Morty [UNCENSORED] [1080p] [5.1 MP3] [x265][FINAL]/Rick and Morty (Season 01) [UNCENSORED] [1080p] [5.1 MP3] [x265]/Extras/Behind The Scenes/Behind the Scenes [720p] [x265].mp4")
        assertThat(contentRenamer.extractName(rickMortyExtras)).isEqualTo("Rick and Morty")

        // 4. Subfolder / Story / Chapter / Course structure
        val docWho1 = File("~/Series-Shows/D/Doctor.Who.(1963).Classic/Story 001 - An Unearthly Child/S01E01 - An Unearthly Child.mkv")
        val docWho1Name = contentRenamer.extractName(docWho1)
        assertThat(docWho1Name).isEqualTo("Doctor Who Classic")
        assertThat(contentRenamer.extractSeason(docWho1)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(docWho1)).isEqualTo(1)

        val docWho2 = File("~/Series-Shows/D/Doctor.Who.1963.4/Story 028 - The Smugglers/S04E01 - The Smugglers, Episode One.mp4")
        assertThat(contentRenamer.extractName(docWho2)).isEqualTo("Doctor Who")
        assertThat(contentRenamer.extractSeason(docWho2)).isEqualTo(4)
        assertThat(contentRenamer.extractEpisode(docWho2)).isEqualTo(1)

        val capFuture = File("~/Series-Shows/C/Captain Future.S01.German.1978.ANIME.FS.DVDRiP.XViD/New/E01.avi")
        assertThat(contentRenamer.extractName(capFuture)).isEqualTo("Captain Future")
        assertThat(contentRenamer.extractSeason(capFuture)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(capFuture)).isEqualTo(1)

        val babylon5 = File("~/Series-Shows/B/Babylon 5/Temporada 1 - Signos y presagios/1x01 - Medianoche en la línea de fuego.avi")
        assertThat(contentRenamer.extractName(babylon5)).isEqualTo("Babylon 5")
        assertThat(contentRenamer.extractSeason(babylon5)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(babylon5)).isEqualTo(1)

        val b99 = File("~/Series-Shows/B/Brooklyn Nine Nine/Brooklyn.NIne-Nine.Season.3/Ep-01.New.Captain.mp4")
        assertThat(contentRenamer.extractName(b99)).isEqualTo("Brooklyn Nine Nine")
        assertThat(contentRenamer.extractSeason(b99)).isEqualTo(3)
        assertThat(contentRenamer.extractEpisode(b99)).isEqualTo(1)

        // 5. Codec, Release, Quality, and Language tags
        val appleseed = File("~/Series-Shows/A/Appleseed XIII/Appleseed XIII - Ep. 01 - The Nemean Lion (720p - DUAL Audio).mkv")
        assertThat(contentRenamer.extractName(appleseed)).isEqualTo("Appleseed XIII")
        assertThat(contentRenamer.extractEpisode(appleseed)).isEqualTo(1)

        val chainsaw = File("~/Series-Shows/C/Chainsaw Man (01-12) (1080p)/[SubsPlease] Chainsaw Man - 01v2 (1080p) [8C038972].mkv")
        assertThat(contentRenamer.extractName(chainsaw)).isEqualTo("Chainsaw Man")
        assertThat(contentRenamer.extractEpisode(chainsaw)).isEqualTo(1)

        val tbbt = File("~/Series-Shows/B/The Big Bang Theory/The Big Bang Theory (German)/Big Bang Theory S05/S05E01.avi")
        assertThat(contentRenamer.extractName(tbbt)).isEqualTo("The Big Bang Theory")
        assertThat(contentRenamer.extractSeason(tbbt)).isEqualTo(5)
        assertThat(contentRenamer.extractEpisode(tbbt)).isEqualTo(1)

        val pridePrejudice = File("~/Series-Shows/P/Pride and Prejudice (BBC 1995) Jane Austen 1080p H.264 10GB (moviesbyrizzo) subs/Subtitles folder/Danish/Pride and Prejudice (BBC -1995) E1 1080p H.264 (moviesbyrizzo).srt")
        assertThat(contentRenamer.extractName(pridePrejudice)).startsWith("Pride and Prejudice")
        assertThat(contentRenamer.extractEpisode(pridePrejudice)).isEqualTo(1)

        val dotHack = File("~/Series-Shows/#/_HACK/004_TRIOLOGY/[GAX]dot_hack-G.U._Trilogy_-_Movie(ger.sub)(h264)[525944DF].mkv")
        val dotHackName = contentRenamer.extractName(dotHack)
        assertThat(dotHackName.startsWith("dot hack-GU Trilogy") || dotHackName.startsWith("dot hack GU Trilogy") || dotHackName.contains("Trilogy")).isTrue()

        val hackGift = File("~/Series-Shows/#/.Hack Sign 1-28+Extras/(B-A)hack_GIFT_(1BB88E0D).mkv")
        assertThat(contentRenamer.extractName(hackGift)).isEqualTo("hack GIFT")

        val shopForKillers = File("~/Series-Shows/S/A.Shop.For.Killers.Korean.S01.2024.Hevc33/A.Shop.for.Killers.KOREAN.S01E01.2024.1080p.WEBRip.x265.HEVC-kkolev33.mkv")
        assertThat(contentRenamer.extractName(shopForKillers)).isEqualTo("A Shop For Killers")
        assertThat(contentRenamer.extractSeason(shopForKillers)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(shopForKillers)).isEqualTo(1)

        // 6. Acronyms & Shortened Names
        val iasip = File("~/Series-Shows/I/Its.Always.Sunny.In.Philadelphia.S0114.(2005-)/IASIP S01 (360p re-dvdrip)/IASIP S01E01 The Gang Gets Racist.mp4")
        assertThat(contentRenamer.extractName(iasip)).isEqualTo("Its Always Sunny In Philadelphia")
        assertThat(contentRenamer.extractSeason(iasip)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(iasip)).isEqualTo(1)

        val batmanTas = File("~/Series-Shows/B/BATMAN The Animated Series (1992-1999) - COMPLETE Season 1-6, TV S01-S06 and 2 Movies - 1080p BluRay x264/Season 2 (1993-94)/Batman T.A.S - S02 E01 - Shadow of The Bat, Part 1 (1080p - BluRay).mp4")
        assertThat(contentRenamer.extractName(batmanTas) == "Batman TAS" || contentRenamer.extractName(batmanTas) == "BATMAN The Animated Series").isTrue()
        assertThat(contentRenamer.extractSeason(batmanTas)).isEqualTo(2)
        assertThat(contentRenamer.extractEpisode(batmanTas)).isEqualTo(1)

        val stargate = File("~/Series-Shows/S/Stargate SG1 Ultimate Fan Collection/Season 1/StargateSG1S01E01.mp4")
        assertThat(contentRenamer.extractName(stargate) == "Stargate SG-1" || contentRenamer.extractName(stargate) == "Stargate SG1").isTrue()
        assertThat(contentRenamer.extractSeason(stargate)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(stargate)).isEqualTo(1)

        // 7. Unicode and Hyphenated prefixes
        val timeJam = File("~/Series-Shows/T/Time Jam：Valerian and Laureline/Time Jam：Valerian and Laureline - 01 Time Matters [8bit][480p][5A6E2794].mkv")
        assertThat(contentRenamer.extractName(timeJam)).isEqualTo("Time Jam Valerian and Laureline")
        assertThat(contentRenamer.extractEpisode(timeJam)).isEqualTo(1)

        val catfish = File("~/Series-Shows/C/Catfish Docu and TV Series (2010-2016) [About Social Media Web Liars+Scammers)/Season 4 (2015) (324p)/Catfish-The.TV.Show.S04E00_SP02 Season Special Nev and Max's Craziest Momenrts.mp4")
        assertThat(contentRenamer.extractName(catfish) == "Catfish-The TV Show" || contentRenamer.extractName(catfish) == "Catfish The TV Show").isTrue()
        assertThat(contentRenamer.extractSeason(catfish)).isEqualTo(4)
        assertThat(contentRenamer.extractEpisode(catfish)).isEqualTo(0)
    }

    @Test
    fun testPopeyePackExtraction() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        val galleryFile = File("~/Series-Shows/P/Popeye.The.Sailor.1933-1943.COMPLETE.DVDRip.x264-panos[rartv]/Gallery.mkv")
        assertThat(contentRenamer.extractName(galleryFile)).isEqualTo("Popeye The Sailor")
        assertThat(contentRenamer.extractSeason(galleryFile)).isNull()
        assertThat(contentRenamer.extractEpisode(galleryFile)).isNull()

        val timelineFile = File("~/Series-Shows/P/Popeye.The.Sailor.1933-1943.COMPLETE.DVDRip.x264-panos[rartv]/1900-1920.mkv")
        assertThat(contentRenamer.extractName(timelineFile)).isEqualTo("Popeye The Sailor")

        val hospitalikyFile = File("~/Series-Shows/P/Popeye.The.Sailor.1933-1943.COMPLETE.DVDRip.x264-panos[rartv]/1937e04-Hospitaliky.mkv")
        assertThat(contentRenamer.extractName(hospitalikyFile)).isEqualTo("Popeye The Sailor")
        assertThat(contentRenamer.extractSeason(hospitalikyFile)).isEqualTo(1937)
        assertThat(contentRenamer.extractEpisode(hospitalikyFile)).isEqualTo(4)

        val goonlandFile = File("~/Series-Shows/P/Popeye.The.Sailor.1933-1943.COMPLETE.DVDRip.x264-panos[rartv]/1938e10-Goonland.mkv")
        assertThat(contentRenamer.extractName(goonlandFile)).isEqualTo("Popeye The Sailor")
        assertThat(contentRenamer.extractSeason(goonlandFile)).isEqualTo(1938)
        assertThat(contentRenamer.extractEpisode(goonlandFile)).isEqualTo(10)

        val actFile = File("~/Series-Shows/P/Popeye.The.Sailor.1933-1943.COMPLETE.DVDRip.x264-panos[rartv]/Act.mkv")
        assertThat(contentRenamer.extractName(actFile)).isEqualTo("Popeye The Sailor")

        val finichFile = File("~/Series-Shows/P/Popeye.The.Sailor.1933-1943.COMPLETE.DVDRip.x264-panos[rartv]/Finich.mkv")
        assertThat(contentRenamer.extractName(finichFile)).isEqualTo("Popeye The Sailor")

        val elephinksFile = File("~/Series-Shows/P/Popeye.The.Sailor.1933-1943.COMPLETE.DVDRip.x264-panos[rartv]/Elephinks.mkv")
        assertThat(contentRenamer.extractName(elephinksFile)).isEqualTo("Popeye The Sailor")
    }

    @Test
    fun testOutOfBoundsFilenameEpisodeMatch() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        val file = File("~/Series-Shows/S/SampleShow/[Group_Long_Tag_12345678901234567890] SampleShow - 05.mkv")
        val episode = contentRenamer.extractEpisode(file)
        assertThat(episode).isEqualTo(5)
    }

    @Test
    fun testMST3KExtraction() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        val file = File("~/Series-Shows/M/MST3K/Season 3/MST3K_0308_Gamera_vs_Gaos_xvid.avi")
        val name = contentRenamer.extractName(file)
        val season = contentRenamer.extractSeason(file)
        val episode = contentRenamer.extractEpisode(file)

        assertThat(name).isEqualTo("MST3K")
        assertThat(season).isEqualTo(3)
        assertThat(episode).isEqualTo(8)
    }

    @Test
    fun testDougExtractionAndYear() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        val file = File("~/Series-Shows/D/DOUG (1991-1999) - Complete Animated TV Series, S01-S07 and Movie - 480p x264/Season 7 (1998-99)/DOUG - S07 E25 - Quailman vs. the Triad of Terror (480p - DSNP Web-DL).srt")
        val name = contentRenamer.extractName(file)
        val year = contentRenamer.extractYear(file.absolutePath)
        val season = contentRenamer.extractSeason(file)
        val episode = contentRenamer.extractEpisode(file)

        assertThat(name).isEqualTo("DOUG")
        assertThat(year).isEqualTo(1991)
        assertThat(season).isEqualTo(7)
        assertThat(episode).isEqualTo(25)
    }

    @Test
    fun testCOPSExtraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)

        val file = File("~/Series-Shows/C/C.O.P.S.(1988-1989).,/C.O.P.S. - Ep. 02 - The Case of the Crime Circus (480p - DVDRip).mp4")
        val name = contentRenamer.extractName(file)
        val year = contentRenamer.extractYear(file.absolutePath)
        val episode = contentRenamer.extractEpisode(file)

        assertThat(name).isEqualTo("COPS")
        assertThat(year).isEqualTo(1988)
        assertThat(episode).isEqualTo(2)

        val searchResult = mediaProxyService.search(name, year ?: 0)
        assertThat(searchResult).isNotEmpty()
        assertThat(searchResult.first().title == "C.O.P.S." || searchResult.first().title == "Cops").isTrue()
        assertThat(searchResult.first().title).isNotEqualTo("Katts and Dog")
    }

    @Test
    fun testRamsaysKitchenNightmaresExtraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)

        val file = File("~/Series-Shows/R/Ramsay\\s.Kitchen.Nightmares.(all.Seasons.+.Extras)/UK Season 05 [2007]/05x07 - Curry Lounge.avi")
        val name = contentRenamer.extractName(file)
        val year = contentRenamer.extractYear(file.path)
        val season = contentRenamer.extractSeason(file)
        val episode = contentRenamer.extractEpisode(file)

        assertThat(name == "Ramsays Kitchen Nightmares" || name == "Ramsay's Kitchen Nightmares").isTrue()
        assertThat(name).doesNotContain("\\")
        assertThat(name.lowercase()).doesNotContain("all seasons")
        assertThat(name).doesNotEndWith(" UK")
        assertThat(year).isEqualTo(2007)
        assertThat(season).isEqualTo(5)
        assertThat(episode).isEqualTo(7)

        val searchResult = mediaProxyService.search(name, year ?: 0)
        assertThat(searchResult).isNotEmpty()
        assertThat(searchResult.first().title).containsIgnoringCase("Kitchen Nightmares")
    }

    @Test
    fun testLupinIIIExtraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)

        val file = File("~/Series-Shows/L/Lupin III -  Full Collection 2018/Movies, Specials & OVA/(1969) [Movie] Pilot Film/Lupin III Pilot Film 1 - TV [480p][neo1024][03AE79BB].mkv")
        val name = contentRenamer.extractName(file)
        val year = contentRenamer.extractYear(file.path)
        val season = contentRenamer.extractSeason(file)
        val episode = contentRenamer.extractEpisode(file)

        assertThat(name).isEqualTo("Lupin III Pilot Film")
        assertThat(name).doesNotContainIgnoringCase("Full Collection")
        assertThat(name).doesNotContainIgnoringCase("Movies, Specials")
        assertThat(name).doesNotContainIgnoringCase("OVA")
        assertThat(year).isEqualTo(1969)

        val searchResult = mediaProxyService.search(name, year ?: 0)
        assertThat(searchResult).isNotEmpty()
        assertThat(searchResult.first().title.contains("Lupin", ignoreCase = true) && searchResult.first().title.contains("Pilot", ignoreCase = true)).isTrue()
    }

    @Test
    fun testMoeshaExtraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)

        val file = File("~/Series-Shows/M/Moesha.(1996-2001).Tv.Series.Season.S0106:/Season 3 (1997-98)/MOESHA - S03 E06 - The Play Scene (480p - NF Web-DL).mp4")
        val name = contentRenamer.extractName(file)
        val year = contentRenamer.extractYear(file.path)
        val season = contentRenamer.extractSeason(file)
        val episode = contentRenamer.extractEpisode(file)

        System.out.println("[DEBUG_LOG] Moesha -> Name: '$name', Year: $year, Season: $season, Episode: $episode")

        assertThat(name == "Moesha" || name == "MOESHA").isTrue()
        assertThat(year).isEqualTo(1996)
        assertThat(season).isEqualTo(3)
        assertThat(episode).isEqualTo(6)

        val searchResult = mediaProxyService.search(name, year ?: 0)
        System.out.println("[DEBUG_LOG] Moesha searchResult: $searchResult")
        assertThat(searchResult).isNotEmpty()
        assertThat(searchResult.first().title).isEqualToIgnoringCase("Moesha")
    }

    @Test
    fun testGolgo13Extraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)

        val file = File("~/Series-Shows/G/Golgo.13/[subs4u]_Golgo_13_20_(Xvid,ger.sub)[9378A5C4].avi")
        val name = contentRenamer.extractName(file)
        val year = contentRenamer.extractYear(file.path)
        val season = contentRenamer.extractSeason(file)
        val episode = contentRenamer.extractEpisode(file)

        System.out.println("[DEBUG_LOG] Golgo 13 -> Name: '$name', Year: $year, Season: $season, Episode: $episode")

        assertThat(name).isEqualTo("Golgo 13")
        assertThat(episode).isEqualTo(20)

        val searchResult = mediaProxyService.search(name, year ?: 0)
        System.out.println("[DEBUG_LOG] Golgo 13 searchResult: $searchResult")
        assertThat(searchResult).isNotEmpty()
        assertThat(searchResult.first().title).containsIgnoringCase("Golgo 13")
    }

    @Test
    fun testSpace1999Extraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)

        val fileWithParentheses = File("~/Series-Shows/S/Space 1999 (1975)/Season 1/Space 1999 - S01E01 - Breakaway.mkv")
        val nameWithParen = contentRenamer.extractName(fileWithParentheses)
        val yearWithParen = contentRenamer.extractYear(fileWithParentheses.absolutePath)
        assertThat(nameWithParen).isEqualTo("Space 1999")
        assertThat(yearWithParen).isEqualTo(1975)

        val searchResultParen = mediaProxyService.search(nameWithParen, yearWithParen ?: 0, MediaType.Series)
        assertThat(searchResultParen).isNotEmpty()
        assertThat(searchResultParen.first().title.contains("Space: 1999", ignoreCase = true) || searchResultParen.first().title.contains("Space 1999", ignoreCase = true)).isTrue()

        val fileCastInterview = File("~/Series-Shows/S/Space 1999 (Complete)/# Cast Interviews/Space 1999 - Interviews ~ Catherine Schell (Maya).mpg")
        val nameInterview = contentRenamer.extractName(fileCastInterview)
        val yearInterview = contentRenamer.extractYear(fileCastInterview.absolutePath)
        val searchResultInterview = mediaProxyService.search("Space 1999", MediaType.Series)
        assertThat(searchResultInterview).isNotEmpty()
        assertThat(searchResultInterview.first().type).isEqualTo(MediaType.Series)
        assertThat(searchResultInterview.first().title.contains("Space: 1999", ignoreCase = true) || searchResultInterview.first().title.contains("Space 1999", ignoreCase = true)).isTrue()
    }

    @Test
    fun test2001SpaceOdysseyExtraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)

        val file = File("~/Movies/2/2001 A Space Odyssey (1968)/2001 A Space Odyssey (1968).1080p.mkv")
        val name = contentRenamer.extractName(file)
        val year = contentRenamer.extractYear(file.absolutePath)

        assertThat(name.contains("2001 A Space Odyssey") || name.contains("2001: A Space Odyssey") || name == "2001 A Space Odyssey").isTrue()
        assertThat(year).isEqualTo(1968)

        val searchResult = mediaProxyService.search(name, year ?: 0)
        assertThat(searchResult).isNotEmpty()
        assertThat(searchResult.first().title.contains("2001: A Space Odyssey") || searchResult.first().title.contains("2001 A Space Odyssey")).isTrue()
    }

    @Test
    fun test2012MovieExtraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)

        val file = File("~/Movies/2/2012 (2009)/2012 (2009).1080p.mkv")
        val name = contentRenamer.extractName(file)
        val year = contentRenamer.extractYear(file.absolutePath)

        assertThat(name).isEqualTo("2012")
        assertThat(year).isEqualTo(2009)

        val searchResult = mediaProxyService.search(name, year ?: 0)
        assertThat(searchResult).isNotEmpty()
        assertThat(searchResult.first().title).isEqualTo("2012")
    }

    @Test
    fun test1917MovieExtraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)

        val file = File("~/Movies/1/1917 (2019)/1917 (2019).1080p.mkv")
        val name = contentRenamer.extractName(file)
        val year = contentRenamer.extractYear(file.absolutePath)

        assertThat(name).isEqualTo("1917")
        assertThat(year).isEqualTo(2019)

        val searchResult = mediaProxyService.search(name, year ?: 0)
        assertThat(searchResult).isNotEmpty()
        assertThat(searchResult.first().title).isEqualTo("1917")
    }

    @Test
    fun testClassOf1999Extraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)

        val file = File("~/Movies/C/Class of 1999 (1990)/Class of 1999 (1990).1080p.mkv")
        val name = contentRenamer.extractName(file)
        val year = contentRenamer.extractYear(file.absolutePath)

        assertThat(name).isEqualTo("Class of 1999")
        assertThat(year).isEqualTo(1990)

        val searchResult = mediaProxyService.search(name, year ?: 0)
        assertThat(searchResult).isNotEmpty()
        assertThat(searchResult.first().title).containsIgnoringCase("Class of 1999")
    }

    @Test
    fun testMASHExtraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)

        val file = File("~/Series-Shows/M/M.A.S.H_1970-1982.dvdrip_[teko]/Season_01/s01e03_Requiem.For.A.Lightweight.avi")
        val name = contentRenamer.extractName(file)
        val year = contentRenamer.extractYear(file.absolutePath)
        val season = contentRenamer.extractSeason(file)
        val episode = contentRenamer.extractEpisode(file)

        assertThat(name).isEqualTo("MASH")
        assertThat(year).isEqualTo(1970)
        assertThat(season).isEqualTo(1)
        assertThat(episode).isEqualTo(3)

        val searchResult = mediaProxyService.search(name, year ?: 0, MediaType.Series)
        assertThat(searchResult).isNotEmpty()
        assertThat(searchResult.first().title).isEqualTo("M*A*S*H")
        assertThat(searchResult.first().type).isEqualTo(MediaType.Series)
    }

    @Test
    fun testCowboyExtraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)
        val file = File("~/Series-Shows/C/Cowboy Bebop 01-26 (1080p Blu-ray 8bit AC3 MP4)/[NoobSubs] Cowboy Bebop 19 (1080p Blu-ray 8bit AC3).mp4")
        val name = contentRenamer.extractName(file)
        val episode = contentRenamer.extractEpisode(file)

        assertThat(name).isEqualTo("Cowboy Bebop")
        assertThat(episode).isEqualTo(19)

        val searchResult = mediaProxyService.search("Cowboy Bebop", MediaType.Series)
        assertThat(searchResult).isNotEmpty()
        assertThat(searchResult.first().title).isEqualTo("Cowboy Bebop")
        assertThat(searchResult.first().type).isEqualTo(MediaType.Series)
    }

    @Test
    fun testFOTBExtraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)
        val file = File("~/Series-Shows/F/Fresh.Off.The.Boat.S0106.(2015-)/Fresh off the Boat S02 (360p re-webrip)/Fresh off the Boat 02E14 Michael Chang Fever.mp4")
        val name = contentRenamer.extractName(file)
        val season = contentRenamer.extractSeason(file)
        val episode = contentRenamer.extractEpisode(file)

        assertThat(name).isEqualTo("Fresh Off The Boat")
        assertThat(episode).isEqualTo(14)
        assertThat(season).isEqualTo(2)
    }

    @Test
    fun testNoiseAfterTitleExtraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)
        val file = File("~/Series-Shows/A/Arrow/S3/arrow.301.hdtv-lol.mp4")
        val name = contentRenamer.extractName(file)
        val season = contentRenamer.extractSeason(file)
        val episode = contentRenamer.extractEpisode(file)

        assertThat(name).isEqualTo("Arrow")
        assertThat(episode).isEqualTo(1)
        assertThat(season).isEqualTo(3)
    }

    @Test
    fun shouldExtractSomething() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)
        val file = File("~/Series-Shows/L/Lessons.In.Chemistry.(2023)/Lessons in Chemistry (2023) - S01E06 - Poirot (1080p ATVP WEB-DL x265 Silence).mkv")
        val name = contentRenamer.extractName(file)
        val season = contentRenamer.extractSeason(file)
        val episode = contentRenamer.extractEpisode(file)

        assertThat(name).isEqualTo("Lessons In Chemistry")
        assertThat(season).isEqualTo(1)
        assertThat(episode).isEqualTo(6)
    }

}
