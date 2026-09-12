package com.spybot.web.security

import com.spybot.core.model.MergedUserView
import com.spybot.core.security.MergedUserPrincipal
import com.spybot.core.service.AuthenticationService
import com.spybot.core.service.PasskeyQueries
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.web.webauthn.api.Bytes
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationRequestToken
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations

class MergedUserWebAuthnAuthenticationProviderTest {
    private val relyingParty = mock(WebAuthnRelyingPartyOperations::class.java)
    private val passkeyQueries = mock(PasskeyQueries::class.java)
    private val authenticationService = mock(AuthenticationService::class.java)
    private val provider = MergedUserWebAuthnAuthenticationProvider(relyingParty, passkeyQueries, authenticationService)
    private val request = mock(RelyingPartyAuthenticationRequest::class.java)
    private val token = WebAuthnAuthenticationRequestToken(request)
    private val handle = Bytes.random()

    private fun assertionResolvesTo(name: String) {
        `when`(relyingParty.authenticate(request)).thenReturn(
            ImmutablePublicKeyCredentialUserEntity
                .builder()
                .id(handle)
                .name(name)
                .displayName(name)
                .build(),
        )
    }

    @Test
    fun `a passkey login ends up as the app's own principal, resolved by handle rather than name`() {
        val user = MergedUserPrincipal(MergedUserView(id = 708, name = "Alice", obsolete = false, isSuperuser = true, lastLogin = null))
        assertionResolvesTo("Alice")
        `when`(passkeyQueries.findWebauthnUserIdByHandle(handle.toBase64UrlString())).thenReturn(708)
        `when`(authenticationService.loadPrincipal(708)).thenReturn(user)

        val result = provider.authenticate(token)!!

        assertTrue(result.isAuthenticated)
        assertEquals(user, result.principal)
        assertEquals(setOf("ROLE_USER", "ROLE_ADMIN"), result.authorities.map { it.authority }.toSet())
    }

    @Test
    fun `a passkey whose handle is unknown is rejected`() {
        assertionResolvesTo("Alice")
        `when`(passkeyQueries.findWebauthnUserIdByHandle(handle.toBase64UrlString())).thenReturn(null)

        assertThrows<BadCredentialsException> { provider.authenticate(token) }
    }

    @Test
    fun `a passkey whose user is gone is rejected`() {
        assertionResolvesTo("Alice")
        `when`(passkeyQueries.findWebauthnUserIdByHandle(handle.toBase64UrlString())).thenReturn(999)
        `when`(authenticationService.loadPrincipal(999)).thenReturn(null)

        assertThrows<BadCredentialsException> { provider.authenticate(token) }
    }

    @Test
    fun `only handles WebAuthn assertion tokens`() {
        assertTrue(provider.supports(WebAuthnAuthenticationRequestToken::class.java))
        assertNull(provider.authenticate(UsernamePasswordAuthenticationToken("x", "y")))
    }
}
