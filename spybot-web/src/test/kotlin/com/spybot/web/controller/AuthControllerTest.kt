package com.spybot.web.controller

import com.spybot.core.model.MergedUserView
import com.spybot.core.model.PasskeyView
import com.spybot.core.security.MergedUserPrincipal
import com.spybot.core.service.AuthenticationService
import com.spybot.core.service.SpybotQueryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.OffsetDateTime

class AuthControllerTest {
    private val authenticationService = Mockito.mock(AuthenticationService::class.java)
    private val queryService = Mockito.mock(SpybotQueryService::class.java)
    private val controller = AuthController(authenticationService, queryService)
    private val principal = MergedUserPrincipal(MergedUserView(id = 708, name = "bensge", obsolete = false, isSuperuser = false, lastLogin = null))

    @Test
    fun `a magic-link login without any passkey lands on the profile with the passkey prompt`() {
        Mockito.`when`(authenticationService.authenticateByLoginCode("code")).thenReturn(principal)
        Mockito.`when`(queryService.passkeysForUser(708)).thenReturn(emptyList())

        val view = controller.linkAuth("code", MockHttpServletRequest(), MockHttpServletResponse())

        assertEquals("/profile?passkey-prompt", view.url)
    }

    @Test
    fun `a magic-link login with a passkey lands on the home page as before`() {
        Mockito.`when`(authenticationService.authenticateByLoginCode("code")).thenReturn(principal)
        Mockito.`when`(queryService.passkeysForUser(708)).thenReturn(listOf(PasskeyView(1, "Mac", "iCloud Keychain", OffsetDateTime.now(), null, true)))

        val view = controller.linkAuth("code", MockHttpServletRequest(), MockHttpServletResponse())

        assertEquals("/", view.url)
    }

    @Test
    fun `an invalid code just goes home`() {
        Mockito.`when`(authenticationService.authenticateByLoginCode("nope")).thenReturn(null)

        assertEquals("/", controller.linkAuth("nope", MockHttpServletRequest(), MockHttpServletResponse()).url)
    }
}
