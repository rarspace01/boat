package boat.utilities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.InetAddress

internal class HttpHelperPublicUrlTest {

    @Test
    fun shouldRefuseLocalAndPrivateAddresses() {
        listOf(
            "http://localhost/list", "http://127.0.0.1:8080/", "http://[::1]/", "http://10.0.0.5/", "http://192.168.1.10/",
            "http://172.16.0.1/", "http://169.254.169.254/latest/meta-data/", "http://100.64.0.1/", "http://0.0.0.0/",
            "http://[fd00::1]/", "http://[fe80::1]/",
        ).forEach { assertThat(HttpHelper.isAllowedPublicUrl(it)).describedAs(it).isFalse() }
    }

    @Test
    fun shouldRefuseOtherSchemesAndBrokenUrls() {
        listOf("file:///etc/passwd", "ftp://example.org/list", "gopher://1.1.1.1/", "not a url", "http:///nohost").forEach {
            assertThat(HttpHelper.isAllowedPublicUrl(it)).describedAs(it).isFalse()
        }
    }

    @Test
    fun shouldAcceptPublicAddresses() {
        assertThat(HttpHelper.isAllowedPublicUrl("https://1.1.1.1/list.txt")).isTrue()
        assertThat(HttpHelper.isAllowedPublicUrl("http://[2606:4700:4700::1111]/")).isTrue()
        assertThat(HttpHelper.isPublicAddress(InetAddress.getByName("8.8.8.8"))).isTrue()
    }

    @Test
    fun shouldAllowPrivateAddressesIfConfigured() {
        assertThat(HttpHelper.isAllowedPublicUrl("http://192.168.1.10/list", allowPrivate = true)).isTrue()
        assertThat(HttpHelper.isAllowedPublicUrl("file:///etc/passwd", allowPrivate = true)).isFalse()
    }

    @Test
    fun shouldNotFetchPrivateUrls() {
        assertThat(HttpHelper().getPublicPage("http://127.0.0.1:1/list")).isNull()
    }
}
