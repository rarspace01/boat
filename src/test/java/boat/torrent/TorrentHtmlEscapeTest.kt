package boat.torrent

import boat.model.Transfer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TorrentHtmlEscapeTest {

    private val evil = "<script>alert(1)</script>--><img src=x onerror=alert(2)>"

    @Test
    fun shouldEscapeTorrentNameInHtml() {
        val html = Torrent(evil).apply { magnetUri = "magnet:?xt=urn:btih:" + "a".repeat(40) }.toHtml()

        assertThat(html).doesNotContain("<script>", "<img")
        assertThat(html).contains("&lt;script&gt;", "--&gt;")
        assertThat(html).contains("<a href=\"./download?d=")
    }

    @Test
    fun shouldEscapeRemoteTorrentFields() {
        val torrent = Torrent("name").apply {
            remoteId = "<b>id</b>"
            remoteStatusText = "<i>status</i>"
            remoteProgress = "1"
        }

        assertThat(torrent.toHtml()).doesNotContain("<b>id", "<i>status")
    }

    @Test
    fun shouldEscapeTransferFieldsInHtml() {
        val html = Transfer(name = evil, feedbackMessage = evil, source = "<s>").toString()

        assertThat(html).doesNotContain("<script>", "<img", "<s>", "--><img")
        assertThat(html.substringAfter("-->").substringBefore("<!--")).contains("&lt;script&gt;")
    }
}
