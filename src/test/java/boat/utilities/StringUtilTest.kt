package boat.utilities

import boat.utilities.StringUtil.Companion.deductFirstTorrentLetter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class StringUtilTest {

    @Test
    fun shouldDeductFirstLetter() {
        assertThat(deductFirstTorrentLetter("ivakisaka")).isEqualTo("I")
        assertThat(deductFirstTorrentLetter("Ivakisaka.2010.1080p")).isEqualTo("I")
    }

    @Test
    fun shouldIgnoreArticles() {
        assertThat(deductFirstTorrentLetter("The Dobi Kafide")).isEqualTo("D")
        assertThat(deductFirstTorrentLetter("the.matrix")).isEqualTo("M")
        assertThat(deductFirstTorrentLetter("A Bociborop Mifi")).isEqualTo("B")
        assertThat(deductFirstTorrentLetter("An Akegalez Wdofupin")).isEqualTo("A")
        assertThat(deductFirstTorrentLetter("Das Bibo")).isEqualTo("B")
    }

    @Test
    fun shouldNotRemoveArticleLikeWordStarts() {
        assertThat(deductFirstTorrentLetter("Topumu of Ecadubidig")).isEqualTo("T")
        assertThat(deductFirstTorrentLetter("Amemunaro")).isEqualTo("A")
    }

    @Test
    fun shouldMapDigitsToNumberFolder() {
        assertThat(deductFirstTorrentLetter("300 Rise of an Empire")).isEqualTo("0-9")
        assertThat(deductFirstTorrentLetter("12 Monkeys")).isEqualTo("0-9")
    }

    @Test
    fun shouldTakeNameInFrontOfYear() {
        assertThat(deductFirstTorrentLetter("Movie 2012")).isEqualTo("M")
    }

    @Test
    fun shouldMapUmlautsAndSpecialCharacters() {
        assertThat(deductFirstTorrentLetter("Ärger")).isEqualTo("A")
        assertThat(deductFirstTorrentLetter("Ödland")).isEqualTo("O")
        assertThat(deductFirstTorrentLetter("Über den Wtozen")).isEqualTo("U")
        assertThat(deductFirstTorrentLetter("über")).isEqualTo("U")
        assertThat(deductFirstTorrentLetter("\"Quoted\"")).isEqualTo("Q")
        assertThat(deductFirstTorrentLetter("#hashtag")).isEqualTo("?")
    }

    @Test
    fun shouldHandleEmptyName() {
        assertThat(deductFirstTorrentLetter("")).isEqualTo("")
        assertThat(deductFirstTorrentLetter("   ")).isEqualTo("")
    }

    @Test
    fun shouldUseNumberFolderForTitlesStartingWithAYear() {
        assertThat(deductFirstTorrentLetter("2001 A Sebaz Ocumaze")).isEqualTo("0-9")
        assertThat(deductFirstTorrentLetter("1917")).isEqualTo("0-9")
        assertThat(deductFirstTorrentLetter("Bubes Rutepe 2049")).isEqualTo("B")
    }
}
