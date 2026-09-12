package com.spybot.web.security

import com.spybot.core.model.MergedUserView
import com.spybot.core.security.MergedUserPrincipal
import com.spybot.core.service.AuthenticationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.web.webauthn.api.Bytes
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationProvider

class MergedUserWebAuthnAuthenticationProviderTest {
    private val delegate = mock(WebAuthnAuthenticationProvider::class.java)
    private val authenticationService = mock(AuthenticationService::class.java)
    private val provider = MergedUserWebAuthnAuthenticationProvider(delegate, authenticationService)
    private val request = mock(Authentication::class.java)

    private fun springResult(name: String) =
        WebAuthnAuthentication(
            ImmutablePublicKeyCredentialUserEntity.builder().id(Bytes.random()).name(name).displayName("Alice").build(),
            listOf(SimpleGrantedAuthority("ROLE_USER")),
        )

    @Test
    fun `a passkey login ends up as the app's own principal`() {
        val user = MergedUserPrincipal(MergedUserView(id = 708, name = "Alice", obsolete = false, isSuperuser = true, lastLogin = null))
        `when`(delegate.authenticate(any() ?: request)).thenReturn(springResult("708"))
        `when`(authenticationService.loadPrincipal(708)).thenReturn(user)

        val result = provider.authenticate(request)!!

        assertTrue(result.isAuthenticated)
        assertEquals(user, result.principal)
        assertEquals(setOf("ROLE_USER", "ROLE_ADMIN"), result.authorities.map { it.authority }.toSet())
    }

    @Test
    fun `a passkey whose user is gone is rejected`() {
        `when`(delegate.authenticate(any() ?: request)).thenReturn(springResult("999"))
        `when`(authenticationService.loadPrincipal(999)).thenReturn(null)

        assertThrows<BadCredentialsException> { provider.authenticate(request) }
    }
}
