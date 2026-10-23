package boat
import org.assertj.core.api.Assertions.assertThat

import boat.info.MediaItem
import boat.info.MediaProxyService
import boat.info.MediaType
import boat.info.TheMovieDataBaseService
import boat.info.TheTVDBService
import boat.utilities.HttpHelper
import org.junit.jupiter.api.Tag
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
            "~/data/TV/The.Mutobitizep.S01E01.1080p.mkv",
            "~/data/TV/Samoduru.Tururo.S03.2019/Samoduru.Tururo.S03E05.mkv",
            "~/data/Movies/Ivakisaka.2010.720p.mkv",
            "~/data/TV/The.Bufo.S02E08.mkv",
            "~/data/Movies/Icibokamelid.2014.1080p.mkv",
            "~/data/TV/Dobi.S01.GERMAN.1080p/Dobi.S01E01.mkv",
            "~/data/TV/Cegogubup.S01.1080p/Cegogubup.E01.mkv",
            "~/Series-Shows/0-9/1000 Wgar to Die/Season 1/1000.Wgar.to.Die.S01E01.Life Will Kill You.avi",
            "~/Series-Shows/0-9/1000.Lb.Simolub/1000-lb.Simolub S01E01.720p WEBRip x264-KAVULOF.mkv",
            "~/Series-Shows/B/BOGABO The Atavibik Sasase (1992-1999) - COMPLETE Season 1-6, TV S01-S06 and 2 Movies - 1080p BluRay x264/Season 1 (1992-93)/Bogabo T.A.S - S01 E01 - The Cez and The Cedi, Part 1 (1080p - BluRay).mp4",
            "~/Series-Shows/S/A.Sufa.For.Kodabem.Korean.S01.2024.Hevc33/A.Sufa.for.Kodabem.KOREAN.S01E01.2024.1080p.WEBRip.x265.HEVC-kkolev33.mkv",
            "~/Series-Shows/S/Sonosatame.(2012)/Season 1/Sonosatame (2012) - S01E01 - Episode 1 (1080p WEB-DL x265 r00t).mkv",
            "~/Series-Shows/M/La Meilleure Version de Moi-même/La.Meilleure.Version.De.Moi-Meme.S01E01.FRENCH.WEB-DL.XviD-ZT.avi",
            "~/Series-Shows/M/Magnet:?xt=urn:btih:52e24d7fe3557a389121c7d89d0e039122028d31&/The.Redini.S04E01.Life.and.Death.1080p.10bit.AMZN.WEB-DL.DDP5.1.HEVC-Vocicec.mkv",
            "~/Series-Shows/#/.Hack Legend Of The Twilight/[Exiled-Destiny]_Hack_Legend_Of_The_Twilight_Ep01_(1E372EE5).mkv",
            "~/Series-Shows/#/.Hack Sign 1-28+Extras/[V-A]_hack_SIGN_-_01_[BB605406].mkv",
            "~/Series-Shows/#/_HACK/ROOTS/hack_roots-24.avi",
            "~/Series-Shows/0-9/071c.Wnat.The.Bap.Bozif/071c-Suzo.Wnat.The.Bap.Bozif.S01E11.1080p.WEB.H264-EXPLOIT[ettv].torrent.mkv",
            "~/Series-Shows/0-9/24.Complete.With.Subtitles/24 Season 2/24 - [2x01] - Day 2  8 00 A.M. - 9 00 A.M.mkv",
            "~/Series-Shows/0-9/24.Complete.With.Subtitles/24 Season 2/24 - [2x03] - Day 2  10 00 A.M. - 11 00 A.M.srt",
            "~/Series-Shows/S/Suzo.Tabe.Danutikep.S04E01.1080p.web.x264-vxt.mkv",
            "~/Series-Shows/A/ASUG FABE (1991-1995) - Complete Animated TV Series and 2005 Movie - 720p x264/Asug Fabe - complete animated series/asug fabe dvd 1.mkv",
            "~/Series-Shows/#/.Hack Legend Of The Twilight/[Exiled-Destiny]_Hack_Legend_Of_The_Twilight_Ep01_(1E372EE5).mkv",
            "~/Series-Shows/#/.Hack Sign 1-28+Extras/[V-A]_hack_SIGN_-_01_[BB605406].mkv",
            "~/Series-Shows/#/_HACK/ROOTS/hack_roots-24.avi",
            "~/Series-Shows/#/_HACK/Legend_o_T/hack-Legend of the Twilight 01 - The Legendary Hero.avi",
            "~/Series-Shows/C/Ceti.Fik.With.Me.S01/teneighty-cfwm.s01e01.mkv",
            "~/Series-Shows/D/Demadi Bemu Super/Demadi Bemu Super - Completo [WEB-DL - 1080p]/Demadi%20Bemu%20Super%20-%20001%20%5B1080p%5D.mkv",
            "~/Series-Shows/0-9/071c.Wnat.The.Bap.Bozif/071c-Suzo.Wnat.The.Bap.Bozif.S01E11.1080p.WEB.H264-EXPLOIT[ettv].torrent.mkv",
            "~/Series-Shows/0-9/90.Dar.Fafelu.Happily.Ever.After.No.Teporun.Bazo/www.Torrenting.com - 90.Dar.Fafelu.Happily.Ever.After.S03E08.No.Teporun.Bazo.XviD-AFG.avi",
            "~/Series-Shows/A/Amafitugi Tizo/Season 1/01.01 - Selapun Pekaf Pisal.mp4",
            "~/Series-Shows/A/Arapef - The Lepu Akuvacebu [1080p]/Book 1; Water/101 - The Bon in the Isatuno.mp4",
            "~/Series-Shows/D/Dunapir/Season 1/Dunapir 101 - I, Dunapir.mkv",
            "~/Series-Shows/D/Demadi Bemu Super/Demadi Bemu Super - Completo [WEB-DL - 1080p]/Demadi%20Bemu%20Super%20-%20101%20%5B1080p%5D.mkv",
            "~/Series-Shows/Z/Zidimafili%20Saze%20S02%201080p%20BDRip%2010%20bits%20DD%20x265-EMBER.torrent/S02E12-The Gorakuto SAZE in Hbisuga [F9FE3939].mkv",
            "~/Series-Shows/R/Robologidob Dabe/Robologidob.Dabe.S01.1080p.WEBRip.x265-KUVEGINI/Subs/Robologidob.Dabe.S01E07.1080p.WEBRip.x265-KUVEGINI/6_spa.srt",
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
                assertThat(name).isEqualTo("Ceti Fik With Me")
            }
            if (fullName.contains("Demadi%20Bemu%20Super")) {
                assertThat(name).isEqualTo("Demadi Bemu Super")
            }
            if (fullName.contains("071c-Suzo.Wnat.The.Bap.Bozif")) {
                assertThat(name).isEqualTo("Suzo Wnat The Bap Bozif")
            }
            if (fullName.contains("www.Torrenting.com")) {
                assertThat(name).isEqualTo("90 Dar Fafelu Happily Ever After")
            }
            if (fullName.contains("Amafitugi Tizo")) {
                assertThat(name).isEqualTo("Amafitugi Tizo")
            }
            if (fullName.contains("Arapef - The Lepu Akuvacebu")) {
                assertThat(name).isEqualTo("Arapef - The Lepu Akuvacebu")
            }
            if (fullName.contains("Dunapir")) {
                assertThat(name).isEqualTo("Dunapir")
                assertThat(season).isEqualTo(1)
                assertThat(episode).isEqualTo(1)
            }
            if (fullName.contains("101 - The Bon in the Isatuno.mp4")) {
                val ep = contentRenamer.extractEpisode(file)
                val se = contentRenamer.extractSeason(file)
                assertThat(se).isEqualTo(1)
                assertThat(ep).isEqualTo(1)
            }
            if (fullName.contains("Demadi Bemu Super - 101 [1080p].mkv")) {
                val ep = contentRenamer.extractEpisode(file)
                assertThat(ep).isEqualTo(101)
            }
            if (fullName.contains("Robologidob Dabe")) {
                assertThat(name).isEqualTo("Robologidob Dabe")
                assertThat(season).isEqualTo(1)
                assertThat(episode).isEqualTo(7)
            }
            if (fullName.contains("Zidimafili%20Saze")) {
                assertThat(name).isEqualTo("Zidimafili Saze")
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
        assertThat(contentRenamer.isJunk("Dobi")).isFalse()
        assertThat(contentRenamer.isJunk("Lecu")).isFalse()
        assertThat(contentRenamer.isJunk("The Mutobitizep")).isFalse()
        assertThat(contentRenamer.isJunk("Ivakisaka")).isFalse()
        assertThat(contentRenamer.isJunk("Cegogubup")).isFalse()
        assertThat(contentRenamer.isJunk("Suzo Wnat The Bap Bozif")).isFalse()
        assertThat(contentRenamer.isJunk("Arapef - The Lepu Akuvacebu")).isFalse()
        assertThat(contentRenamer.isJunk("Robologidob Dabe")).isFalse()
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
        val angelStunts = File("~/Series-Shows/A/Adera Complete Series Season 1,2,3,4,5 + Extras, Subs/Extras/Season 2/Featurettes/Stunts.mkv")
        assertThat(contentRenamer.extractName(angelStunts)).startsWith("Adera")

        val stargateTrailer = File("~/Series-Shows/S/Safepicu SG1 Ultimate Fan Collection/Season 1/Extras/Season 2 Trailer.mp4")
        assertThat(contentRenamer.extractName(stargateTrailer)).startsWith("Safepicu SG1")

        val stargateFeaturette = File("~/Series-Shows/S/Safepicu SG1 Ultimate Fan Collection/Season 1/Extras/CostumeDesignFeaturette.mp4")
        assertThat(contentRenamer.extractName(stargateFeaturette)).startsWith("Safepicu SG1")

        val twentyFourExtra = File("~/Series-Shows/0-9/24.Complete.With.Subtitles/24 Season 1/24 EXTRAS/Alternate Ending.mp4")
        assertThat(contentRenamer.extractName(twentyFourExtra)).isEqualTo("24")
        assertThat(contentRenamer.extractSeason(twentyFourExtra)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(twentyFourExtra)).isNull()

        val twentyFourInterview = File("~/Series-Shows/0-9/24.Complete.With.Subtitles/24 Season 1/24 EXTRAS/Kuseba Sosukotuka on season 1 of 24.mp4")
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

        assertThat(contentRenamer.getSafeTitle(MediaItem("Suzo Wnat: The Bap Bozif"))).isEqualTo("Suzo Wnat The Bap Bozif")
        assertThat(contentRenamer.getSafeTitle(MediaItem("Face/Off"))).isEqualTo("FaceOff")
        assertThat(contentRenamer.getSafeTitle(MediaItem("What If...?"))).isEqualTo("What If...")
        assertThat(contentRenamer.getSafeTitle(MediaItem("Back\\Slash"))).isEqualTo("BackSlash")
        assertThat(contentRenamer.getSafeTitle(MediaItem("Title*With\"Dangerous<Chars>And|Pipes"))).isEqualTo("TitleWithDangerousCharsAndPipes")
        assertThat(contentRenamer.getSafeTitle(MediaItem("Opave's Etifan"))).isEqualTo("Opaves Etifan")
        assertThat(contentRenamer.getSafeTitle(MediaItem("1000-lb Simolub (2020)"))).isEqualTo("1000-lb Simolub (2020)")
        assertThat(contentRenamer.getSafeTitle("Simple Title")).isEqualTo("Simple Title")
        assertThat(contentRenamer.getSafeTitle("This//Hack")).isEqualTo("This Hack")
    }

    @Test
    fun testExtractSeasonAndEpisode() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )
        val seriesItem = MediaItem(title = "Bacozami Bap", type = boat.info.MediaType.Series, id = "{tvdb-81189}")
        seriesItem.seasonCount = 5
        val movieItem = MediaItem(title = "Ivakisaka", year = 2010, type = boat.info.MediaType.Movie, id = "{tmdb-27205}")

        assertThat(contentRenamer.extractSeasonAndEpisode(File("Bacozami.Bap.S02E03.mkv"), seriesItem)).isEqualTo(Pair(2, 3))
        // episode without season defaults to season 1
        assertThat(contentRenamer.extractSeasonAndEpisode(File("Bacozami Bap - 05.mkv"), seriesItem)).isEqualTo(Pair(1, 5))
        // absolute numbering within the season count keeps its season
        assertThat(contentRenamer.extractSeasonAndEpisode(File("Bacozami Bap - 305.mkv"), seriesItem)).isEqualTo(Pair(3, 5))
        assertThat(contentRenamer.extractSeasonAndEpisode(File("Bacozami Bap - 105.mkv"), seriesItem)).isEqualTo(Pair(1, 5))
        // beyond the season count it stays an absolute episode
        assertThat(contentRenamer.extractSeasonAndEpisode(File("Bacozami Bap - 905.mkv"), seriesItem)).isEqualTo(Pair(1, 905))
        // movies have no season/episode
        assertThat(contentRenamer.extractSeasonAndEpisode(File("Ivakisaka.2010.E01.mkv"), movieItem)).isEqualTo(Pair(null, null))
    }

    @Test
    fun shouldKeepOnlyTheExtensionOfFileNamesThatRepeatTheExtension() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )
        val seriesItem = MediaItem(title = "The Mutobitizep", type = boat.info.MediaType.Series, id = "12345")

        val target = contentRenamer.getTargetFile(File("mkv.mkv"), seriesItem, "The Mutobitizep", 1, 1)

        assertThat(target).endsWith("The Mutobitizep.S01E01.mkv")
    }

    @Test
    fun testGetTargetFile() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )
        val seriesItem = MediaItem(title = "The Mutobitizep", type = boat.info.MediaType.Series, id = "12345")

        // Season 1, Episode 1 -> S01E01
        val target1 = contentRenamer.getTargetFile(File("mutobitizep.mkv"), seriesItem, "The Mutobitizep", 1, 1)
        assertThat(target1).contains("/Season 1/The Mutobitizep.S01E01.mkv")

        // Season 1, Episode 101 -> S01E101
        val target2 = contentRenamer.getTargetFile(File("mutobitizep.mkv"), seriesItem, "The Mutobitizep", 1, 101)
        assertThat(target2).contains("/Season 1/The Mutobitizep.S01E101.mkv")

        // Season null, Episode 5 -> S01E05
        val target3 = contentRenamer.getTargetFile(File("mutobitizep.mkv"), seriesItem, "The Mutobitizep", null, 5)
        assertThat(target3).contains("/The Mutobitizep.S01E05.mkv")

        // Season 10, Episode 24 -> S10E24
        val target4 = contentRenamer.getTargetFile(File("mutobitizep.mkv"), seriesItem, "The Mutobitizep", 10, 24)
        assertThat(target4).contains("/Season 10/The Mutobitizep.S10E24.mkv")

        // Movie (no season, no episode)
        val movieItem = MediaItem(title = "Ivakisaka", type = boat.info.MediaType.Movie, id = "67890")
        val targetMovie = contentRenamer.getTargetFile(File("ivakisaka.mkv"), movieItem, "Ivakisaka", null, null)
        assertThat(targetMovie).endsWith("/Movies/I/Ivakisaka 67890.mkv")

        // Movie with year, resolution and codec
        val inceptionItem = MediaItem(title = "Ivakisaka", year = 2010, type = boat.info.MediaType.Movie, id = "{tmdb-27205}")
        val movieFolder = File("Ivakisaka.2010.1080p.BluRay.x264-SPARKS")
        assertThat(contentRenamer.getTargetFile(File(movieFolder, "sparks-ivakisaka.mkv"), inceptionItem, "Ivakisaka", null, null))
            .endsWith("/Movies/I/Ivakisaka (2010) {tmdb-27205}.1080p.x264.mkv")

        // Movie subtitles with language code follow the movie name
        assertThat(contentRenamer.getTargetFile(File(movieFolder, "Ivakisaka.2010.1080p.en.srt"), inceptionItem, "Ivakisaka", null, null))
            .endsWith("/Movies/I/Ivakisaka (2010) {tmdb-27205}.1080p.x264.en.srt")
        assertThat(contentRenamer.getTargetFile(File(movieFolder, "Ivakisaka.ger.forced.srt"), inceptionItem, "Ivakisaka", null, null))
            .endsWith("/Movies/I/Ivakisaka (2010) {tmdb-27205}.1080p.x264.ger.forced.srt")

        // Movie subtitles without language code and extras keep their name, so they don't overwrite the movie
        assertThat(contentRenamer.getTargetFile(File(File(movieFolder, "Subs"), "2_English.srt"), inceptionItem, "Ivakisaka", null, null))
            .endsWith("/Movies/I/2_English.srt")
        assertThat(contentRenamer.getTargetFile(File(File(movieFolder, "Sample"), "sparks-ivakisaka-sample.mkv"), inceptionItem, "Ivakisaka", null, null))
            .endsWith("/Movies/I/sparks-ivakisaka-sample.mkv")
        assertThat(contentRenamer.getTargetFile(File(File(movieFolder, "Featurettes"), "Dreams.mkv"), inceptionItem, "Ivakisaka", null, null))
            .endsWith("/Movies/I/Dreams.mkv")

        // Multi part movies get a part suffix
        assertThat(contentRenamer.getTargetFile(File(movieFolder, "ivakisaka.cd2.mkv"), inceptionItem, "Ivakisaka", null, null))
            .endsWith("/Movies/I/Ivakisaka (2010) {tmdb-27205}.1080p.x264 - pt2.mkv")

        // Movies ignore season/episode
        assertThat(contentRenamer.getTargetFile(File(movieFolder, "sparks-ivakisaka.mkv"), inceptionItem, "Ivakisaka", 1, 5))
            .endsWith("/Movies/I/Ivakisaka (2010) {tmdb-27205}.1080p.x264.mkv")

        // 9-1-1 Series with resolution and codec
        val nineOneOneItem = MediaItem(title = "9-1-1", type = boat.info.MediaType.Series, id = "{tvdb-334586}")
        val targetNineOneOne = contentRenamer.getTargetFile(File("9-1-1.S05E06.1080p.WEB.H264-CAKES[TGx].mkv"), nineOneOneItem, "9-1-1", 5, 6)
        assertThat(targetNineOneOne).endsWith("/Series-Shows/0-9/9-1-1 {tvdb-334586}/Season 5/9-1-1.S05E06.1080p.h264.mkv")

        // Bucin Series with resolution and codec
        val buffyItem = MediaItem(title = "Bucin the Vampire Satebi", type = boat.info.MediaType.Series, id = "{tvdb-70327}")
        val targetBuffy = contentRenamer.getTargetFile(
            File("Bucin.The.Vampire.Satebi.S08E07.No.Fipusu.For.Ygu.(2).MOTION.COMIC.720p.WEB-DL.AC3-5.1.x264-DEKEZEL.mkv"),
            buffyItem,
            "Bucin The Vampire Satebi",
            8,
            7
        )
        assertThat(targetBuffy).endsWith("/Series-Shows/B/Bucin the Vampire Satebi {tvdb-70327}/Season 8/Bucin The Vampire Satebi.S08E07.720p.x264.mkv")

        // The Bok Boto Topumu Series (Folder should be B not T)
        val tbbtItem = MediaItem(title = "The Bok Boto Topumu", type = boat.info.MediaType.Series, id = "{tvdb-80379}")
        val targetTbbt = contentRenamer.getTargetFile(File("The Bok Boto Topumu S02E08.mkv"), tbbtItem, "The Bok Boto Topumu", 2, 8)
        assertThat(targetTbbt).endsWith("/Series-Shows/B/The Bok Boto Topumu {tvdb-80379}/Season 2/The Bok Boto Topumu.S02E08.mkv")

        // .hack//SIGN series, without the leading dot of a hidden folder
        val hackSignItem = MediaItem(title = ".hack//SIGN", type = boat.info.MediaType.Series, id = "{tmdb-333800}")
        val targetHackSign = contentRenamer.getTargetFile(File("HS22.avi"), hackSignItem, "SIGN", 1, 22)
        assertThat(targetHackSign).endsWith("/Series-Shows/H/hack SIGN {tmdb-333800}/Season 1/SIGN.S01E22.avi")

        // Extra with season but no episode
        val extraItem = MediaItem(title = "24", type = boat.info.MediaType.Series, id = "{tvdb-1234}")
        val targetExtra = contentRenamer.getTargetFile(File("Alternate Ending.mp4"), extraItem, "24", 1, null)
        assertThat(targetExtra).endsWith("/Series-Shows/0-9/24 {tvdb-1234}/Season 1/Alternate Ending.mp4")

        // Extra with series media type but no season and no episode (keep original file name in series folder)
        val hackSignExtra = File("~/Series-Shows/#/.Hack Sign 1-28+Extras/[V-A]_hack_SIGN_-_Textless_Ending_[934E6984].mkv")
        val targetHackSignExtra = contentRenamer.getTargetFile(hackSignExtra, hackSignItem, "Hack Sign", null, null)
        assertThat(targetHackSignExtra).endsWith("/Series-Shows/H/hack SIGN {tmdb-333800}/[V-A]_hack_SIGN_-_Textless_Ending_[934E6984].mkv")
    }

    @Test
    fun testBugsBunnyAndBlacklistExtraction() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        // Befa Bolif file
        val bugsBunnyFile = File("~/Series-Shows/B/Befa Bolif Pack (1938-2004)/Befa Bolif - (Ep. 23) - Super-Rabbit.avi")
        val bugsName = contentRenamer.extractName(bugsBunnyFile)
        val bugsEpisode = contentRenamer.extractEpisode(bugsBunnyFile)
        assertThat(bugsName).isEqualTo("Befa Bolif")
        assertThat(bugsEpisode).isEqualTo(23)

        // The Bicobufaf file
        val blacklistFile = File("~/Series-Shows/B/The Bicobufaf/Season 1/The Bicobufaf Episode 14 - Medavogo Parov.mp4")
        val blacklistName = contentRenamer.extractName(blacklistFile)
        val blacklistEpisode = contentRenamer.extractEpisode(blacklistFile)
        val blacklistSeason = contentRenamer.extractSeason(blacklistFile)
        assertThat(blacklistName).isEqualTo("The Bicobufaf")
        assertThat(blacklistSeason).isEqualTo(1)
        assertThat(blacklistEpisode).isEqualTo(14)

        // Bozek Bemone file
        val blackButlerFile = File("~/Series-Shows/B/Bozek Bemone/Bozek Bemone Season 2 [1080p]/Bozek Bemone 02.mkv")
        val blackButlerName = contentRenamer.extractName(blackButlerFile)
        val blackButlerEpisode = contentRenamer.extractEpisode(blackButlerFile)
        val blackButlerSeason = contentRenamer.extractSeason(blackButlerFile)
        assertThat(blackButlerName).isEqualTo("Bozek Bemone")
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

        // /Series-Shows/S/Suzo Tabe/Danutikep/Season 1/01.mkv -> "Suzo Tabe Danutikep"
        val starTrekFile = File("~/Series-Shows/S/Suzo Tabe/Danutikep/Season 1/01.mkv")
        val starTrekName = contentRenamer.extractName(starTrekFile)
        println("[DEBUG_LOG] starTrekName: '$starTrekName'")
        assertThat(starTrekName).isEqualToIgnoringCase("Suzo Tabe Danutikep")

        // Boundary check: single-letter folder 'S' and category 'Series-Shows' are not included
        val boundaryFile = File("~/Series-Shows/S/Sonosatame/Season 1/01.mkv")
        val boundaryName = contentRenamer.extractName(boundaryFile)
        assertThat(boundaryName).isEqualToIgnoringCase("Sonosatame")
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
        val avatarFile = File("~/Series-Shows/A/Arapef - The Lepu Akuvacebu [1080p]/Book 1; Water/101 - The Bon in the Isatuno.mp4")
        assertThat(contentRenamer.extractSeason(avatarFile)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(avatarFile)).isEqualTo(1)

        val duckmanFile = File("~/Series-Shows/D/Dunapir/Season 1/Dunapir 101 - I, Dunapir.mkv")
        assertThat(contentRenamer.extractSeason(duckmanFile)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(duckmanFile)).isEqualTo(1)

        val duckmanS2 = File("~/Series-Shows/D/Dunapir/Season 2/Dunapir 203 - Cicuka Bidoci.mkv")
        assertThat(contentRenamer.extractSeason(duckmanS2)).isEqualTo(2)
        assertThat(contentRenamer.extractEpisode(duckmanS2)).isEqualTo(3)

        // Tier 2: Zero-padded absolute episodes (001, 101) in anime / long-running shows
        val dbSuper001 = File("~/Series-Shows/D/Demadi Bemu Super/Demadi Bemu Super - Completo [WEB-DL - 1080p]/Demadi Bemu Super - 001 [1080p].mkv")
        assertThat(contentRenamer.extractEpisode(dbSuper001)).isEqualTo(1)

        val dbSuper101 = File("~/Series-Shows/D/Demadi Bemu Super/Demadi Bemu Super - Completo [WEB-DL - 1080p]/Demadi Bemu Super - 101 [1080p].mkv")
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

        val whiteCollarFile = File("~/Series-Shows/W/Wmake.Cimaku.(2009)/Season 2 (AMZN WEB-DL)/Wmake Cimaku (2009) - S02E05 - Unfinished Business (1080p AMZN WEB-DL x265 Silence).mkv")
        val name = contentRenamer.extractName(whiteCollarFile)
        val year = contentRenamer.extractYear(whiteCollarFile.path)
        val season = contentRenamer.extractSeason(whiteCollarFile)
        val episode = contentRenamer.extractEpisode(whiteCollarFile)

        assertThat(name).isEqualTo("Wmake Cimaku")
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

        val shieldFile1 = File("~/Series-Shows/A/Agevez of S.A.L.A.L.I/Agevez of S.A.L.A.L.I. - S06E11 - From the Asiba.mkv.mkv")
        val name1 = contentRenamer.extractName(shieldFile1)
        val season1 = contentRenamer.extractSeason(shieldFile1)
        val episode1 = contentRenamer.extractEpisode(shieldFile1)
        val year1 = contentRenamer.extractYear(shieldFile1.path)

        assertThat(name1).isEqualTo("Agevez of SALALI")
        assertThat(season1).isEqualTo(6)
        assertThat(episode1).isEqualTo(11)
        assertThat(year1).isNull()

        val shieldFile2 = File("~/Series-Shows/A/Agevez of S.A.L.A.L.I/Mipanet.Agevez.Of.S.A.L.A.L.I.S06E01.1080p.AMZN.WEB-DL.DDP5.1.H.264-T6D.mkv")
        val name2 = contentRenamer.extractName(shieldFile2)
        val season2 = contentRenamer.extractSeason(shieldFile2)
        val episode2 = contentRenamer.extractEpisode(shieldFile2)

        assertThat(name2).isEqualTo("Mipanet Agevez Of SALALI")
        assertThat(season2).isEqualTo(6)
        assertThat(episode2).isEqualTo(1)

        // Verify other dot patterns do not fail
        val dotPatternFile = File("~/Series-Shows/A/A.Bociborop.Mifi.2001.1080p.mkv")
        val dotName = contentRenamer.extractName(dotPatternFile)
        assertThat(dotName).isEqualTo("A Bociborop Mifi")
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

        // 2. Year handling (1000 Wgar to Die / 1000-lb Simolub)
        val waysToDieFile = File("~/Series-Shows/0-9/1000 Wgar to Die/Season 1/1000.Wgar.to.Die.S01E01.Life Will Kill You.avi")
        assertThat(contentRenamer.extractName(waysToDieFile)).isEqualTo("1000 Wgar to Die")
        assertThat(contentRenamer.extractYear(waysToDieFile.path)).isNull()
        assertThat(contentRenamer.extractSeason(waysToDieFile)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(waysToDieFile)).isEqualTo(1)

        val lbSistersFile = File("~/Series-Shows/0-9/1000.Lb.Simolub/1000-lb.Simolub S01E01.720p WEBRip x264-KAVULOF.mkv")
        assertThat(contentRenamer.extractName(lbSistersFile)).isEqualTo("1000-lb Simolub")
        assertThat(contentRenamer.extractYear(lbSistersFile.path)).isNull()

        // 3. Extras, Specials, Featurettes, and Bonus content
        val xFilesSpots = File("~/Series-Shows/X/The X-Favuc/Extras/Season 01 Extras/TV Spots/TV Spot (1).mp4")
        assertThat(contentRenamer.extractName(xFilesSpots)).isEqualTo("The X-Favuc")

        val officePsas = File("~/Series-Shows/O/The Osugol (US)/The Osugol (US) Season 1-9/Fake PSAs/The Osugol - Fake PSA 01.mkv")
        assertThat(contentRenamer.extractName(officePsas)).isEqualTo("The Osugol US")

        val shaunSheep = File("~/Series-Shows/S/Siran.The.Saniz.(2007).Repack/Featurettes/The Fedoris Ladopa - Llamas shorts.mkv")
        assertThat(contentRenamer.extractName(shaunSheep)).isEqualTo("Siran The Saniz")

        val angerManagement = File("~/Series-Shows/A/Asubu.Masokagupi.(2011).2/Season 2/S02E36 - Cotepul and the Gevi Sakekip.mp4")
        assertThat(contentRenamer.extractName(angerManagement)).isEqualTo("Asubu Masokagupi")

        // the title ends at a bracketed episode
        val house = File("~/Series-Shows/H/Hgosa MD/Hgosa MD Season 2/Hgosa - [2x01] - Acceptance.mkv")
        assertThat(contentRenamer.extractName(house)).isEqualTo("Hgosa")
        assertThat(contentRenamer.extractSeason(house)).isEqualTo(2)
        assertThat(contentRenamer.extractEpisode(house)).isEqualTo(1)
        assertThat(contentRenamer.extractName(File("~/Series-Shows/S/Sefanuma/Sefanuma Season 19 - Complete/The Sefanuma [19x02] The Hkicu Of Seborip.avi.avi"))).isEqualTo("The Sefanuma")
        val dragonBallGt = File("~/Series-Shows/D/Demadibemu GT/Season 1/Demadi Bemu GT [1.21] A Sucedu Rologiba.mp4")
        assertThat(contentRenamer.extractName(dragonBallGt)).isEqualTo("Demadi Bemu GT")
        assertThat(contentRenamer.extractEpisode(dragonBallGt)).isEqualTo(21)
        // site prefixes, German episodes, double dashed episodes and the show after the episode marker
        assertThat(contentRenamer.extractName(File("~/Series-Shows/W/Www.Torrenting.Org.90.Dar.Fafelu.Before.The.90.Days/www.Torrenting.org       90 Dar Fafelu Before the 90 Days S03E06 iNTERNAL 1080p WEB x26.mkv")))
            .isEqualTo("90 Dar Fafelu Before the 90 Days")
        val captainFuture = File("~/Series-Shows/C/Cutecip Fipusu.S01.German.1978.ANIME.FS.DVDRiP.XViD/Old/Cutecip.Fipusu.Folge.13-19.DVD-Rip.Xvid.by.Fotav.TMF.avi")
        assertThat(contentRenamer.extractName(captainFuture)).isEqualTo("Cutecip Fipusu")
        assertThat(contentRenamer.extractEpisode(captainFuture)).isNull()
        val hak5 = File("~/Series-Shows/H/HAK 5 all Seasons (Youtube Channel Clone) 1080p/hak5--1718--free-virtualization-clusters-and-breadboards--hd720p30.h264.mp4")
        assertThat(contentRenamer.extractName(hak5)).isEqualTo("hak5")
        assertThat(contentRenamer.extractSeason(hak5)).isEqualTo(17)
        assertThat(contentRenamer.extractEpisode(hak5)).isEqualTo(18)
        assertThat(contentRenamer.extractName(File("~/Series-Shows/H/HAK 5 all Seasons (Youtube Channel Clone) 1080p/S18E11-HAK5-Open Source Distributed Decentralized Social Networks.mp4"))).isEqualTo("HAK5")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/E/Ecuf of the Edaz/Ecuf of the Edaz (Hleluro no Eden) + Movies [BD-1080p]/[bonkai77].Ecuf.of.the.Edaz.Episode.10.[BD.1080p.Dual-Audio.x265.HEVC.10bit].mkv"))).isEqualTo("Ecuf of the Edaz")
        // audio channels aren't episodes
        val tonupo = File("~/Series-Shows/W/Www.Uindex.Org.-201904.Eac3.2.0/Tonupo FD-2019-S04 WEBRip EAC3 2 0 1080p x265-iVy.mkv")
        assertThat(contentRenamer.extractName(tonupo)).isEqualTo("Tonupo FD")
        assertThat(contentRenamer.extractEpisode(tonupo)).isNull()
        // resolutions of any height, with a frame rate, as k or as width and height
        fun resolution(name: String) = contentRenamer.extractResolution(File("~/Series-Shows/S/Show/$name"))
        assertThat(resolution("Show.S01E01.1080p.mkv")).isEqualTo("1080p")
        assertThat(resolution("Bavodadiv (2005 360p re-dvdrip).mp4")).isEqualTo("360p")
        assertThat(resolution("T.G.Bakupos.Sinita.Bov-S04E00-468p.mp4")).isEqualTo("468p")
        assertThat(resolution("hak5--1718--free-virtualization-clusters--hd720p30.h264.mp4")).isEqualTo("720p")
        assertThat(resolution("Show.S01E01.1440p.mkv")).isEqualTo("1440p")
        assertThat(resolution("Show.S01E01.1080i.ts")).isEqualTo("1080i")
        assertThat(resolution("The Book of Bezu Fase.S01E01.4k.hevc.mkv")).isEqualTo("4k")
        assertThat(resolution("BONOLET! ED (BD 1920x1080 x264 AAC).mp4")).isEqualTo("1080p")
        assertThat(resolution("Sapidaf X - The Movie [1280x960].mkv")).isEqualTo("960p")
        assertThat(resolution("Show.S01E01.x264.mkv")).isNull()
        // the bracket wins over numbers in the episode's title
        val sefanuma = File("~/Series-Shows/S/Sefanuma/Sefanuma Season 20- Complete/The Sefanuma [20.19] Wvukapu Hloma 9-0-2-1-Des.avi")
        assertThat(contentRenamer.extractSeason(sefanuma)).isEqualTo(20)
        assertThat(contentRenamer.extractEpisode(sefanuma)).isEqualTo(19)
        val houseUpperCase = File("~/Series-Shows/H/Hgosa MD/Hgosa MD Season 8/Hgosa - [8X01] -  Twenty Vicodin.avi")
        assertThat(contentRenamer.extractName(houseUpperCase)).isEqualTo("Hgosa")
        assertThat(contentRenamer.extractEpisode(houseUpperCase)).isEqualTo(1)

        // openings, endings and OVAs go to the extras without season and episode, their numbering would collide with each other
        val hunterNcop = File("~/Series-Shows/H/Hzised.X.Hzised.(1999).(/[Judas] Hzised x Hzised (1999) Openings & Endings/[Judas] Hzised x Hzised (1999) - NCOP 01.mkv")
        assertThat(contentRenamer.extractName(hunterNcop)).isEqualTo("Hzised x Hzised")
        assertThat(contentRenamer.extractSeason(hunterNcop)).isNull()
        assertThat(contentRenamer.extractEpisode(hunterNcop)).isNull()
        val dragonBallOpening = File("~/Series-Shows/D/Demadi Bemu Kai (Z)/Demadi.Bemu.KAI.-.OP1v2.1080p.BluRay.x264.DHD.mkv")
        assertThat(contentRenamer.extractName(dragonBallOpening)).isEqualTo("Demadi Bemu KAI")
        assertThat(contentRenamer.extractSeason(dragonBallOpening)).isNull()
        assertThat(contentRenamer.extractEpisode(dragonBallOpening)).isNull()
        assertThat(contentRenamer.extractName(File("~/Series-Shows/B/BONOLET!  1-16 [Dual Audio ](BD 1080p 1920x1080 x264 AAC)/BONOLET! ?ED (BD 1920x1080 x264 AAC).mp4"))).isEqualTo("BONOLET")
        val onePunchManOva = File("~/Series-Shows/O/Oli Poden Muf/Oli Poden Muf S1+OVA [BD 1080p Hi10 FLAC]/Oli Poden Muf OVA 01 1080p.mkv")
        assertThat(contentRenamer.extractName(onePunchManOva)).isEqualTo("Oli Poden Muf")
        assertThat(contentRenamer.extractSeason(onePunchManOva)).isNull()
        assertThat(contentRenamer.extractEpisode(onePunchManOva)).isNull()
        val onePunchMan = MediaItem("Oli-Poden Muf", year = 2015, type = MediaType.Series, id = "{tvdb-293088}")
        assertThat(contentRenamer.getTargetFile(onePunchManOva, onePunchMan, "Oli Poden Muf", null, null, "/PFDB"))
            .isEqualTo("/PFDB/Series-Shows/O/Oli-Poden Muf {tvdb-293088}/Other/Oli Poden Muf OVA 01 1080p.mkv")
        assertThat(contentRenamer.getTargetFile(File("~/Series-Shows/O/Oli-Poden Muf {tvdb-293088}/Season 1/[Judas] Oli Poden Muf - S01 NCOP.mkv"), onePunchMan, "Oli Poden Muf", 1, null, "/PFDB"))
            .isEqualTo("/PFDB/Series-Shows/O/Oli-Poden Muf {tvdb-293088}/Other/[Judas] Oli Poden Muf - S01 NCOP.mkv")
        assertThat(contentRenamer.extractSeason(File("~/Series-Shows/O/Oli-Poden Muf {tvdb-293088}/Season 1/[Judas] Oli Poden Muf - S01OVA03.mkv"))).isNull()
        // the regular episodes next to them, OP and ED are words in other titles
        assertThat(contentRenamer.extractEpisode(File("~/Series-Shows/O/Oli Poden Muf/Oli Poden Muf S1+OVA [BD 1080p Hi10 FLAC]/Oli Poden Muf 01 1080p.mkv"))).isEqualTo(1)
        assertThat(contentRenamer.extractSeason(File("~/Series-Shows/E/Ed Eti n Enav/ED.ETI.N.ENAV.S01E02.mkv"))).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(File("~/Series-Shows/T/Tarikoza.Castle.2/[K.o.R] Tarikoza Castle - 202 [h264][AAC][ED8CC609].mkv"))).isNotNull()

        // letter O instead of the zero in the season, the episode must not come from "AC3 2.0" or "No.64"
        val bicobufaf = File("~/Series-Shows/B/The Bicobufaf/[FileTracker.pl]The Bicobufaf S01-S06[wilu75]/S1/The.Bicobufaf.SO1E13.The.Cocudi.Arakim.No.64.PL.480p.WEB-DL.AC3.2.0.XviD-Ralf.DeiX.avi")
        assertThat(contentRenamer.extractName(bicobufaf)).isEqualTo("The Bicobufaf")
        assertThat(contentRenamer.extractSeason(bicobufaf)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(bicobufaf)).isEqualTo(13)

        assertThat(contentRenamer.extractName(File("~/Series-Shows/C/Coniz-22.(tv.Mini.Series.2019).Web265.Bone/Coniz-22 2019 S01E01 720p WEB-DL HEVC x265 BONE.mkv"))).isEqualTo("Coniz-22")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/B/Bbc.Pipadu.Eseve.(2006).Se/Featurettes/Desert Lions.mkv"))).isEqualTo("Bbc Pipadu Eseve")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/L/Lopog ISI -  Full Collection 2018/Series/(1971) Lopog ISI Part I/Lopog ISI Series 1 - 01 - Is Lopog Burning [NyardReencodes][720p][v2][80494603]_new.mkv"))).isEqualTo("Lopog ISI")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/A/Atel.88/(1985) Atel 88/[Mo7tas] Atel 88 - S00E01 [Remastered 1080p] [Kaito Fansub].mkv"))).isEqualTo("Atel 88")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/S/The Sukukid - Stephen King Horror Mini-Series 1997 Eng Subs [H264-mp4]/Subtitles [SubRip-MicroDVD-IDX]/01 The Sukukid - Part 1 Stephen King Mini-Series 1997 English.srt"))).isEqualTo("The Sukukid")
        // a resolution isn't a year
        val ruvem = "~/Series-Shows/R/Ruvem/Ruvem 1-2 BD-BOX - TV + SP/EXTRA/[Moozzi2] Ruvem ? [SP00] Menu - 01 [ BOX-01 ] (BD 1920x1080 x.265).mkv"
        assertThat(contentRenamer.extractName(File(ruvem))).isEqualTo("Ruvem")
        assertThat(contentRenamer.extractYear(ruvem)).isNull()

        // release tags in the middle of the folder name, subtitles in language folders
        val prideAndPrejudice = "~/Series-Shows/P/Pumek and Pipirebak (BBC 1995) Jane Austen 1080p H.264 10GB (moviesbyrizzo) subs "
        assertThat(contentRenamer.extractName(File("$prideAndPrejudice/Pumek and Pipirebak (BBC -1995) E1 1080p H.264 (moviesbyrizzo).mp4"))).isEqualTo("Pumek and Pipirebak")
        assertThat(contentRenamer.extractName(File("$prideAndPrejudice/Subtitles folder/Danish/Pumek and Pipirebak (BBC -1995) E1 1080p H.264 (moviesbyrizzo).srt"))).isEqualTo("Pumek and Pipirebak")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/A/Atupirupi/Atupirupi Part 1 2014 720p HDTV AAC x264-PSYPHER.mkv"))).isEqualTo("Atupirupi")

        // season as year with mini myth specials, which must not overwrite the regular episodes
        val mevoSebudik = File("~/Series-Shows/M/Mevosebudik 2003-2009/MevoSebudik 2003 (Disc 352p re-tvrip)/MevoSebudik 2003E01 Pilot 1 Jse-Azukotag Cucar, Pek Rumus and Sebe.mp4")
        assertThat(contentRenamer.extractName(mevoSebudik)).isEqualTo("Mevosebudik")
        assertThat(contentRenamer.extractSeason(mevoSebudik)).isEqualTo(2003)
        assertThat(contentRenamer.extractEpisode(mevoSebudik)).isEqualTo(1)
        val mythBustersMiniMyth = File("~/Series-Shows/M/Mevosebudik 2003-2009/MevoSebudik 2004 (Disc 352p re-tvrip)/MevoSebudik 2004X01 Egg-uinox MiniMyth.mp4")
        assertThat(contentRenamer.extractName(mythBustersMiniMyth)).isEqualTo("Mevosebudik")
        assertThat(contentRenamer.extractSeason(mythBustersMiniMyth)).isEqualTo(2004)
        assertThat(contentRenamer.extractEpisode(mythBustersMiniMyth)).isNull()
        val mythBustersEpisodeMiniMyth = File("~/Series-Shows/M/Mevosebudik 2003-2009/MevoSebudik 2004 (Disc 352p re-tvrip)/MevoSebudik 2004E06X01 Breakstep Bridge Revisit MiniMyth.mp4")
        assertThat(contentRenamer.extractSeason(mythBustersEpisodeMiniMyth)).isEqualTo(2004)
        assertThat(contentRenamer.extractEpisode(mythBustersEpisodeMiniMyth)).isNull()
        val mythBustersSpecial = File("~/Series-Shows/M/Mevosebudik 2003-2009/MevoSebudik 2008 (Disc 352p re-tvrip)/MevoSebudik 2008X01 Solal Wrod Sinekuf 2.mp4")
        assertThat(contentRenamer.extractEpisode(mythBustersSpecial)).isNull()

        // season and episode glued to the title, confirmed by the season folder
        val sirovipob = File("~/Series-Shows/S/Sirovipob.US.Season.S09.x265.405p-we/Sirovipob914 Found.mkv")
        assertThat(contentRenamer.extractName(sirovipob)).isEqualTo("Sirovipob")
        assertThat(contentRenamer.extractSeason(sirovipob)).isEqualTo(9)
        assertThat(contentRenamer.extractEpisode(sirovipob)).isEqualTo(14)

        assertThat(contentRenamer.extractName(File("~/Series-Shows/L/Lecu/Lecu The Complete Series Season 1,2,3,4,5,6 + English Subs/Bekem Favobevo/Season 1/Lecu Revealed/Bloopers From The Set.mkv"))).isEqualTo("Lecu")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/T/Turo.Pimes.(1990)/Featurettes/Season 1/The Lif Luza/The Lif Luza 01.mkv"))).isEqualTo("Turo Pimes")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/K/Kug.And.Pepov/Kug.And.Pepov.S01.COMPLETE.BluRay.x264.AAC.2.0-LuCakoga [UNCENSORED]/subtitles/english_SDH/Kug.And.Pepov.S01E01.Series.Premiere.BluRay.x264.AAC.2.0-LuCakoga.srt"))).isEqualTo("Kug And Pepov")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/J/Jlusesi.Jcika.3.(2019)/Jlusesi Jcika - S03E01 - A.K.A The Pevadok Babapo.mkv"))).isEqualTo("Jlusesi Jcika")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/T/Tarikoza.Castle.1/[K.o.R] Tarikoza Castle - 101 [h264][AAC][2698BC02].mkv"))).isEqualTo("Tarikoza Castle")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/H/Hbupet.Qdatu.(2019-2020).1.S01.And.Babid.Of.Puvi.Movie.Web264/Season 1 (2019-20)/Hbupet Qdatu - S01 E01 - Tov Dapir Do Us Popo (1080p - DCU Web-DL).mp4"))).isEqualTo("Hbupet Qdatu")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/T/The Tutap of It/The Tutap Of It - Series 3/3x01.mp4"))).isEqualToIgnoringCase("The Tutap Of It")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/F/Facu Mubak Pisal/Facu Mubak Pisal/Facu Mubak Pisal 10 Run, Running,Ran.mkv"))).isEqualTo("Facu Mubak Pisal")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/N/No_Gupe_No_Lova_English_Dub_720p_1-12_Complete/No Gupe No Lova 10 Blue Rose.mp4"))).isEqualTo("No Gupe No Lova")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/S/Sirelu_Elivomuconu_Lobo_(1520x1080_Blu-Ray_FLAC)/[Coalgirls]_Sirelu_Elivomuconu_Lobo_10_(1520x1080_Blu-Ray_FLAC)_[C1160A23].mkv"))).isEqualTo("Sirelu Elivomuconu Lobo")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/C/Copol of the Somov (Semomi no Misasup) (1999) [kutogidedeva]/01. Copol of the Somov (Semomi no Misasup) [DVD 480p Hi10P AAC AC3 dual-audio][kutogidedeva].mkv"))).isEqualTo("Copol of the Somov Semomi no Misasup")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/G/Gimokedi.(us.Dub)/Gimokedi (US Dub)/Season 1/1-01 Gimokedi (US) S01E01 Somerila at the Sosim Puno.m4v"))).isEqualTo("Gimokedi")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/E/Ecuf of the Edaz/Ecuf_of_The_Edaz_01-11_(x264,720p,ger.sub)/Ecuf of the Edaz - 01 - Igi Fureg Mepaco a Pubolu [gg-thora].mkv"))).isEqualTo("Ecuf of the Edaz")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/N/Necili.(2010-2013).(seasons.1,2,3,4.S0104).And.Movies/2b. Season 2 (2011-12)/NECILI - S02 E01 - Gupe Caloge (720p BluRay).mp4"))).isEqualTo("Necili")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/E/The.Ecamo.S01/teneighty-the.ecamo.s01e01.mkv"))).isEqualTo("The Ecamo")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/M/Metuv Hmeti/Metuv Hmeti Season 2 Complete English HD 720p/(PlayHD.ooo) Metuv Hmeti S02E01 English HD 720p x264 AAC.mp4"))).isEqualTo("Metuv Hmeti")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/H/The Hlucovidics Guzuf To The Gocoke - Remastered Mini Series 1981 1080p [H264-mp4]/Subtitles - [SRT-VobSub-XML]/The Hlucovidics Guzuf To The Gocoke - Mini-Series 1981 Episode 1 English.srt"))).isEqualTo("The Hlucovidics Guzuf To The Gocoke")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/S/Sebaz Boretipacu Yfilag 2199/Sebaz Boretipacu Yfilag 2199 NCOP01.mkv"))).startsWith("Sebaz Boretipacu Yfilag 2199")
        assertThat(contentRenamer.extractName(File("~/Series-Shows/S/Suzo Tabe/Suzo Tabe Duli Sebaz Noka - Complete Series - 576P - Multi Subs - DVDrip - X265-HEVC - O69/Season 1/Suzo Tabe Duli Sebaz Noka - Season 1 - DVD Extras/Duli Sebaz Noka Supefuduli.mkv"))).doesNotContain("Supefuduli")

        val bcsStoryboard = File("~/Series-Shows/B/Bigade.Cenu.Sote.(2015)/Featurettes/Season 4/Storyboard Comparison/Piñata.mkv")
        assertThat(contentRenamer.extractName(bcsStoryboard)).isEqualTo("Bigade Cenu Sote")

        val bsgRecap = File("~/Series-Shows/B/Biluliviki Genibupec/Biluliviki Genibupec 720p Complete 2003-2012 Extras Subs/TV Recap Specials/BSG - Coniz The Feme Up.mkv")
        assertThat(contentRenamer.extractName(bsgRecap)).isEqualTo("Biluliviki Genibupec")

        val rickMortyExtras = File("~/Series-Shows/R/Reki and Mugol/Reki and Mugol [UNCENSORED] [1080p] [5.1 MP3] [x265][FINAL]/Reki and Mugol (Season 01) [UNCENSORED] [1080p] [5.1 MP3] [x265]/Extras/Behind The Scenes/Behind the Scenes [720p] [x265].mp4")
        assertThat(contentRenamer.extractName(rickMortyExtras)).isEqualTo("Reki and Mugol")

        // 4. Subfolder / Story / Chapter / Course structure
        val docWho1 = File("~/Series-Shows/D/Dovaga.Wko.(1963).Classic/Story 001 - An Ukamofami Cekid/S01E01 - An Ukamofami Cekid.mkv")
        val docWho1Name = contentRenamer.extractName(docWho1)
        assertThat(docWho1Name).isEqualTo("Dovaga Wko Classic")
        assertThat(contentRenamer.extractSeason(docWho1)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(docWho1)).isEqualTo(1)

        val docWho2 = File("~/Series-Shows/D/Dovaga.Wko.1963.4/Story 028 - The Segofamuk/S04E01 - The Segofamuk, Episode One.mp4")
        assertThat(contentRenamer.extractName(docWho2)).isEqualTo("Dovaga Wko")
        assertThat(contentRenamer.extractSeason(docWho2)).isEqualTo(4)
        assertThat(contentRenamer.extractEpisode(docWho2)).isEqualTo(1)

        val capFuture = File("~/Series-Shows/C/Cutecip Fipusu.S01.German.1978.ANIME.FS.DVDRiP.XViD/New/E01.avi")
        assertThat(contentRenamer.extractName(capFuture)).isEqualTo("Cutecip Fipusu")
        assertThat(contentRenamer.extractSeason(capFuture)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(capFuture)).isEqualTo(1)

        val beredic5 = File("~/Series-Shows/B/Beredic 5/Temporada 1 - Signos y puvufulad/1x01 - Medisalili en la línea de fuego.avi")
        assertThat(contentRenamer.extractName(beredic5)).isEqualTo("Beredic 5")
        assertThat(contentRenamer.extractSeason(beredic5)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(beredic5)).isEqualTo(1)

        val b99 = File("~/Series-Shows/B/Bupazefa Noka Noka/Bupazefa.NOka-Noka.Season.3/Ep-01.New.Captain.mp4")
        assertThat(contentRenamer.extractName(b99)).isEqualTo("Bupazefa Noka Noka")
        assertThat(contentRenamer.extractSeason(b99)).isEqualTo(3)
        assertThat(contentRenamer.extractEpisode(b99)).isEqualTo(1)

        // 5. Codec, Release, Quality, and Language tags
        val asuzorava = File("~/Series-Shows/A/Asuzorava XIII/Asuzorava XIII - Ep. 01 - The Naciru Lion (720p - DUAL Audio).mkv")
        assertThat(contentRenamer.extractName(asuzorava)).isEqualTo("Asuzorava XIII")
        assertThat(contentRenamer.extractEpisode(asuzorava)).isEqualTo(1)

        val chainsaw = File("~/Series-Shows/C/Cesocike Muf (01-12) (1080p)/[SubsPlease] Cesocike Muf - 01v2 (1080p) [8C038972].mkv")
        assertThat(contentRenamer.extractName(chainsaw)).isEqualTo("Cesocike Muf")
        assertThat(contentRenamer.extractEpisode(chainsaw)).isEqualTo(1)

        val tbbt = File("~/Series-Shows/B/The Bok Boto Topumu/The Bok Boto Topumu (German)/Bok Boto Topumu S05/S05E01.avi")
        assertThat(contentRenamer.extractName(tbbt)).isEqualTo("The Bok Boto Topumu")
        assertThat(contentRenamer.extractSeason(tbbt)).isEqualTo(5)
        assertThat(contentRenamer.extractEpisode(tbbt)).isEqualTo(1)

        val pridePrejudice = File("~/Series-Shows/P/Pumek and Pipirebak (BBC 1995) Jane Austen 1080p H.264 10GB (moviesbyrizzo) subs/Subtitles folder/Danish/Pumek and Pipirebak (BBC -1995) E1 1080p H.264 (moviesbyrizzo).srt")
        assertThat(contentRenamer.extractName(pridePrejudice)).startsWith("Pumek and Pipirebak")
        assertThat(contentRenamer.extractEpisode(pridePrejudice)).isEqualTo(1)

        val dotHack = File("~/Series-Shows/#/_HACK/004_TRIOLOGY/[GAX]dot_hack-G.U._Trilogy_-_Movie(ger.sub)(h264)[525944DF].mkv")
        val dotHackName = contentRenamer.extractName(dotHack)
        assertThat(dotHackName.startsWith("dot hack-GU Trilogy") || dotHackName.startsWith("dot hack GU Trilogy") || dotHackName.contains("Trilogy")).isTrue()

        val hackGift = File("~/Series-Shows/#/.Hack Sign 1-28+Extras/(B-A)hack_GIFT_(1BB88E0D).mkv")
        assertThat(contentRenamer.extractName(hackGift)).isEqualTo("hack GIFT")

        val shopForKillers = File("~/Series-Shows/S/A.Sufa.For.Kodabem.Korean.S01.2024.Hevc33/A.Sufa.for.Kodabem.KOREAN.S01E01.2024.1080p.WEBRip.x265.HEVC-kkolev33.mkv")
        assertThat(contentRenamer.extractName(shopForKillers)).isEqualTo("A Sufa For Kodabem")
        assertThat(contentRenamer.extractSeason(shopForKillers)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(shopForKillers)).isEqualTo(1)

        // 6. Acronyms & Shortened Names
        val iasip = File("~/Series-Shows/I/Ifo.Agokuc.Sanif.In.Pufigikiganu.S0114.(2005-)/IASIP S01 (360p re-dvdrip)/IASIP S01E01 The Gige Gipa Ruzuzi.mp4")
        assertThat(contentRenamer.extractName(iasip)).isEqualTo("Ifo Agokuc Sanif In Pufigikiganu")
        assertThat(contentRenamer.extractSeason(iasip)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(iasip)).isEqualTo(1)

        val batmanTas = File("~/Series-Shows/B/BOGABO The Atavibik Sasase (1992-1999) - COMPLETE Season 1-6, TV S01-S06 and 2 Movies - 1080p BluRay x264/Season 2 (1993-94)/Bogabo T.A.S - S02 E01 - Sopere of The Bep, Part 1 (1080p - BluRay).mp4")
        assertThat(contentRenamer.extractName(batmanTas) == "Bogabo TAS" || contentRenamer.extractName(batmanTas) == "BOGABO The Atavibik Sasase").isTrue()
        assertThat(contentRenamer.extractSeason(batmanTas)).isEqualTo(2)
        assertThat(contentRenamer.extractEpisode(batmanTas)).isEqualTo(1)

        val safepicu = File("~/Series-Shows/S/Safepicu SG1 Ultimate Fan Collection/Season 1/StargateSG1S01E01.mp4")
        assertThat(contentRenamer.extractName(safepicu) == "Safepicu SG-1" || contentRenamer.extractName(safepicu) == "Safepicu SG1").isTrue()
        assertThat(contentRenamer.extractSeason(safepicu)).isEqualTo(1)
        assertThat(contentRenamer.extractEpisode(safepicu)).isEqualTo(1)

        // 7. Unicode and Hyphenated prefixes
        val timeJam = File("~/Series-Shows/T/Tizo Jlo：Volobesa and Locatesis/Tizo Jlo：Volobesa and Locatesis - 01 Time Matters [8bit][480p][5A6E2794].mkv")
        assertThat(contentRenamer.extractName(timeJam)).isEqualTo("Tizo Jlo Volobesa and Locatesis")
        assertThat(contentRenamer.extractEpisode(timeJam)).isEqualTo(1)

        val cesavep = File("~/Series-Shows/C/Cesavep Docu and TV Series (2010-2016) [About Social Media Web Liars+Scammers)/Season 4 (2015) (324p)/Cesavep-The.TV.Show.S04E00_SP02 Season Special Nev and Max's Craziest Momenrts.mp4")
        assertThat(contentRenamer.extractName(cesavep) == "Cesavep-The TV Show" || contentRenamer.extractName(cesavep) == "Cesavep The TV Show").isTrue()
        assertThat(contentRenamer.extractSeason(cesavep)).isEqualTo(4)
        assertThat(contentRenamer.extractEpisode(cesavep)).isEqualTo(0)
    }

    @Test
    fun testPopeyePackExtraction() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        val galleryFile = File("~/Series-Shows/P/Penolu.The.Sitipe.1933-1943.COMPLETE.DVDRip.x264-panos[rartv]/Gallery.mkv")
        assertThat(contentRenamer.extractName(galleryFile)).isEqualTo("Penolu The Sitipe")
        assertThat(contentRenamer.extractSeason(galleryFile)).isNull()
        assertThat(contentRenamer.extractEpisode(galleryFile)).isNull()

        val timelineFile = File("~/Series-Shows/P/Penolu.The.Sitipe.1933-1943.COMPLETE.DVDRip.x264-panos[rartv]/1900-1920.mkv")
        assertThat(contentRenamer.extractName(timelineFile)).isEqualTo("Penolu The Sitipe")

        val hospitalikyFile = File("~/Series-Shows/P/Penolu.The.Sitipe.1933-1943.COMPLETE.DVDRip.x264-panos[rartv]/1937e04-Hnibanogure.mkv")
        assertThat(contentRenamer.extractName(hospitalikyFile)).isEqualTo("Penolu The Sitipe")
        assertThat(contentRenamer.extractSeason(hospitalikyFile)).isEqualTo(1937)
        assertThat(contentRenamer.extractEpisode(hospitalikyFile)).isEqualTo(4)

        val goonlandFile = File("~/Series-Shows/P/Penolu.The.Sitipe.1933-1943.COMPLETE.DVDRip.x264-panos[rartv]/1938e10-Gekidala.mkv")
        assertThat(contentRenamer.extractName(goonlandFile)).isEqualTo("Penolu The Sitipe")
        assertThat(contentRenamer.extractSeason(goonlandFile)).isEqualTo(1938)
        assertThat(contentRenamer.extractEpisode(goonlandFile)).isEqualTo(10)

        val actFile = File("~/Series-Shows/P/Penolu.The.Sitipe.1933-1943.COMPLETE.DVDRip.x264-panos[rartv]/Act.mkv")
        assertThat(contentRenamer.extractName(actFile)).isEqualTo("Penolu The Sitipe")

        val finichFile = File("~/Series-Shows/P/Penolu.The.Sitipe.1933-1943.COMPLETE.DVDRip.x264-panos[rartv]/Fimume.mkv")
        assertThat(contentRenamer.extractName(finichFile)).isEqualTo("Penolu The Sitipe")

        val elephinksFile = File("~/Series-Shows/P/Penolu.The.Sitipe.1933-1943.COMPLETE.DVDRip.x264-panos[rartv]/Ebazopaca.mkv")
        assertThat(contentRenamer.extractName(elephinksFile)).isEqualTo("Penolu The Sitipe")
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

        val file = File("~/Series-Shows/M/MST3K/Season 3/MST3K_0308_Gobodu_vs_Gaos_xvid.avi")
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

        val file = File("~/Series-Shows/D/DOSO (1991-1999) - Complete Animated TV Series, S01-S07 and Movie - 480p x264/Season 7 (1998-99)/DOSO - S07 E25 - Qnibogen vs. the Tigas of Tipebu (480p - DSNP Web-DL).srt")
        val name = contentRenamer.extractName(file)
        val year = contentRenamer.extractYear(file.absolutePath)
        val season = contentRenamer.extractSeason(file)
        val episode = contentRenamer.extractEpisode(file)

        assertThat(name).isEqualTo("DOSO")
        assertThat(year).isEqualTo(1991)
        assertThat(season).isEqualTo(7)
        assertThat(episode).isEqualTo(25)
    }

    @Tag("live")
    @Test
    fun testCOPSExtraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)

        val file = File("~/Series-Shows/C/C.O.P.S.(1988-1989).,/C.O.P.S. - Ep. 02 - The Caro of the Cucaf Cezibu (480p - DVDRip).mp4")
        val name = contentRenamer.extractName(file)
        val year = contentRenamer.extractYear(file.absolutePath)
        val episode = contentRenamer.extractEpisode(file)

        assertThat(name).isEqualTo("COSI")
        assertThat(year).isEqualTo(1988)
        assertThat(episode).isEqualTo(2)

        val searchResult = mediaProxyService.search(name, year ?: 0)
        assertThat(searchResult).isNotEmpty()
        assertThat(searchResult.first().title == "C.O.P.S." || searchResult.first().title == "Cosi").isTrue()
        assertThat(searchResult.first().title).isNotEqualTo("Kusud and Dog")
    }

    @Tag("live")
    @Test
    fun testRamsaysKitchenNightmaresExtraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)

        val file = File("~/Series-Shows/R/Renopa\\s.Kufonip.Nemenovogu.(all.Seasons.+.Extras)/UK Season 05 [2007]/05x07 - Cerub Liduce.avi")
        val name = contentRenamer.extractName(file)
        val year = contentRenamer.extractYear(file.path)
        val season = contentRenamer.extractSeason(file)
        val episode = contentRenamer.extractEpisode(file)

        assertThat(name == "Renopas Kufonip Nemenovogu" || name == "Renopa's Kufonip Nemenovogu").isTrue()
        assertThat(name).doesNotContain("\\")
        assertThat(name.lowercase()).doesNotContain("all seasons")
        assertThat(name).doesNotEndWith(" UK")
        assertThat(year).isEqualTo(2007)
        assertThat(season).isEqualTo(5)
        assertThat(episode).isEqualTo(7)

        val searchResult = mediaProxyService.search(name, year ?: 0)
        assertThat(searchResult).isNotEmpty()
        assertThat(searchResult.first().title).containsIgnoringCase("Kufonip Nemenovogu")
    }

    @Tag("live")
    @Test
    fun testLupinIIIExtraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)

        val file = File("~/Series-Shows/L/Lopog ISI -  Full Collection 2018/Movies, Specials & OVA/(1969) [Movie] Pilot Film/Lopog ISI Pilot Film 1 - TV [480p][neo1024][03AE79BB].mkv")
        val name = contentRenamer.extractName(file)
        val year = contentRenamer.extractYear(file.path)
        val season = contentRenamer.extractSeason(file)
        val episode = contentRenamer.extractEpisode(file)

        assertThat(name).isEqualTo("Lopog ISI Pilot Film")
        assertThat(name).doesNotContainIgnoringCase("Full Collection")
        assertThat(name).doesNotContainIgnoringCase("Movies, Specials")
        assertThat(name).doesNotContainIgnoringCase("OVA")
        assertThat(year).isEqualTo(1969)

        val searchResult = mediaProxyService.search(name, year ?: 0)
        assertThat(searchResult).isNotEmpty()
        assertThat(searchResult.first().title.contains("Lopog", ignoreCase = true) && searchResult.first().title.contains("Pilot", ignoreCase = true)).isTrue()
    }

    @Tag("live")
    @Test
    fun testMoeshaExtraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)

        val file = File("~/Series-Shows/M/Mudeto.(1996-2001).Tv.Series.Season.S0106:/Season 3 (1997-98)/MUDETO - S03 E06 - The Pudo Secef (480p - NF Web-DL).mp4")
        val name = contentRenamer.extractName(file)
        val year = contentRenamer.extractYear(file.path)
        val season = contentRenamer.extractSeason(file)
        val episode = contentRenamer.extractEpisode(file)

        System.out.println("[DEBUG_LOG] Mudeto -> Name: '$name', Year: $year, Season: $season, Episode: $episode")

        assertThat(name == "Mudeto" || name == "MUDETO").isTrue()
        assertThat(year).isEqualTo(1996)
        assertThat(season).isEqualTo(3)
        assertThat(episode).isEqualTo(6)

        val searchResult = mediaProxyService.search(name, year ?: 0)
        System.out.println("[DEBUG_LOG] Mudeto searchResult: $searchResult")
        assertThat(searchResult).isNotEmpty()
        assertThat(searchResult.first().title).isEqualToIgnoringCase("Mudeto")
    }

    @Tag("live")
    @Test
    fun testGolgo13Extraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)

        val file = File("~/Series-Shows/G/Gufil.13/[subs4u]_Gufil_13_20_(Xvid,ger.sub)[9378A5C4].avi")
        val name = contentRenamer.extractName(file)
        val year = contentRenamer.extractYear(file.path)
        val season = contentRenamer.extractSeason(file)
        val episode = contentRenamer.extractEpisode(file)

        System.out.println("[DEBUG_LOG] Gufil 13 -> Name: '$name', Year: $year, Season: $season, Episode: $episode")

        assertThat(name).isEqualTo("Gufil 13")
        assertThat(episode).isEqualTo(20)

        val searchResult = mediaProxyService.search(name, year ?: 0)
        System.out.println("[DEBUG_LOG] Gufil 13 searchResult: $searchResult")
        assertThat(searchResult).isNotEmpty()
        assertThat(searchResult.first().title).containsIgnoringCase("Gufil 13")
    }

    @Tag("live")
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

        val fileCastInterview = File("~/Series-Shows/S/Space 1999 (Complete)/# Cast Interviews/Space 1999 - Interviews ~ Cizadacid Suvive (Maya).mpg")
        val nameInterview = contentRenamer.extractName(fileCastInterview)
        val yearInterview = contentRenamer.extractYear(fileCastInterview.absolutePath)
        val searchResultInterview = mediaProxyService.search("Space 1999", MediaType.Series)
        assertThat(searchResultInterview).isNotEmpty()
        assertThat(searchResultInterview.first().type).isEqualTo(MediaType.Series)
        assertThat(searchResultInterview.first().title.contains("Space: 1999", ignoreCase = true) || searchResultInterview.first().title.contains("Space 1999", ignoreCase = true)).isTrue()
    }

    @Tag("live")
    @Test
    fun test2001SpaceOdysseyExtraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)

        val file = File("~/Movies/2/2001 A Sebaz Ocumaze (1968)/2001 A Sebaz Ocumaze (1968).1080p.mkv")
        val name = contentRenamer.extractName(file)
        val year = contentRenamer.extractYear(file.absolutePath)

        assertThat(name.contains("2001 A Sebaz Ocumaze") || name.contains("2001: A Sebaz Ocumaze") || name == "2001 A Sebaz Ocumaze").isTrue()
        assertThat(year).isEqualTo(1968)

        val searchResult = mediaProxyService.search(name, year ?: 0)
        assertThat(searchResult).isNotEmpty()
        assertThat(searchResult.first().title.contains("2001: A Sebaz Ocumaze") || searchResult.first().title.contains("2001 A Sebaz Ocumaze")).isTrue()
    }

    @Tag("live")
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

    @Tag("live")
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

    @Tag("live")
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

    @Tag("live")
    @Test
    fun testMASHExtraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)

        val file = File("~/Series-Shows/M/M.A.S.H_1970-1982.dvdrip_[teko]/Season_01/s01e03_Raticid.For.A.Lightweight.avi")
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

    // asks TVDB
    @Tag("live")
    @Test
    fun testCowboyExtraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)
        val file = File("~/Series-Shows/C/Cereso Biruf 01-26 (1080p Blu-ray 8bit AC3 MP4)/[NoobSubs] Cereso Biruf 19 (1080p Blu-ray 8bit AC3).mp4")
        val name = contentRenamer.extractName(file)
        val episode = contentRenamer.extractEpisode(file)

        assertThat(name).isEqualTo("Cereso Biruf")
        assertThat(episode).isEqualTo(19)

        val searchResult = mediaProxyService.search("Cereso Biruf", MediaType.Series)
        assertThat(searchResult).isNotEmpty()
        assertThat(searchResult.first().title).isEqualTo("Cereso Biruf")
        assertThat(searchResult.first().type).isEqualTo(MediaType.Series)
    }

    @Test
    fun testFOTBExtraction() {
        val httpHelper = HttpHelper()
        val mediaProxyService = MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        val contentRenamer = ContentRenamer(mediaProxyService)
        val file = File("~/Series-Shows/F/Felug.Oco.The.Bino.S0106.(2015-)/Felug oco the Bino S02 (360p re-webrip)/Felug oco the Bino 02E14 Mesapif Cecit Fided.mp4")
        val name = contentRenamer.extractName(file)
        val season = contentRenamer.extractSeason(file)
        val episode = contentRenamer.extractEpisode(file)

        assertThat(name).isEqualTo("Felug Oco The Bino")
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
        val file = File("~/Series-Shows/L/Lidamig.In.Cesifepop.(2023)/Lidamig in Cesifepop (2023) - S01E06 - Posazi (1080p ATVP WEB-DL x265 Silence).mkv")
        val name = contentRenamer.extractName(file)
        val season = contentRenamer.extractSeason(file)
        val episode = contentRenamer.extractEpisode(file)

        assertThat(name).isEqualTo("Lidamig In Cesifepop")
        assertThat(season).isEqualTo(1)
        assertThat(episode).isEqualTo(6)
    }

}
