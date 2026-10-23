package boat.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

internal class CrossSiteRequestFilterTest {

    private fun call(
        method: String,
        secFetchSite: String?,
        path: String = "/boat/download",
        secFetchMode: String? = null,
        secFetchDest: String? = null
    ): Pair<MockHttpServletResponse, MockFilterChain> {
        val request = MockHttpServletRequest(method, path)
        secFetchSite?.let { request.addHeader("Sec-Fetch-Site", it) }
        secFetchMode?.let { request.addHeader("Sec-Fetch-Mode", it) }
        secFetchDest?.let { request.addHeader("Sec-Fetch-Dest", it) }
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
    fun shouldAnswerRejectedRequestsDirectly() {
        // not forwarded to the error page, which needs a login and so made the browser ask for the login again
        val (response, _) = call("GET", "cross-site")
        assertThat(response.status).isEqualTo(403)
        assertThat(response.errorMessage).isNull()
        assertThat(response.contentAsString).contains("cross site requests are not allowed")
    }

    @Test
    fun shouldAllowOwnPagesTypedUrlsAndClientsWithoutTheHeader() {
        listOf("same-origin", "none", null).forEach { site ->
            val (response, chain) = call("GET", site)
            assertThat(response.status).isEqualTo(200)
            assertThat(chain.request).isNotNull
        }
    }

    @Test
    fun shouldAllowOpeningEntryPagesFromLinksOfOtherSitesAndApps() {
        // e.g. a link to the search page sent via WhatsApp
        listOf("/", "/search", "/boat/search", "/boat/searchOld", "/boat/searchList", "/boat/info", "/boat/debug").forEach { path ->
            val (response, chain) = call("GET", "cross-site", path, "navigate", "document")
            assertThat(response.status).describedAs(path).isEqualTo(200)
            assertThat(chain.request).describedAs(path).isNotNull
        }
    }

    @Test
    fun shouldRejectCrossSiteNavigationsToEndpointsChangingState() {
        listOf("/boat/download", "/boat/query", "/boat/queryOld", "/boat/clearMediaCache", "/boat/shutdown").forEach { path ->
            assertThat(call("GET", "cross-site", path, "navigate", "document").first.status).describedAs(path).isEqualTo(403)
        }
    }

    @Test
    fun shouldRejectCrossSiteRequestsToEntryPagesWhichAreNoPageNavigation() {
        // a form posted by another site
        assertThat(call("POST", "cross-site", "/boat/search", "navigate", "document").first.status).isEqualTo(403)
        // loaded into a frame of another site
        assertThat(call("GET", "cross-site", "/boat/search", "navigate", "iframe").first.status).isEqualTo(403)
        // fetched by a script of another site
        assertThat(call("GET", "cross-site", "/boat/info", "cors", "empty").first.status).isEqualTo(403)
        assertThat(call("GET", "cross-site", "/boat/info").first.status).isEqualTo(403)
    }
}
