package boat.info

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class NetflixHistoryParsingTest {
    @Test
    fun shouldParseDistinctTitlesOfViewingHistory() {
        val csv = "Title,Date\n\"Dune\",\"10/21/26\"\n\"Star Wars: A New Hope\",\"10/18/26\"\n" +
                "\"Matlock: Season 1: Pregame\",\"10/12/26\"\n\"Dune\",\"1/1/20\"\n\"The \"\"Best\"\" Man\",\"1/2/20\"\n"
        assertThat(NetflixHistoryImportTest.parseTitles(csv))
            .containsExactly("Dune", "Star Wars: A New Hope", "Matlock: Season 1: Pregame", "The \"Best\" Man")
    }
}
