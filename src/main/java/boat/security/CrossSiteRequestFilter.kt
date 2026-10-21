package boat.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

/**
 * CSRF protection without tokens: the pages use HTTP Basic auth, which browsers attach to requests started by other
 * sites too, and state-changing endpoints accept GET (`/boat/download`). Browsers tell where a request comes from in
 * `Sec-Fetch-Site`, so requests started by another site are rejected. Requests of the page itself (`same-origin`),
 * typed urls and bookmarks (`none`) and clients without the header (curl, the deployment scripts) pass.
 */
class CrossSiteRequestFilter : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val site = request.getHeader("Sec-Fetch-Site")
        if (request.method != "OPTIONS" && site != null && site !in ALLOWED_SITES) {
            logger.warn("rejected cross site request [${request.method} ${request.requestURI}] from [$site]")
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "cross site requests are not allowed")
            return
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        private val ALLOWED_SITES = setOf("same-origin", "none")
    }
}
