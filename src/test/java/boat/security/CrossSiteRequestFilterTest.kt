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
        secFetchDest: String? = null,
        origin: String? = null
    ): Pair<MockHttpServletResponse, MockFilterChain> {
        val request = MockHttpServletRequest(method, path)
        secFetchSite?.let { request.addHeader("Sec-Fetch-Site", it) }
        secFetchMode?.let { request.addHeader("Sec-Fetch-Mode", it) }
        secFetchDest?.let { request.addHeader("Sec-Fetch-Dest", it) }
        request.serverName = "boat.example"
        request.serverPort = 443
        request.addHeader("Host", "boat.example")
        origin?.let { request.addHeader("Origin", it) }
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
    fun shouldOnlyAllowReadingForRequestsWithoutAPageWhichStartedThem() {
        // "none" is also a link a native mail or chat app hands to the browser, it must not change anything
        listOf("POST", "PUT", "DELETE", "PATCH").forEach { method ->
            val (response, chain) = call(method, "none")
            assertThat(response.status).describedAs(method).isEqualTo(403)
            assertThat(chain.request).describedAs(method).isNull()
        }
        assertThat(call("HEAD", "none").first.status).isEqualTo(200)
        assertThat(call("POST", "same-origin").first.status).isEqualTo(200)
        assertThat(call("POST", null).first.status).isEqualTo(200)
    }

    @Test
    fun shouldAllowOpeningEntryPagesFromLinksOfOtherSitesAndApps() {
        // e.g. a link to the search page sent via WhatsApp
        listOf("/", "/search", "/boat/search", "/boat/query", "/boat/direct", "/boat/searchList", "/boat/info", "/boat/debug").forEach { path ->
            val (response, chain) = call("GET", "cross-site", path, "navigate", "document")
            assertThat(response.status).describedAs(path).isEqualTo(200)
            assertThat(chain.request).describedAs(path).isNotNull
        }
    }

    @Test
    fun shouldAllowSearchFromPageSavedToIosHomeScreen() {
        // the search form opens the results in Safari, which sends it as cross-site
        val (response, chain) = call("GET", "cross-site", "/boat/query", "navigate", "document")
        assertThat(response.status).isEqualTo(200)
        assertThat(chain.request).isNotNull
    }

    @Test
    fun shouldRejectCrossSiteNavigationsToEndpointsChangingState() {
        listOf("/boat/download", "/boat/queueList", "/boat/transferDelete", "/boat/transferRestart", "/boat/admin/clearMediaCache", "/boat/shutdown").forEach { path ->
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

    @Test
    fun shouldAllowNewTabDownloadFormsOfOwnPageFromIosHomeScreen() {
        // the forms of /boat/direct and /boat/searchList open a new tab, which is Safari and sends them as cross site
        listOf("/boat/download", "/boat/queueList").forEach { path ->
            listOf("https://boat.example", "https://boat.example:443", "https://BOAT.example").forEach { origin ->
                val (response, chain) = call("POST", "cross-site", path, "navigate", "document", origin)
                assertThat(response.status).describedAs("$path $origin").isEqualTo(200)
                assertThat(chain.request).describedAs("$path $origin").isNotNull
            }
        }
    }

    @Test
    fun shouldRejectCrossSitePostsOfOtherOriginsOrToOtherEndpoints() {
        listOf(null, "null", "", "https://evil.example", "https://boat.example.evil.example", "https://boat.example:8443", "not a url")
            .forEach { origin ->
                val (response, chain) = call("POST", "cross-site", "/boat/download", "navigate", "document", origin)
                assertThat(response.status).describedAs(origin).isEqualTo(403)
                assertThat(chain.request).describedAs(origin).isNull()
            }
        // only the two download forms open a new tab, deleting and the like stay same origin only
        listOf("/boat/transferDelete", "/boat/transferRestart", "/boat/admin/clearMediaCache", "/boat/shutdown").forEach { path ->
            assertThat(call("POST", "cross-site", path, "navigate", "document", "https://boat.example").first.status).describedAs(path).isEqualTo(403)
        }
        // a GET to them isn't a form of boat's pages
        assertThat(call("GET", "cross-site", "/boat/download", "navigate", "document", "https://boat.example").first.status).isEqualTo(403)
    }
}
