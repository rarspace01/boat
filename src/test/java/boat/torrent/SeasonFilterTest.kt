package boat.torrent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SeasonFilterTest {

    private fun matches(name: String, season: Int) = SeasonFilter.matches(name, season)

    @Test
    fun shouldMatchEpisodesAndSeasonPacksOfTheSeason() {
        listOf(
            "The.Office.US.S02E05.1080p.WEB-DL", "The Office S02 Complete 720p", "The.Office.S2.1080p", "The Office (US) Season 2 Complete",
            "The.Office.US.Season.2.1080p.BluRay", "The Office 2x05 HDTV", "the_office_s02e05_hdtv", "The Office S02-E05", "The Office - Season2",
        ).forEach { assertThat(matches(it, 2)).describedAs(it).isTrue }
    }

    @Test
    fun shouldNotMatchOtherSeasons() {
        listOf(
            "The.Office.US.S03E05.1080p.WEB-DL", "The Office S01 Complete", "The Office Season 12", "The Office 12x05", "The.Office.S12E01",
            "The Office (US) Season 1", "The Office S20E01",
        ).forEach { assertThat(matches(it, 2)).describedAs(it).isFalse }
    }

    @Test
    fun shouldMatchSeasonsInsideARange() {
        listOf(
            "The Office S01-S05 Complete", "The Office S01-05 1080p", "The Office Seasons 1-5", "The Office Season 1 to 5", "The Office S01 & S02",
            "The Office S1-S3", "The Office Seasons 1 and 2",
        ).forEach { assertThat(matches(it, 2)).describedAs(it).isTrue }
        assertThat(matches("The Office S03-S05 Complete", 2)).isFalse
        assertThat(matches("The Office Seasons 3-5", 2)).isFalse
    }

    @Test
    fun shouldMatchCompleteSeriesPacks() {
        listOf("The Office Complete Series 1080p", "The.Office.US.Complete.Collection", "The Office All Seasons", "The Office Boxset", "The Office Full Series").forEach {
            assertThat(matches(it, 4)).describedAs(it).isTrue
        }
    }

    @Test
    fun shouldNotMatchNamesWithoutAnySeasonInformation() {
        listOf("The Office US 2005 1080p WEB-DL", "Dune 2021 1080p", "Planet Earth", "S.W.A.T. 2017 720p", "Mr. Robot 1080p").forEach {
            assertThat(matches(it, 1)).describedAs(it).isFalse
        }
    }

    @Test
    fun shouldNotTreatWordsOrYearsAsSeasons() {
        // "S3" in a word, 1080p, x264, years
        assertThat(matches("Dolby.DTS3.Dune.2021.1080p.x264", 3)).isFalse
        assertThat(matches("Show 2160p x265 10bit", 10)).isFalse
        assertThat(matches("Show S01E02 1080p", 1080)).isFalse
    }
}
