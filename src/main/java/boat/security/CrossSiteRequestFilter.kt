package boat.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import java.net.URI

/**
 * CSRF protection without tokens: the pages use HTTP Basic auth, which browsers attach to requests started by other
 * sites too. Browsers tell where a request comes from in `Sec-Fetch-Site`, so requests started by another site are
 * rejected. Requests of the page itself (`same-origin`) and clients without the header (curl, the deployment scripts)
 * pass. Typed urls and bookmarks (`none`) only pass for GET and HEAD: browsers send `none` as well for a link which a
 * native app (mail or chat client) hands over, so the endpoints changing state only take POST.
 *
 * Opening a link from another site or app (e.g. WhatsApp) is cross site as well, so the entry pages, which only show
 * forms or status, can be opened that way: a GET of the page itself, not loaded into a frame of the other site.
 * The search results (`/boat/query`) only read as well: Safari on iOS sends the search form of a page saved to the home
 * screen as `cross-site`, since its `target="_blank"` opens the results in Safari, which is another app.
 *
 * The download forms of `/boat/direct` and `/boat/searchList` open a new tab the same way, but they change state. Their
 * POST still names boat's page in `Origin`, which other sites can't set, so it passes when `Origin` is boat itself.
 * Only these two endpoints accept that, the others (delete, restart, ...) stay same origin only.
 */
class CrossSiteRequestFilter : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val site = request.getHeader("Sec-Fetch-Site")
        if (request.method != "OPTIONS" && site != null && !isAllowedSite(site, request.method) &&
            !isNavigationToEntryPage(request) && !isNewTabFormOfOwnPage(request)
        ) {
            logger.warn("rejected cross site request [${request.method} ${request.requestURI}] from [$site]")
            // written directly, sendError forwards to the error page, which needs a login, so the browser asked for the
            // login again instead of showing that the request was rejected
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = "text/plain;charset=UTF-8"
            response.writer.write("cross site requests are not allowed, open the page directly")
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun isAllowedSite(site: String, method: String): Boolean =
        site == SAME_ORIGIN || (site == USER_STARTED && method in SAFE_METHODS)

    private fun isNavigationToEntryPage(request: HttpServletRequest): Boolean {
        return request.method in SAFE_METHODS &&
                request.getHeader("Sec-Fetch-Mode") == "navigate" &&
                request.getHeader("Sec-Fetch-Dest") == "document" &&
                request.requestURI in ENTRY_PAGES
    }

    private fun isNewTabFormOfOwnPage(request: HttpServletRequest): Boolean =
        request.method == "POST" && request.requestURI in NEW_TAB_FORM_TARGETS && isOwnOrigin(request)

    /** `Origin` names boat as the browser addressed it (`Host`) or as the reverse proxy forwarded it. */
    private fun isOwnOrigin(request: HttpServletRequest): Boolean {
        val origin = request.getHeader("Origin")?.takeUnless { it.isBlank() || it == "null" } ?: return false
        val uri = runCatching { URI(origin) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (host == null || scheme !in setOf("http", "https")) return false
        val defaultPort = if (scheme == "https") 443 else 80
        val port = if (uri.port != -1) uri.port else defaultPort
        val ownHost = request.getHeader("Host")?.lowercase()
        return ownHost == "$host:$port" || (ownHost == host && port == defaultPort) ||
                (request.serverName.equals(host, ignoreCase = true) && request.serverPort == port)
    }

    companion object {
        private const val SAME_ORIGIN = "same-origin"
        // typed urls, bookmarks and links handed over by native apps
        private const val USER_STARTED = "none"
        private val SAFE_METHODS = setOf("GET", "HEAD")
        // read only pages (the search results included), everything changing state (e.g. /boat/download, /boat/admin/clearMediaCache) stays same origin only
        private val ENTRY_PAGES = setOf("/", "/search", "/boat/search", "/boat/query", "/boat/direct", "/boat/searchList", "/boat/info", "/boat/debug")
        // forms of boat's pages with target _blank, Safari on iOS sends them as cross site from a page on the home screen
        private val NEW_TAB_FORM_TARGETS = setOf("/boat/download", "/boat/queueList")
    }
}
