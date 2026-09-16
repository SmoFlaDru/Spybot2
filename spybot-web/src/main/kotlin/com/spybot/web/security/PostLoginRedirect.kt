package com.spybot.web.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.web.savedrequest.DefaultSavedRequest
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.security.web.savedrequest.RequestCache
import org.springframework.stereotype.Component

/**
 * Where to send someone once they have logged in.
 *
 * When an anonymous visitor opens a protected page (a user page from the Hall of Fame, say),
 * Spring Security remembers that request in the session before bouncing them to `/login`. Both
 * login flows - the passkey assertion and the TeamSpeak magic link - end here so that they take
 * the visitor back to that page instead of to a fixed landing page.
 *
 * The saved request is consumed on the first call, so a destination is honoured exactly once.
 */
@Component
class PostLoginRedirect(
    // Reads the same session attribute the security filter chain's default request cache writes.
    private val requestCache: RequestCache = HttpSessionRequestCache(),
) {
    /**
     * The page the visitor was on their way to, as a site-relative path (with its query string),
     * or [fallback] if none was saved. Deliberately not Spring's own `SavedRequest.redirectUrl`:
     * that one is absolute and rebuilt from the request's scheme and host, which are wrong behind
     * the production proxy (TLS ends before Caddy), and it carries a `?continue` marker for
     * Spring's request-replay filter that the app has no use for.
     */
    fun consume(
        request: HttpServletRequest,
        response: HttpServletResponse,
        fallback: String,
    ): String {
        val saved = requestCache.getRequest(request, response) as? DefaultSavedRequest ?: return fallback
        requestCache.removeRequest(request, response)
        val path = saved.requestURI ?: return fallback
        // Only a site-relative path is ever a valid destination; "//host" would be an open redirect.
        if (!path.startsWith("/") || path.startsWith("//")) return fallback
        val query = saved.queryString
        return if (query.isNullOrEmpty()) path else "$path?$query"
    }
}
