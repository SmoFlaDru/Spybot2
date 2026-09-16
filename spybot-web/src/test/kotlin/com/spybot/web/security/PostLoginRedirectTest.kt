package com.spybot.web.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import tools.jackson.databind.json.JsonMapper

class PostLoginRedirectTest {
    private val requestCache = HttpSessionRequestCache()
    private val postLoginRedirect = PostLoginRedirect(requestCache)
    private val request = MockHttpServletRequest()
    private val response = MockHttpServletResponse()

    /** Spring Security saving the request an anonymous visitor made before sending them to /login. */
    private fun visitorWasBouncedFrom(
        path: String,
        query: String? = null,
    ) {
        val original =
            MockHttpServletRequest("GET", path).apply {
                queryString = query
                session = request.session
            }
        requestCache.saveRequest(original, MockHttpServletResponse())
    }

    @Test
    fun `without a saved request the fallback is used`() {
        assertEquals("/profile", postLoginRedirect.consume(request, response, fallback = "/profile"))
    }

    @Test
    fun `the saved page is returned as a site-relative path and then forgotten`() {
        visitorWasBouncedFrom("/u/42")

        assertEquals("/u/42", postLoginRedirect.consume(request, response, fallback = "/"))
        assertNull(requestCache.getRequest(request, response))
        assertEquals("/", postLoginRedirect.consume(request, response, fallback = "/"))
    }

    @Test
    fun `the query string comes along, without Spring's continue marker`() {
        visitorWasBouncedFrom("/profile", query = "passkey-prompt")

        assertEquals("/profile?passkey-prompt", postLoginRedirect.consume(request, response, fallback = "/"))
    }

    @Test
    fun `the passkey success handler answers with the destination as JSON`() {
        visitorWasBouncedFrom("/u/42")

        PasskeyLoginSuccessHandler(postLoginRedirect).onAuthenticationSuccess(request, response, org.mockito.Mockito.mock())

        assertEquals(200, response.status)
        assertEquals("application/json", response.contentType)
        val body = JsonMapper.builder().build().readTree(response.contentAsString)
        assertEquals("/u/42", body["redirectUrl"].asString())
        assertEquals(true, body["authenticated"].asBoolean())
    }

    @Test
    fun `the passkey success handler falls back to the profile page`() {
        PasskeyLoginSuccessHandler(postLoginRedirect).onAuthenticationSuccess(request, response, org.mockito.Mockito.mock())

        val body = JsonMapper.builder().build().readTree(response.contentAsString)
        assertEquals("/profile", body["redirectUrl"].asString())
    }
}
