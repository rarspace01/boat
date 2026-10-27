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
    fun shouldRefuseReservedAndEmbeddedLocalAddresses() {
        listOf(
            "192.0.0.1", "198.18.0.1", "198.19.255.255", "240.0.0.1", "255.255.255.255",
            // IPv6 addresses carrying a local IPv4 address: NAT64, 6to4, IPv4 compatible
            "64:ff9b::7f00:1", "64:ff9b::a9fe:a9fe", "64:ff9b:1::1", "2002:7f00:1::", "2002:c0a8:101::1", "::127.0.0.1",
            "2001:db8::1",
        ).forEach { assertThat(HttpHelper.isPublicAddress(InetAddress.getByName(it))).describedAs(it).isFalse() }
    }

    @Test
    fun shouldAcceptPublicAddressesInsideIpv6Ones() {
        listOf("64:ff9b::808:808", "2002:808:808::1", "2606:4700::1111", "198.20.0.1", "192.0.1.1")
            .forEach { assertThat(HttpHelper.isPublicAddress(InetAddress.getByName(it))).describedAs(it).isTrue() }
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
