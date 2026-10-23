package boat.torrent.searchEngines

import boat.utilities.HttpHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TorrentProjectParsingTest {

    private fun row(id: Int, seeders: Int) =
        "<div><span><a href='/t3-$id/Plonk-$id-torrent.html'>Plonk $id</a></span> <span>$seeders</span><span>2</span><span>1 year ago</span><span>1.1 GB</span></div>"

    private fun resultPage(rows: List<String>) =
        "<html><body><div id=\"similarfiles\">${rows.joinToString("")}</div></body></html>"

    private fun torrentPage(id: Int) =
        "<html><body><div id=\"download\"><a href=\"https://mylink.cx/?url=magnet%3A%3Fxt%3Durn%3Abtih%3A${"%040d".format(id)}\">magnet</a></div></body></html>"

    @Test
    fun shouldOnlyReadThePagesOfTheBestSeededTorrents() {
        val ids = (1..TorrentProject.MAX_DETAIL_PAGES + 5).toList()
        val httpHelper: HttpHelper = mockk()
        every { httpHelper.getPage(match { it.contains("/?t=") }) } returns resultPage(ids.map { row(it, it) })
        every { httpHelper.getPage(match { it.contains("/t3-") }) } answers {
            torrentPage(firstArg<String>().substringAfter("/t3-").substringBefore("/").toInt())
        }

        val torrents = TorrentProject(httpHelper).searchTorrents("plonk")

        assertThat(torrents).hasSize(ids.size)
        assertThat(torrents.filter { it.magnetUri.isNotEmpty() }.map { it.seeder })
            .containsExactlyInAnyOrderElementsOf(ids.takeLast(TorrentProject.MAX_DETAIL_PAGES))
        assertThat(torrents.map { it.remoteUrl }).allMatch { it.isEmpty() }
    }

    @Test
    fun shouldStopReadingTorrentPagesAfterTheFirstFailure() {
        val httpHelper: HttpHelper = mockk()
        every { httpHelper.getPage(match { it.contains("/?t=") }) } returns resultPage((1..5).map { row(it, 10) })
        every { httpHelper.getPage(match { it.contains("/t3-") }) } returns ""

        val torrents = TorrentProject(httpHelper).searchTorrents("plonk")

        assertThat(torrents).hasSize(5)
        assertThat(torrents.map { it.magnetUri }).allMatch { it.isEmpty() }
        verify(exactly = 1) { httpHelper.getPage(match { it.contains("/t3-") }) }
    }
}
