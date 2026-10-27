package boat.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

/**
 * CSRF protection without tokens: the pages use HTTP Basic auth, which browsers attach to requests started by other
 * sites too. Browsers tell where a request comes from in `Sec-Fetch-Site`, so requests started by another site are
 * rejected. Requests of the page itself (`same-origin`) and clients without the header (curl, the deployment scripts)
 * pass. Typed urls and bookmarks (`none`) only pass for GET and HEAD: browsers send `none` as well for a link which a
 * native app (mail or chat client) hands over, so the endpoints changing state only take POST.
 *
 * Opening a link from another site or app (e.g. WhatsApp) is cross site as well, so the entry pages, which only show
 * forms or status, can be opened that way: a GET of the page itself, not loaded into a frame of the other site.
 */
class CrossSiteRequestFilter : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val site = request.getHeader("Sec-Fetch-Site")
        if (request.method != "OPTIONS" && site != null && !isAllowedSite(site, request.method) && !isNavigationToEntryPage(request)) {
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

    companion object {
        private const val SAME_ORIGIN = "same-origin"
        // typed urls, bookmarks and links handed over by native apps
        private const val USER_STARTED = "none"
        private val SAFE_METHODS = setOf("GET", "HEAD")
        // read only pages, everything changing state (e.g. /boat/download, /boat/clearMediaCache) stays same origin only
        private val ENTRY_PAGES = setOf("/", "/search", "/boat/search", "/boat/searchOld", "/boat/searchList", "/boat/info", "/boat/debug")
    }
}
