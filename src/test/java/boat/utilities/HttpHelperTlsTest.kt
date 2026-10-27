package boat.utilities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class HttpHelperTlsTest {

    @Test
    fun shouldNeverFallBackToUnverifiedTlsForHostersMetadataAndGithub() {
        listOf(
            "https://www.premiumize.me/api/transfer/list",
            "https://api.alldebrid.com/v4/magnet/status",
            "https://api.themoviedb.org/3/search/multi",
            "https://api4.thetvdb.com/v4/login",
            "https://api.github.com/repos/rarspace01/boat/releases/latest",
            "https://raw.githubusercontent.com/x/y/z",
        ).forEach { url ->
            // not even if they are in the setting
            val host = java.net.URI.create(url).host
            assertThat(HttpHelper.mayFallBackToUnverifiedTls(url, setOf(host))).describedAs(url).isFalse()
        }
    }

    @Test
    fun shouldNeverFallBackToUnverifiedTlsForUrlsWithApiKey() {
        assertThat(HttpHelper.mayFallBackToUnverifiedTls("https://example.org/api?apikey=secret", setOf("example.org"))).isFalse()
        assertThat(HttpHelper.mayFallBackToUnverifiedTls("https://example.org/api?API_KEY=secret", setOf("example.org"))).isFalse()
    }

    @Test
    fun shouldAllowFallbackForTorrentSites() {
        assertThat(HttpHelper.mayFallBackToUnverifiedTls("https://1337x.to/search/daca/1/", setOf("1337x.to"))).isTrue()
        assertThat(HttpHelper.mayFallBackToUnverifiedTls("https://notpremiumize.me.example.org/", setOf("notpremiumize.me.example.org"))).isTrue()
    }

    @Test
    fun shouldNotFallBackForSitesWhichAreNotInTheSetting() {
        // someone on the way could make the verification fail for any site and then show their own certificate
        assertThat(HttpHelper.mayFallBackToUnverifiedTls("https://1337x.to/search/daca/1/", emptySet())).isFalse()
        assertThat(HttpHelper.mayFallBackToUnverifiedTls("https://1337x.to/search/daca/1/", setOf("other.example"))).isFalse()
        // the offline tests have no UNVERIFIED_TLS_HOSTS
        assertThat(HttpHelper.mayFallBackToUnverifiedTls("https://1337x.to/search/daca/1/")).isFalse()
    }

    @Test
    fun shouldRedactApiKeysAndPasswords() {
        assertThat(HttpHelper.redactSecrets("Server returned HTTP response code: 500 for URL: https://x.me/api?id=1&apikey=abc123&q=a"))
            .isEqualTo("Server returned HTTP response code: 500 for URL: https://x.me/api?id=1&apikey=***&q=a")
        assertThat(HttpHelper.redactSecrets("https://x/?api_key=abc&token=def")).isEqualTo("https://x/?api_key=***&token=***")
        assertThat(HttpHelper.redactSecrets(null)).isNull()
    }
}
