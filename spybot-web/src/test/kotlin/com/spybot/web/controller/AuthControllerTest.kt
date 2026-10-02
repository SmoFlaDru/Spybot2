package com.spybot.web.controller

import com.spybot.core.model.MergedUserView
import com.spybot.core.model.PasskeyView
import com.spybot.core.security.MergedUserPrincipal
import com.spybot.core.service.AuthenticationService
import com.spybot.core.service.PasskeyQueries
import com.spybot.web.security.PostLoginRedirect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import java.time.OffsetDateTime

class AuthControllerTest {
    private val authenticationService = Mockito.mock(AuthenticationService::class.java)
    private val passkeyQueries = Mockito.mock(PasskeyQueries::class.java)
    private val requestCache = HttpSessionRequestCache()
    private val controller = AuthController(authenticationService, passkeyQueries, PostLoginRedirect(requestCache))
    private val principal =
        MergedUserPrincipal(MergedUserView(id = 708, name = "bensge", obsolete = false, isSuperuser = false, lastLogin = null))

    @Test
    fun `a magic-link login without any passkey lands on the profile with the passkey prompt`() {
        Mockito.`when`(authenticationService.authenticateByLoginCode("code")).thenReturn(principal)
        Mockito.`when`(passkeyQueries.passkeysForUser(708)).thenReturn(emptyList())

        val view = controller.linkAuth("code", MockHttpServletRequest(), MockHttpServletResponse())

        assertEquals("/profile?passkey-prompt", view.url)
    }

    @Test
    fun `a magic-link login with a passkey lands on the home page as before`() {
        Mockito.`when`(authenticationService.authenticateByLoginCode("code")).thenReturn(principal)
        Mockito
            .`when`(
                passkeyQueries.passkeysForUser(708),
            ).thenReturn(listOf(PasskeyView(1, "Mac", "iCloud Keychain", OffsetDateTime.now(), null, true)))

        val view = controller.linkAuth("code", MockHttpServletRequest(), MockHttpServletResponse())

        assertEquals("/", view.url)
    }

    @Test
    fun `an invalid code just goes home`() {
        Mockito.`when`(authenticationService.authenticateByLoginCode("nope")).thenReturn(null)

        assertEquals("/", controller.linkAuth("nope", MockHttpServletRequest(), MockHttpServletResponse()).url)
    }

    @Test
    fun `a magic-link login returns to the protected page the visitor was sent to log in from`() {
        Mockito.`when`(authenticationService.authenticateByLoginCode("code")).thenReturn(principal)
        Mockito.`when`(passkeyQueries.passkeysForUser(708)).thenReturn(emptyList())
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        // What Spring Security does when an anonymous visitor opens /u/42 from the Hall of Fame.
        requestCache.saveRequest(MockHttpServletRequest("GET", "/u/42").apply { session = request.session }, response)

        val view = controller.linkAuth("code", request, response)

        // The page they wanted wins over the passkey nudge, and the destination is used up.
        assertEquals("/u/42", view.url)
        assertEquals(null, requestCache.getRequest(request, response))
    }
}
