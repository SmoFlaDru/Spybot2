package com.spybot.web.filter

import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class VisitorIdFilterTest {
    private val filter = VisitorIdFilter()

    private fun run(request: MockHttpServletRequest): MockHttpServletResponse {
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, MockFilterChain())
        return response
    }

    @Test
    fun `first visit to the generator gets a long-lived visitor cookie`() {
        val request = MockHttpServletRequest("GET", "/namegen")

        val response = run(request)

        val cookie = response.getCookie(VisitorIdFilter.COOKIE_NAME)
        assertNotNull(cookie)
        assertEquals(32, cookie!!.value.length)
        assertTrue(cookie.isHttpOnly)
        assertTrue(cookie.maxAge >= 365 * 24 * 60 * 60)
        assertEquals("Lax", cookie.getAttribute("SameSite"))
        assertEquals(cookie.value, VisitorIdFilter.visitorId(request))
    }

    @Test
    fun `a returning visitor keeps their id and gets no new cookie`() {
        val request = MockHttpServletRequest("POST", "/namegen/like")
        request.setCookies(Cookie(VisitorIdFilter.COOKIE_NAME, "0123456789abcdef0123456789abcdef"))

        val response = run(request)

        assertNull(response.getCookie(VisitorIdFilter.COOKIE_NAME))
        assertEquals("0123456789abcdef0123456789abcdef", VisitorIdFilter.visitorId(request))
    }

    @Test
    fun `a tampered cookie is replaced rather than trusted`() {
        val request = MockHttpServletRequest("GET", "/namegen")
        request.setCookies(Cookie(VisitorIdFilter.COOKIE_NAME, "not-a-visitor-id"))

        val response = run(request)

        val cookie = response.getCookie(VisitorIdFilter.COOKIE_NAME)
        assertNotNull(cookie)
        assertTrue(Regex("[0-9a-f]{32}").matches(cookie!!.value))
    }

    @Test
    fun `pages outside the generator are left alone`() {
        val request = MockHttpServletRequest("GET", "/halloffame")

        val response = run(request)

        assertNull(response.getCookie(VisitorIdFilter.COOKIE_NAME))
        assertNull(request.getAttribute(VisitorIdFilter.ATTRIBUTE))
    }
}
