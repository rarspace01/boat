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
        assertThat(html).contains("<form method=\"post\" action=\"./download\"")
    }

    @Test
    fun shouldDownloadWithAPostFormCarryingTheMagnetAndTheMedia() {
        val magnetUri = "magnet:?xt=urn:btih:" + "a".repeat(40)
        val html = Torrent("name").apply { this.magnetUri = magnetUri }.toHtml("{tmdb-1\"><b>}", boat.info.MediaType.Movie)

        val base64 = java.util.Base64.getUrlEncoder().encodeToString(magnetUri.toByteArray())
        assertThat(html).contains("<input type=\"hidden\" name=\"d\" value=\"$base64\">")
        assertThat(html).contains("<input type=\"hidden\" name=\"mid\" value=\"{tmdb-1&quot;&gt;&lt;b&gt;}\">")
        assertThat(html).contains("<input type=\"hidden\" name=\"mt\" value=\"Movie\">")
        assertThat(html).doesNotContain("href=\"./download")
    }

    @Test
    fun shouldEscapeTheSizeFromTheSearchSites() {
        val html = Torrent("name").apply { size = evil }.toHtml()

        assertThat(html).doesNotContain("<script>", "<img")
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
    fun shouldEscapeTheProgressOfTheHoster() {
        // the progress of Premiumize is the json value as it is, the info page shows it inside an html comment
        val torrent = Torrent("name").apply {
            remoteId = "1"
            remoteStatusText = "running"
            remoteProgress = evil
        }

        assertThat(torrent.toHtml()).doesNotContain("-->", "<img", "<script>")
    }

    @Test
    fun shouldEscapeTransferFieldsInHtml() {
        val html = Transfer(name = evil, feedbackMessage = evil, source = "<s>").toString()

        assertThat(html).doesNotContain("<script>", "<img", "<s>", "--><img")
        assertThat(html.substringAfter("-->").substringBefore("<!--")).contains("&lt;script&gt;")
    }
}
