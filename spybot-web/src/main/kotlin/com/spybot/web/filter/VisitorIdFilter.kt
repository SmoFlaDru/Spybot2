package com.spybot.web.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Gives anonymous visitors a stable identity for the name generator's likes: a random id in a
 * long-lived cookie, set on first visit to /namegen and exposed to controllers as a request
 * attribute. It is deliberately scoped to the generator pages rather than the whole site, and it
 * identifies a browser, not a person - clearing cookies starts over. That is fine for likes.
 */
@Component
class VisitorIdFilter : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !request.requestURI.startsWith("/namegen")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val existing =
            request.cookies
                ?.firstOrNull { it.name == COOKIE_NAME }
                ?.value
                ?.takeIf { VALID_ID.matches(it) }
        val visitorId = existing ?: newVisitorId().also { response.addCookie(cookie(it, request.isSecure)) }
        request.setAttribute(ATTRIBUTE, visitorId)
        filterChain.doFilter(request, response)
    }

    private fun cookie(
        visitorId: String,
        secure: Boolean,
    ): Cookie =
        Cookie(COOKIE_NAME, visitorId).apply {
            path = "/"
            maxAge = MAX_AGE_SECONDS
            isHttpOnly = true
            this.secure = secure
            setAttribute("SameSite", "Lax")
        }

    companion object {
        const val COOKIE_NAME = "spybot_visitor"
        const val ATTRIBUTE = "spybot.visitorId"
        private const val MAX_AGE_SECONDS = 365 * 24 * 60 * 60
        private val VALID_ID = Regex("[0-9a-f]{32}")

        fun newVisitorId(): String = UUID.randomUUID().toString().replace("-", "")

        /** The visitor id the filter attached to this request; only valid on /namegen routes. */
        fun visitorId(request: HttpServletRequest): String =
            request.getAttribute(ATTRIBUTE) as? String ?: error("VisitorIdFilter did not run for ${request.requestURI}")
    }
}
