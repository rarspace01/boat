package boat

import boat.info.MediaItem
import boat.info.MediaProxyService
import boat.info.TheMovieDataBaseService
import boat.info.TheTVDBService
import boat.utilities.HttpHelper
import org.junit.jupiter.api.Disabled
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
                assert(name == "Come Fly With Me") { "Expected 'Come Fly With Me' but got '$name'" }
            }
            if (fullName.contains("Dragon%20Ball%20Super")) {
                assert(name == "Dragon Ball Super") { "Expected 'Dragon Ball Super' but got '$name'" }
            }
            if (fullName.contains("071c-Star.Wars.The.Bad.Batch")) {
                assert(name == "Star Wars The Bad Batch") { "Expected 'Star Wars The Bad Batch' but got '$name'" }
            }
            if (fullName.contains("www.Torrenting.com")) {
                assert(name == "90 Day Fiance Happily Ever After") { "Expected '90 Day Fiance Happily Ever After' but got '$name'" }
            }
            if (fullName.contains("Adventure Time")) {
                assert(name == "Adventure Time") { "Expected 'Adventure Time' but got '$name'" }
            }
            if (fullName.contains("Avatar - The Last Airbender")) {
                assert(name == "Avatar - The Last Airbender") { "Expected 'Avatar - The Last Airbender' but got '$name'" }
            }
            if (fullName.contains("Duckman")) {
                assert(name == "Duckman") { "Expected 'Duckman' but got '$name'" }
                assert(season == 1) { "Expected season 1 but got $season" }
                assert(episode == 1) { "Expected episode 1 but got $episode" }
            }
            if (fullName.contains("101 - The Boy in the Iceberg.mp4")) {
                val ep = contentRenamer.extractEpisode(file)
                val se = contentRenamer.extractSeason(file)
                assert(se == 1) { "Expected season 1 but got $se" }
                assert(ep == 1) { "Expected episode 1 but got $ep" }
            }
            if (fullName.contains("Dragon Ball Super - 101 [1080p].mkv")) {
                val ep = contentRenamer.extractEpisode(file)
                assert(ep == 101) { "Expected episode 101 but got $ep" }
            }
            if (fullName.contains("Reservation Dogs")) {
                assert(name == "Reservation Dogs") { "Expected 'Reservation Dogs' but got '$name'" }
                assert(season == 1) { "Expected season 1 but got $season" }
                assert(episode == 7) { "Expected episode 7 but got $episode" }
            }
            if (fullName.contains("Zombieland%20Saga")) {
                assert(name == "Zombieland Saga") { "Expected 'Zombieland Saga' but got '$name'" }
            }
            if (fullName.contains("SIGN/HS28.avi")) {
                assert(name.equals("HACK SIGN", ignoreCase = true)) { "Expected 'HACK SIGN' but got '$name'" }
                assert(episode == 28) { "Expected episode 28 but got $episode" }
            }
            if (fullName.contains("24 - S01xE11.mp4")) {
                assert(name == "24") { "Expected '24' but got '$name'" }
                assert(season == 1) { "Expected season 1 but got $season" }
                assert(episode == 11) { "Expected episode 11 but got $episode" }
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
        assert(contentRenamer.isJunk(""))
        assert(contentRenamer.isJunk("   "))
        assert(contentRenamer.isJunk("A"))
        assert(contentRenamer.isJunk("#"))
        assert(contentRenamer.isJunk("0-9"))
        assert(contentRenamer.isJunk("TV"))
        assert(contentRenamer.isJunk("Series-Shows"))
        assert(contentRenamer.isJunk("Downloads"))
        assert(contentRenamer.isJunk("Season 1"))
        assert(contentRenamer.isJunk("Season 02"))
        assert(contentRenamer.isJunk("Disc 1"))
        assert(contentRenamer.isJunk("CD 1"))
        assert(contentRenamer.isJunk("DVD 1"))
        assert(contentRenamer.isJunk("Vol 1"))
        assert(contentRenamer.isJunk("Volume 2"))
        assert(contentRenamer.isJunk("Book 1"))
        assert(contentRenamer.isJunk("Extras"))
        assert(contentRenamer.isJunk("Featurettes"))
        assert(contentRenamer.isJunk("Sample"))
        assert(contentRenamer.isJunk("Samples"))
        assert(contentRenamer.isJunk("Trailer"))
        assert(contentRenamer.isJunk("Trailers"))
        assert(contentRenamer.isJunk("Subs"))
        assert(contentRenamer.isJunk("Subtitles"))
        assert(contentRenamer.isJunk("6 spa"))
        assert(contentRenamer.isJunk("6_spa"))
        assert(contentRenamer.isJunk("1080p"))
        assert(contentRenamer.isJunk("720p"))
        assert(contentRenamer.isJunk("x264"))
        assert(contentRenamer.isJunk("x265"))
        assert(contentRenamer.isJunk("RARBG"))
        assert(contentRenamer.isJunk("101"))
        assert(contentRenamer.isJunk("01.01"))
        assert(contentRenamer.isJunk("1x01"))
        assert(contentRenamer.isJunk("S01E01"))
        assert(contentRenamer.isJunk("Deleted Scenes"))
        assert(contentRenamer.isJunk("Alternate Ending"))
        assert(contentRenamer.isJunk("Alternate Endings"))
        assert(contentRenamer.isJunk("Special Features"))
        assert(contentRenamer.isJunk("HS22"))
        assert(contentRenamer.isJunk("E01"))
        assert(contentRenamer.isJunk("EP01"))
        assert(contentRenamer.isJunk("Outtakes"))
        assert(contentRenamer.isJunk("Bloopers"))
        assert(contentRenamer.isJunk("Gag Reel"))
        assert(contentRenamer.isJunk("Behind The Scenes"))
        assert(contentRenamer.isJunk("Making Of"))
        assert(contentRenamer.isJunk("Screentests"))
        assert(contentRenamer.isJunk("Covers and Fonts"))
        assert(contentRenamer.isJunk("Original Scripts"))
        assert(contentRenamer.isJunk("http://example.com"))
        assert(contentRenamer.isJunk("magnet:?xt=urn:btih:..."))

        // Legitimate titles (not junk)
        assert(!contentRenamer.isJunk("Dark"))
        assert(!contentRenamer.isJunk("Lost"))
        assert(!contentRenamer.isJunk("The Mandalorian"))
        assert(!contentRenamer.isJunk("Inception"))
        assert(!contentRenamer.isJunk("Chernobyl"))
        assert(!contentRenamer.isJunk("Star Wars The Bad Batch"))
        assert(!contentRenamer.isJunk("Avatar - The Last Airbender"))
        assert(!contentRenamer.isJunk("Reservation Dogs"))
        assert(!contentRenamer.isJunk("24 Complete With Subtitles"))
        assert(!contentRenamer.isJunk("9-1-1"))
        assert(!contentRenamer.isJunk("9 1 1"))
        assert(!contentRenamer.isJunk("9-1-1: Lone Star"))
    }

    @Test
    fun testConservativeExtractionAndAuxiliaryFiles() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        // Auxiliary files in show folders resolve to the show name
        val angelStunts = File("~/Series-Shows/A/Angel Complete Series Season 1,2,3,4,5 + Extras, Subs/Extras/Season 2/Featurettes/Stunts.mkv")
        assert(contentRenamer.extractName(angelStunts).startsWith("Angel")) {
            "Expected 'Angel...' but got '${contentRenamer.extractName(angelStunts)}'"
        }

        val stargateTrailer = File("~/Series-Shows/S/Stargate SG1 Ultimate Fan Collection/Season 1/Extras/Season 2 Trailer.mp4")
        assert(contentRenamer.extractName(stargateTrailer).startsWith("Stargate SG1")) {
            "Expected 'Stargate SG1...' but got '${contentRenamer.extractName(stargateTrailer)}'"
        }

        val stargateFeaturette = File("~/Series-Shows/S/Stargate SG1 Ultimate Fan Collection/Season 1/Extras/CostumeDesignFeaturette.mp4")
        assert(contentRenamer.extractName(stargateFeaturette).startsWith("Stargate SG1")) {
            "Expected 'Stargate SG1...' but got '${contentRenamer.extractName(stargateFeaturette)}'"
        }

        val twentyFourExtra = File("~/Series-Shows/0-9/24.Complete.With.Subtitles/24 Season 1/24 EXTRAS/Alternate Ending.mp4")
        assert(contentRenamer.extractName(twentyFourExtra) == "24") {
            "Expected '24' but got '${contentRenamer.extractName(twentyFourExtra)}'"
        }
        assert(contentRenamer.extractSeason(twentyFourExtra) == 1) {
            "Expected season 1 but got '${contentRenamer.extractSeason(twentyFourExtra)}'"
        }
        assert(contentRenamer.extractEpisode(twentyFourExtra) == null) {
            "Expected null episode but got '${contentRenamer.extractEpisode(twentyFourExtra)}'"
        }

        val twentyFourInterview = File("~/Series-Shows/0-9/24.Complete.With.Subtitles/24 Season 1/24 EXTRAS/Kiefer Sutherland on season 1 of 24.mp4")
        assert(contentRenamer.extractName(twentyFourInterview) == "24") {
            "Expected '24' but got '${contentRenamer.extractName(twentyFourInterview)}'"
        }
        assert(contentRenamer.extractSeason(twentyFourInterview) == 1) {
            "Expected season 1 but got '${contentRenamer.extractSeason(twentyFourInterview)}'"
        }

        val nineOneOneFile = File("~/Series-Shows/0-9/9-1-1/9-1-1.S05E06.1080p.WEB.H264-CAKES[TGx].mkv")
        assert(contentRenamer.extractName(nineOneOneFile) == "9-1-1") {
            "Expected '9-1-1' but got '${contentRenamer.extractName(nineOneOneFile)}'"
        }
        assert(contentRenamer.extractSeason(nineOneOneFile) == 5) {
            "Expected season 5 but got '${contentRenamer.extractSeason(nineOneOneFile)}'"
        }
        assert(contentRenamer.extractEpisode(nineOneOneFile) == 6) {
            "Expected episode 6 but got '${contentRenamer.extractEpisode(nineOneOneFile)}'"
        }

        val hackSignFile = File("~/PFDB/Series-Shows/#/_HACK/SIGN/HS22.avi")
        assert(contentRenamer.extractName(hackSignFile).equals("HACK SIGN", ignoreCase = true)) {
            "Expected 'HACK SIGN' but got '${contentRenamer.extractName(hackSignFile)}'"
        }
        assert(contentRenamer.extractSeason(hackSignFile) == null) {
            "Expected null season but got '${contentRenamer.extractSeason(hackSignFile)}'"
        }
        assert(contentRenamer.extractEpisode(hackSignFile) == 22) {
            "Expected episode 22 but got '${contentRenamer.extractEpisode(hackSignFile)}'"
        }

        val hackSignExtrasFile = File("~/PFDB/Series-Shows/#/.Hack Sign 1-28+Extras/[V-A]_hack_SIGN_-_05_[E3229E6C].mkv")
        assert(contentRenamer.extractName(hackSignExtrasFile) == "hack SIGN") {
            "Expected 'hack SIGN' but got '${contentRenamer.extractName(hackSignExtrasFile)}'"
        }
        assert(contentRenamer.extractSeason(hackSignExtrasFile) == null) {
            "Expected null season but got '${contentRenamer.extractSeason(hackSignExtrasFile)}'"
        }
        assert(contentRenamer.extractEpisode(hackSignExtrasFile) == 5) {
            "Expected episode 5 but got '${contentRenamer.extractEpisode(hackSignExtrasFile)}'"
        }

        val hackLegendTwilightFile = File("~/PFDB/Series-Shows/#/_HACK/Legend_o_T/hack-Legend of the Twilight 08 - The Solitary Knight.avi")
        assert(contentRenamer.extractName(hackLegendTwilightFile) == "hack-Legend of the Twilight") {
            "Expected 'hack-Legend of the Twilight' but got '${contentRenamer.extractName(hackLegendTwilightFile)}'"
        }
        assert(contentRenamer.extractSeason(hackLegendTwilightFile) == null) {
            "Expected null season but got '${contentRenamer.extractSeason(hackLegendTwilightFile)}'"
        }
        assert(contentRenamer.extractEpisode(hackLegendTwilightFile) == 8) {
            "Expected episode 8 but got '${contentRenamer.extractEpisode(hackLegendTwilightFile)}'"
        }

        val hackRootsFile = File("~/PFDB/Series-Shows/#/_HACK/ROOTS/hack_roots-21.avi")
        assert(contentRenamer.extractName(hackRootsFile) == "hack roots") {
            "Expected 'hack roots' but got '${contentRenamer.extractName(hackRootsFile)}'"
        }
        assert(contentRenamer.extractSeason(hackRootsFile) == null) {
            "Expected null season but got '${contentRenamer.extractSeason(hackRootsFile)}'"
        }
        assert(contentRenamer.extractEpisode(hackRootsFile) == 21) {
            "Expected episode 21 but got '${contentRenamer.extractEpisode(hackRootsFile)}'"
        }

        // Standalone junk files or files in generic folders with no show name should NOT extract a wrong name (return empty string)
        val standaloneSample = File("~/Downloads/Sample.mkv")
        assert(contentRenamer.extractName(standaloneSample) == "") {
            "Expected '' but got '${contentRenamer.extractName(standaloneSample)}'"
        }

        val standaloneFeaturette = File("~/Downloads/Featurettes/Stunts.mkv")
        assert(contentRenamer.extractName(standaloneFeaturette) == "") {
            "Expected '' but got '${contentRenamer.extractName(standaloneFeaturette)}'"
        }

        val standaloneTrailer = File("~/Downloads/Extras/Season 2 Trailer.mp4")
        assert(contentRenamer.extractName(standaloneTrailer) == "") {
            "Expected '' but got '${contentRenamer.extractName(standaloneTrailer)}'"
        }

        val standaloneSubs = File("~/Downloads/Subs/6_spa.srt")
        assert(contentRenamer.extractName(standaloneSubs) == "") {
            "Expected '' but got '${contentRenamer.extractName(standaloneSubs)}'"
        }

        val standaloneCodec = File("~/Downloads/1080p.x264.mkv")
        assert(contentRenamer.extractName(standaloneCodec) == "") {
            "Expected '' but got '${contentRenamer.extractName(standaloneCodec)}'"
        }

        val standaloneRARBG = File("~/Downloads/RARBG.mp4")
        assert(contentRenamer.extractName(standaloneRARBG) == "") {
            "Expected '' but got '${contentRenamer.extractName(standaloneRARBG)}'"
        }
    }

    @Test
    fun testGetSafeTitle() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        assert(contentRenamer.getSafeTitle(MediaItem("Star Wars: The Bad Batch")) == "Star Wars The Bad Batch")
        assert(contentRenamer.getSafeTitle(MediaItem("Face/Off")) == "FaceOff")
        assert(contentRenamer.getSafeTitle(MediaItem("What If...?")) == "What If...")
        assert(contentRenamer.getSafeTitle(MediaItem("Back\\Slash")) == "BackSlash")
        assert(contentRenamer.getSafeTitle(MediaItem("Title*With\"Dangerous<Chars>And|Pipes")) == "TitleWithDangerousCharsAndPipes")
        assert(contentRenamer.getSafeTitle(MediaItem("1000-lb Sisters (2020)")) == "1000-lb Sisters (2020)")
        assert(contentRenamer.getSafeTitle("Simple Title") == "Simple Title")
        assert(contentRenamer.getSafeTitle("This//Hack") == "This Hack")
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
        assert(target1.contains("/Season 1/The Mandalorian.S01E01.mkv")) { "Expected S01E01 but got $target1" }

        // Season 1, Episode 101 -> S01E101
        val target2 = contentRenamer.getTargetFile(File("mandalorian.mkv"), seriesItem, "The Mandalorian", 1, 101)
        assert(target2.contains("/Season 1/The Mandalorian.S01E101.mkv")) { "Expected S01E101 but got $target2" }

        // Season null, Episode 5 -> S01E05
        val target3 = contentRenamer.getTargetFile(File("mandalorian.mkv"), seriesItem, "The Mandalorian", null, 5)
        assert(target3.contains("/The Mandalorian.S01E05.mkv")) { "Expected S01E05 but got $target3" }

        // Season 10, Episode 24 -> S10E24
        val target4 = contentRenamer.getTargetFile(File("mandalorian.mkv"), seriesItem, "The Mandalorian", 10, 24)
        assert(target4.contains("/Season 10/The Mandalorian.S10E24.mkv")) { "Expected S10E24 but got $target4" }

        // Movie (no season, no episode)
        val movieItem = MediaItem(title = "Inception", type = boat.info.MediaType.Movie, id = "67890")
        val targetMovie = contentRenamer.getTargetFile(File("inception.mkv"), movieItem, "Inception", null, null)
        assert(targetMovie == "~/pfdb/PFDB/Movies/I/Inception 67890.mkv") { "Unexpected movie path: $targetMovie" }

        // 9-1-1 Series with resolution and codec
        val nineOneOneItem = MediaItem(title = "9-1-1", type = boat.info.MediaType.Series, id = "{tvdb-334586}")
        val targetNineOneOne = contentRenamer.getTargetFile(File("9-1-1.S05E06.1080p.WEB.H264-CAKES[TGx].mkv"), nineOneOneItem, "9-1-1", 5, 6)
        assert(targetNineOneOne == "~/pfdb/PFDB/Series-Shows/0-9/9-1-1 {tvdb-334586}/Season 5/9-1-1.S05E06.1080p.h264.mkv") { "Unexpected 9-1-1 target path: $targetNineOneOne" }

        // Buffy Series with resolution and codec
        val buffyItem = MediaItem(title = "Buffy the Vampire Slayer", type = boat.info.MediaType.Series, id = "{tvdb-70327}")
        val targetBuffy = contentRenamer.getTargetFile(
            File("Buffy.The.Vampire.Slayer.S08E07.No.Future.For.You.(2).MOTION.COMIC.720p.WEB-DL.AC3-5.1.x264-DEMENTA.mkv"),
            buffyItem,
            "Buffy The Vampire Slayer",
            8,
            7
        )
        assert(targetBuffy == "~/pfdb/PFDB/Series-Shows/B/Buffy the Vampire Slayer {tvdb-70327}/Season 8/Buffy The Vampire Slayer.S08E07.720p.x264.mkv") { "Unexpected Buffy target path: $targetBuffy" }

        // The Big Bang Theory Series (Folder should be B not T)
        val tbbtItem = MediaItem(title = "The Big Bang Theory", type = boat.info.MediaType.Series, id = "{tvdb-80379}")
        val targetTbbt = contentRenamer.getTargetFile(File("The Big Bang Theory S02E08.mkv"), tbbtItem, "The Big Bang Theory", 2, 8)
        assert(targetTbbt == "~/pfdb/PFDB/Series-Shows/B/The Big Bang Theory {tvdb-80379}/Season 2/The Big Bang Theory.S02E08.mkv") { "Unexpected TBBT target path: $targetTbbt" }

        // .hack//SIGN series
        val hackSignItem = MediaItem(title = ".hack//SIGN", type = boat.info.MediaType.Series, id = "{tmdb-333800}")
        val targetHackSign = contentRenamer.getTargetFile(File("HS22.avi"), hackSignItem, "SIGN", 1, 22)
        assert(targetHackSign == "~/pfdb/PFDB/Series-Shows/H/.hack SIGN {tmdb-333800}/Season 1/SIGN.S01E22.avi") { "Unexpected hack sign target path: $targetHackSign" }

        // Extra with season but no episode
        val extraItem = MediaItem(title = "24", type = boat.info.MediaType.Series, id = "{tvdb-1234}")
        val targetExtra = contentRenamer.getTargetFile(File("Alternate Ending.mp4"), extraItem, "24", 1, null)
        assert(targetExtra == "~/pfdb/PFDB/Series-Shows/0-9/24 {tvdb-1234}/Season 1/Alternate Ending.mp4") { "Unexpected extra target path: $targetExtra" }

        // Extra with series media type but no season and no episode (keep original file name in series folder)
        val hackSignExtra = File("~/PFDB/Series-Shows/#/.Hack Sign 1-28+Extras/[V-A]_hack_SIGN_-_Textless_Ending_[934E6984].mkv")
        val targetHackSignExtra = contentRenamer.getTargetFile(hackSignExtra, hackSignItem, "Hack Sign", null, null)
        assert(targetHackSignExtra == "~/pfdb/PFDB/Series-Shows/H/.hack SIGN {tmdb-333800}/[V-A]_hack_SIGN_-_Textless_Ending_[934E6984].mkv") { "Unexpected hack sign extra target path: $targetHackSignExtra" }
    }

    @Test
    fun testBugsBunnyAndBlacklistExtraction() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        // Bugs Bunny file
        val bugsBunnyFile = File("~/PFDB/Series-Shows/B/Bugs Bunny Pack (1938-2004)/Bugs Bunny - (Ep. 23) - Super-Rabbit.avi")
        val bugsName = contentRenamer.extractName(bugsBunnyFile)
        val bugsEpisode = contentRenamer.extractEpisode(bugsBunnyFile)
        assert(bugsName == "Bugs Bunny") { "Expected 'Bugs Bunny' but got '$bugsName'" }
        assert(bugsEpisode == 23) { "Expected episode 23 but got '$bugsEpisode'" }

        // The Blacklist file
        val blacklistFile = File("~/PFDB/Series-Shows/B/The Blacklist/Season 1/The Blacklist Episode 14 - Madeline Pratt.mp4")
        val blacklistName = contentRenamer.extractName(blacklistFile)
        val blacklistEpisode = contentRenamer.extractEpisode(blacklistFile)
        val blacklistSeason = contentRenamer.extractSeason(blacklistFile)
        assert(blacklistName == "The Blacklist") { "Expected 'The Blacklist' but got '$blacklistName'" }
        assert(blacklistSeason == 1) { "Expected season 1 but got '$blacklistSeason'" }
        assert(blacklistEpisode == 14) { "Expected episode 14 but got '$blacklistEpisode'" }

        // Black Butler file
        val blackButlerFile = File("~/PFDB/Series-Shows/B/Black Butler/Black Butler Season 2 [1080p]/Black Butler 02.mkv")
        val blackButlerName = contentRenamer.extractName(blackButlerFile)
        val blackButlerEpisode = contentRenamer.extractEpisode(blackButlerFile)
        val blackButlerSeason = contentRenamer.extractSeason(blackButlerFile)
        assert(blackButlerName == "Black Butler") { "Expected 'Black Butler' but got '$blackButlerName'" }
        assert(blackButlerSeason == 2) { "Expected season 2 but got '$blackButlerSeason'" }
        assert(blackButlerEpisode == 2) { "Expected episode 2 but got '$blackButlerEpisode'" }
    }

    @Test
    fun testUmbrellaFranchiseExtraction() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        // /Series-Shows/#/_HACK/SIGN/HS28.avi -> "HACK SIGN"
        val hackSignFile = File("~/PFDB/Series-Shows/#/_HACK/SIGN/HS28.avi")
        val hackSignName = contentRenamer.extractName(hackSignFile)
        val hackSignEpisode = contentRenamer.extractEpisode(hackSignFile)
        assert(hackSignName.equals("HACK SIGN", ignoreCase = true)) { "Expected 'HACK SIGN' but got '$hackSignName'" }
        assert(hackSignEpisode == 28) { "Expected episode 28 but got '$hackSignEpisode'" }

        // /Series-Shows/S/Star Trek/Discovery/Season 1/01.mkv -> "Star Trek Discovery"
        val starTrekFile = File("~/PFDB/Series-Shows/S/Star Trek/Discovery/Season 1/01.mkv")
        val starTrekName = contentRenamer.extractName(starTrekFile)
        println("[DEBUG_LOG] starTrekName: '$starTrekName'")
        assert(starTrekName.equals("Star Trek Discovery", ignoreCase = true)) { "Expected 'Star Trek Discovery' but got '$starTrekName'" }

        // Boundary check: single-letter folder 'S' and category 'Series-Shows' are not included
        val boundaryFile = File("~/PFDB/Series-Shows/S/Salamander/Season 1/01.mkv")
        val boundaryName = contentRenamer.extractName(boundaryFile)
        assert(boundaryName.equals("Salamander", ignoreCase = true)) { "Expected 'Salamander' but got '$boundaryName'" }
    }

    @Test
    fun testTwentyFourExtraction() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        val file1 = File("~/Series-Shows/0-9/24.Complete.With.Subtitles/24 Season 1/24 Season 1/24 - S01xE11.mp4")
        assert(contentRenamer.extractName(file1) == "24") { "Expected '24' but got '${contentRenamer.extractName(file1)}'" }
        assert(contentRenamer.extractSeason(file1) == 1) { "Expected season 1 but got '${contentRenamer.extractSeason(file1)}'" }
        assert(contentRenamer.extractEpisode(file1) == 11) { "Expected episode 11 but got '${contentRenamer.extractEpisode(file1)}'" }

        val file2 = File("~/Series-Shows/0-9/24.Complete.With.Subtitles/24 Season 2/24 - [2x01] - Day 2  8 00 A.M. - 9 00 A.M.mkv")
        assert(contentRenamer.extractName(file2) == "24") { "Expected '24' but got '${contentRenamer.extractName(file2)}'" }
        assert(contentRenamer.extractSeason(file2) == 2) { "Expected season 2 but got '${contentRenamer.extractSeason(file2)}'" }
        assert(contentRenamer.extractEpisode(file2) == 1) { "Expected episode 1 but got '${contentRenamer.extractEpisode(file2)}'" }

        val file3 = File("~/Series-Shows/0-9/24.Complete.With.Subtitles/24 Season 1/24 EXTRAS/Alternate Ending.mp4")
        assert(contentRenamer.extractName(file3) == "24") { "Expected '24' but got '${contentRenamer.extractName(file3)}'" }
        assert(contentRenamer.extractSeason(file3) == 1) { "Expected season 1 but got '${contentRenamer.extractSeason(file3)}'" }
        assert(contentRenamer.extractEpisode(file3) == null) { "Expected null episode but got '${contentRenamer.extractEpisode(file3)}'" }
    }

    @Test
    fun testThreeDigitEpisodeDisambiguation() {
        val httpHelper = HttpHelper()
        val contentRenamer = ContentRenamer(
            MediaProxyService(TheMovieDataBaseService(httpHelper), TheTVDBService(httpHelper))
        )

        // Tier 1: Inside Season / Book folders, 101/204 resolves to S01E01 / S02E04
        val avatarFile = File("~/Series-Shows/A/Avatar - The Last Airbender [1080p]/Book 1; Water/101 - The Boy in the Iceberg.mp4")
        assert(contentRenamer.extractSeason(avatarFile) == 1)
        assert(contentRenamer.extractEpisode(avatarFile) == 1)

        val duckmanFile = File("~/Series-Shows/D/Duckman/Season 1/Duckman 101 - I, Duckman.mkv")
        assert(contentRenamer.extractSeason(duckmanFile) == 1)
        assert(contentRenamer.extractEpisode(duckmanFile) == 1)

        val duckmanS2 = File("~/Series-Shows/D/Duckman/Season 2/Duckman 203 - Cellar Beware.mkv")
        assert(contentRenamer.extractSeason(duckmanS2) == 2)
        assert(contentRenamer.extractEpisode(duckmanS2) == 3)

        // Tier 2: Zero-padded absolute episodes (001, 101) in anime / long-running shows
        val dbSuper001 = File("~/Series-Shows/D/Dragon Ball Super/Dragon Ball Super - Completo [WEB-DL - 1080p]/Dragon Ball Super - 001 [1080p].mkv")
        assert(contentRenamer.extractEpisode(dbSuper001) == 1)

        val dbSuper101 = File("~/Series-Shows/D/Dragon Ball Super/Dragon Ball Super - Completo [WEB-DL - 1080p]/Dragon Ball Super - 101 [1080p].mkv")
        assert(contentRenamer.extractEpisode(dbSuper101) == 101)

        // Tier 3: Metadata verification with optional seasonCount
        val seriesWithSeasonCount = MediaItem(title = "Test Show", type = boat.info.MediaType.Series).apply {
            seasonCount = 3
        }
        val standaloneShowFile = File("~/Series-Shows/T/Test Show/Test Show - 105.mkv")
        val episodeWithMeta = contentRenamer.extractEpisode(standaloneShowFile, seriesWithSeasonCount)
        assert(episodeWithMeta == 5) { "Expected episode 5 but got $episodeWithMeta" }
    }

}
