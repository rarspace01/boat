package boat.utilities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class HttpHelperLinkTest {

    @Test
    fun shouldResolveLinksOfTheSearchSite() {
        assertThat(HttpHelper.linkOnSameHost("https://www.site.example", "/t3-abc/name")).isEqualTo("https://www.site.example/t3-abc/name")
        assertThat(HttpHelper.linkOnSameHost("https://www.site.example", "t3-abc")).isEqualTo("https://www.site.example/t3-abc")
        assertThat(HttpHelper.linkOnSameHost("https://site.example", "https://site.example/torrent/1")).isEqualTo("https://site.example/torrent/1")
        assertThat(HttpHelper.linkOnSameHost("https://site.example", "https://www.site.example/torrent/1")).isEqualTo("https://www.site.example/torrent/1")
        // characters the sites don't escape
        assertThat(HttpHelper.linkOnSameHost("https://site.example", "/torrent/Name [ettv]")).isEqualTo("https://site.example/torrent/Name%20%5Bettv%5D")
    }

    @Test
    fun shouldNotFollowLinksToOtherHosts() {
        // baseUrl + "@10.0.0.1/admin" was the host 10.0.0.1, resolved it is a path of the site
        assertThat(HttpHelper.linkOnSameHost("https://site.example", "@10.0.0.1/admin")).isEqualTo("https://site.example/@10.0.0.1/admin")
        assertThat(HttpHelper.linkOnSameHost("https://site.example", "https://169.254.169.254/latest/meta-data")).isNull()
        assertThat(HttpHelper.linkOnSameHost("https://site.example", "//evil.example/x")).isNull()
        assertThat(HttpHelper.linkOnSameHost("https://site.example", "https://site.example.evil.example/x")).isNull()
        assertThat(HttpHelper.linkOnSameHost("https://site.example", "https://user@site.example/x")).isNull()
        assertThat(HttpHelper.linkOnSameHost("https://site.example", "file:///etc/passwd")).isNull()
    }
}
