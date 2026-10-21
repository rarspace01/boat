package boat.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

internal class CrossSiteRequestFilterTest {

    private fun call(method: String, secFetchSite: String?): Pair<MockHttpServletResponse, MockFilterChain> {
        val request = MockHttpServletRequest(method, "/boat/download")
        secFetchSite?.let { request.addHeader("Sec-Fetch-Site", it) }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        CrossSiteRequestFilter().doFilter(request, response, chain)
        return response to chain
    }

    @Test
    fun shouldRejectRequestsStartedByOtherSites() {
        listOf("cross-site", "same-site").forEach { site ->
            val (response, chain) = call("GET", site)
            assertThat(response.status).isEqualTo(403)
            assertThat(chain.request).isNull()
        }
        assertThat(call("POST", "cross-site").first.status).isEqualTo(403)
    }

    @Test
    fun shouldAllowOwnPagesTypedUrlsAndClientsWithoutTheHeader() {
        listOf("same-origin", "none", null).forEach { site ->
            val (response, chain) = call("GET", site)
            assertThat(response.status).isEqualTo(200)
            assertThat(chain.request).isNotNull
        }
    }
}
