package boat.torrent.searchEngines

import boat.utilities.HttpHelper
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SearchHitsTest {

    @Test
    fun shouldSkipAMalformedHitAndKeepTheOthers() {
        val parsed = SearchHits.parseEach("engine", listOf("1", "x", "3")) { it.toInt() }

        assertThat(parsed).containsExactly(1, 3)
    }

    @Test
    fun shouldKeepTheOtherHitsOfAPageWithAMalformedOne() {
        val httpHelper: HttpHelper = mockk()
        fun good(hash: Char) = """{"title":"Daca 2021","hash":"${hash.toString().repeat(40)}","seeders":5,"peers":1,"bytes":1073741824}"""
        // no seeders
        val malformed = """{"title":"Broken","hash":"1123456789abcdef0123456789abcdef01234567","peers":1,"bytes":1}"""
        every { httpHelper.getPage(any(), any<String>(), any()) } returns """{"hits":[${good('a')},$malformed,${good('b')}]}""" andThen """{"hits":[]}"""

        val torrents = Knaben(httpHelper).searchTorrents("daca")

        assertThat(torrents).hasSize(2).allMatch { it.name == "Daca 2021" }
    }
}
