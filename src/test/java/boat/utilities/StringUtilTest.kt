package boat.utilities

import boat.utilities.StringUtil.Companion.deductFirstTorrentLetter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class StringUtilTest {

    @Test
    fun shouldDeductFirstLetter() {
        assertThat(deductFirstTorrentLetter("inception")).isEqualTo("I")
        assertThat(deductFirstTorrentLetter("Inception.2010.1080p")).isEqualTo("I")
    }

    @Test
    fun shouldIgnoreArticles() {
        assertThat(deductFirstTorrentLetter("The Dark Knight")).isEqualTo("D")
        assertThat(deductFirstTorrentLetter("the.matrix")).isEqualTo("M")
        assertThat(deductFirstTorrentLetter("A Beautiful Mind")).isEqualTo("B")
        assertThat(deductFirstTorrentLetter("An American Werewolf")).isEqualTo("A")
        assertThat(deductFirstTorrentLetter("Das Boot")).isEqualTo("B")
    }

    @Test
    fun shouldNotRemoveArticleLikeWordStarts() {
        assertThat(deductFirstTorrentLetter("Theory of Everything")).isEqualTo("T")
        assertThat(deductFirstTorrentLetter("Anastasia")).isEqualTo("A")
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
        assertThat(deductFirstTorrentLetter("Über den Wolken")).isEqualTo("U")
        assertThat(deductFirstTorrentLetter("über")).isEqualTo("U")
        assertThat(deductFirstTorrentLetter("\"Quoted\"")).isEqualTo("Q")
        assertThat(deductFirstTorrentLetter("#hashtag")).isEqualTo("?")
    }

    @Test
    fun shouldHandleEmptyName() {
        assertThat(deductFirstTorrentLetter("")).isEqualTo("")
        assertThat(deductFirstTorrentLetter("   ")).isEqualTo("")
    }
}
