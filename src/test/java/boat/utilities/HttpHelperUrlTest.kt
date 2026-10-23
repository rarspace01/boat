package boat.utilities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class HttpHelperUrlTest {

    @Test
    fun shouldEscapeWhatAUrlMustNotContain() {
        assertThat(HttpHelper.toUri("https://torrentproject.cc/t0-35052878/Botu.Bafira.S10E09.HDTV.x264-SVA[ettv]-torrent.html").toString())
            .isEqualTo("https://torrentproject.cc/t0-35052878/Botu.Bafira.S10E09.HDTV.x264-SVA%5Bettv%5D-torrent.html")
        assertThat(HttpHelper.toUri("https://example.org/a b/Acúzuf").toString()).isEqualTo("https://example.org/a%20b/Ac%C3%BAzuf")
        assertThat(HttpHelper.toUri("https://torrentproject.cc/t0-1/x").host).isEqualTo("torrentproject.cc")
    }

    @Test
    fun shouldKeepAValidUrlAsItIs() {
        val url = "https://api.example.org/search?q=daca%202021&apikey=abc#top"
        assertThat(HttpHelper.toUri(url).toString()).isEqualTo(url)
    }
}
