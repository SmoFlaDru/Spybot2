package com.spybot.web.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import tools.jackson.databind.json.JsonMapper

/**
 * Answers a successful `POST /login/webauthn` the way Spring's default handler does -
 * `{"redirectUrl": ..., "authenticated": true}`, which `passkeys.js` follows - but with the
 * redirect taken from [PostLoginRedirect], so a login that started on a protected page returns
 * there. Without a pending destination the profile page is the landing page, as before.
 */
class PasskeyLoginSuccessHandler(
    private val postLoginRedirect: PostLoginRedirect,
) : AuthenticationSuccessHandler {
    private val json = JsonMapper.builder().build()

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val redirectUrl = postLoginRedirect.consume(request, response, fallback = DEFAULT_LANDING_PAGE)
        response.status = HttpServletResponse.SC_OK
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(json.writeValueAsString(mapOf("redirectUrl" to redirectUrl, "authenticated" to true)))
    }

    companion object {
        const val DEFAULT_LANDING_PAGE = "/profile"
    }
}
