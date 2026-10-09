package boat

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
            "/data/TV/The.Mandalorian.S01E01.1080p.mkv",
            "/data/TV/Stranger.Things.S03.2019/Stranger.Things.S03E05.mkv",
            "/data/Movies/Inception.2010.720p.mkv",
            "/data/TV/The.Boys.S02E08.mkv",
            "/data/Movies/Interstellar.2014.1080p.mkv",
            "/data/TV/Dark.S01.GERMAN.1080p/Dark.S01E01.mkv",
            "/data/TV/Chernobyl.S01.1080p/Chernobyl.E01.mkv",
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
            "~/Series-Shows/D/Dragon Ball Super/Dragon Ball Super - Completo [WEB-DL - 1080p]/Dragon%20Ball%20Super%20-%20101%20%5B1080p%5D.mkv"
        )

        println("\n--- Testing Extraction Logic ---")
        testPaths.forEach { fullName ->
            val name = contentRenamer.extractName(fullName)
            val year = contentRenamer.extractYear(fullName)
            val season = contentRenamer.extractSeason(fullName)
            val episode = contentRenamer.extractEpisode(fullName)
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
            }
            if (fullName.contains("101 - The Boy in the Iceberg.mp4")) {
                val episode = contentRenamer.extractEpisode(fullName)
                assert(episode == 101) { "Expected episode 101 but got $episode" }
            }
            if (fullName.contains("Dragon Ball Super - 101 [1080p].mkv")) {
                val episode = contentRenamer.extractEpisode(fullName)
                assert(episode == 101) { "Expected episode 101 but got $episode" }
            }
        }
    }

}
