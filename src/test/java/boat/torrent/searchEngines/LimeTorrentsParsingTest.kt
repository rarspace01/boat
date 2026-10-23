package boat.torrent.searchEngines

import boat.utilities.HttpHelper
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class LimeTorrentsParsingTest {

    private fun row(hash: String, name: String, seeders: Int) = """
        <tr><td class="tdnormal"><div class="tt-name"><a href="http://itorrents.net/torrent/$hash.torrent?title=$name" class="csprite_dl14"></a>
        <a href="/$name-torrent-1.html">$name</a></div></td><td class="tdnormal">1 year</td><td class="tdnormal">1.5 GB</td>
        <td class="tdseed">$seeders</td><td class="tdleech">2</td></tr>"""

    @Test
    fun shouldReadTheHashesOfTheItorrentsNetLinks() {
        val page = """<html><body><table class="table2"><tr><th>head</th></tr>
            ${row("DBF3CFA1D2FAF83E136D2364718FFB9713731581", "The-Osugol-S09E08", 9)}
            ${row("6745CED23DA7895790BEB63F68DB0E6E38EB49B1", "The-Osugol-S05E15", 0)}
            </table></body></html>"""
        val httpHelper: HttpHelper = mockk()
        every { httpHelper.getPage(any()) } returnsMany listOf(page, "")

        val torrents = LimeTorrents(httpHelper).searchTorrents("the osugol")

        assertThat(torrents.map { it.torrentId }).containsExactly("dbf3cfa1d2faf83e136d2364718ffb9713731581", "6745ced23da7895790beb63f68db0e6e38eb49b1")
        assertThat(torrents.map { it.seeder }).containsExactly(9, 0)
    }
}
