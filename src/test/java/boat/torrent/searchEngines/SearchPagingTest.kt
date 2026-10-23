package boat.torrent.searchEngines

import boat.torrent.Torrent
import boat.utilities.HttpHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SearchPagingTest {

    private fun hash(index: Int) = String.format("%040x", index.toLong() + 1)

    private fun torrent(index: Int, seeders: Int = 10) = Torrent("T$index").apply {
        magnetUri = "magnet:?xt=urn:btih:" + hash(index)
        seeder = seeders
    }

    @Test
    fun shouldFetchPagesUntilThereIsNothingNew() {
        val requested = mutableListOf<Int>()
        val result = SearchPaging.collect(maxResults = 1000) { page ->
            requested.add(page)
            if (page <= 3) (0 until 5).map { torrent((page - 1) * 5 + it) } else emptyList()
        }

        assertThat(result).hasSize(15)
        assertThat(requested).containsExactly(1, 2, 3, 4)
    }

    @Test
    fun shouldStopWhenAPageOnlyRepeatsItself() {
        val requested = mutableListOf<Int>()
        val result = SearchPaging.collect(maxResults = 1000) { page ->
            requested.add(page)
            (0 until 5).map { torrent(it) }
        }

        assertThat(result).hasSize(5)
        assertThat(requested).containsExactly(1, 2)
    }

    @Test
    fun shouldStopAtTheMaximumAndNotReturnMore() {
        val result = SearchPaging.collect(maxResults = 12) { page -> (0 until 5).map { torrent((page - 1) * 5 + it) } }

        assertThat(result).hasSize(12)
    }

    @Test
    fun shouldStopWhenTheTorrentsGotToDeadOnes() {
        val requested = mutableListOf<Int>()
        val result = SearchPaging.collect(maxResults = 1000) { page ->
            requested.add(page)
            (0 until 5).map { torrent((page - 1) * 5 + it, seeders = if (page == 2 && it >= 3) 0 else 10) }
        }

        assertThat(requested).containsExactly(1, 2)
        assertThat(result).hasSize(10)
    }

    // ---- the engines

    private fun knabenPage(from: Int, count: Int, lastSeeders: Int = 5): String =
        "{\"hits\":[" + (0 until count).joinToString(",") { i ->
            val seeders = if (i == count - 1) lastSeeders else 50
            "{\"title\":\"Daca.${from + i}\",\"magnetUrl\":\"magnet:?xt=urn:btih:${hash(from + i)}\",\"seeders\":$seeders,\"peers\":1,\"bytes\":1048576000}"
        } + "]}"

    @Test
    fun knabenShouldPageThroughTheHitsWith300ARequest() {
        val httpHelper: HttpHelper = mockk()
        val offsets = mutableListOf<Int>()
        every { httpHelper.getPage(any(), anyNullable<String>(), anyNullable<Map<String, String>>()) } answers {
            val body = com.google.gson.JsonParser.parseString(secondArg<String>()).asJsonObject
            val from = body["from"].asInt
            assertThat(body["size"].asInt).isEqualTo(300)
            offsets.add(from)
            knabenPage(from, if (from < 600) 300 else 100)
        }

        val torrents = Knaben(httpHelper).searchTorrents("Daca")

        // 600 hits are more than the 500 which are kept per engine
        assertThat(offsets).containsExactly(0, 300)
        assertThat(torrents).hasSize(500)
    }

    @Test
    fun knabenShouldNotAskForMoreOnceTheTorrentsHaveNoSeeders() {
        val httpHelper: HttpHelper = mockk()
        val offsets = mutableListOf<Int>()
        every { httpHelper.getPage(any(), anyNullable<String>(), anyNullable<Map<String, String>>()) } answers {
            val from = com.google.gson.JsonParser.parseString(secondArg<String>()).asJsonObject["from"].asInt
            offsets.add(from)
            knabenPage(from, 300, lastSeeders = 0)
        }

        val torrents = Knaben(httpHelper).searchTorrents("Daca")

        assertThat(offsets).containsExactly(0)
        assertThat(torrents).hasSize(300)
    }

    @Test
    fun torrentsCsvShouldFollowTheNextCursor() {
        val httpHelper: HttpHelper = mockk()
        fun page(start: Int, next: String?) = "{\"torrents\":[" + (0 until 25).joinToString(",") { i ->
            "{\"infohash\":\"${hash(start + i)}\",\"name\":\"Ubuntu $i\",\"size_bytes\":1048576000,\"created_unix\":1,\"seeders\":5,\"leechers\":1}"
        } + "]," + (if (next != null) "\"next\":$next" else "\"next\":null") + "}"
        every { httpHelper.getPage(match { !it.contains("after=") }) } returns page(0, "111")
        every { httpHelper.getPage(match { it.contains("after=111") }) } returns page(25, "222")
        every { httpHelper.getPage(match { it.contains("after=222") }) } returns page(50, null)

        val torrents = TorrentsCSV(httpHelper).searchTorrents("ubuntu")

        assertThat(torrents).hasSize(75)
        verify(exactly = 3) { httpHelper.getPage(any()) }
    }

    @Test
    fun enginesShouldNotFilterOrSortTheirResultsAnymore() {
        val httpHelper: HttpHelper = mockk()
        // a CAM release and a torrent without seeders: the service decides what is shown
        every { httpHelper.getPage(any(), anyNullable<String>(), anyNullable<Map<String, String>>()) } returns
                "{\"hits\":[" +
                "{\"title\":\"Daca 2021 HDCAM\",\"magnetUrl\":\"magnet:?xt=urn:btih:${hash(1)}\",\"seeders\":0,\"peers\":0,\"bytes\":1048576000}," +
                "{\"title\":\"Daca 2021 1080p\",\"magnetUrl\":\"magnet:?xt=urn:btih:${hash(2)}\",\"seeders\":0,\"peers\":0,\"bytes\":1048576000}" +
                "]}"

        val torrents = Knaben(httpHelper).searchTorrents("Daca 2021")

        assertThat(torrents.map { it.name }).containsExactly("Daca 2021 HDCAM", "Daca 2021 1080p")
    }
}
