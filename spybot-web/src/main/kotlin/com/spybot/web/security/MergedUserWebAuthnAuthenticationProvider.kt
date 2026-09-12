package com.spybot.web.security

import com.spybot.core.service.AuthenticationService
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationProvider

/**
 * Spring's [WebAuthnAuthenticationProvider] authenticates a passkey assertion and produces a
 * [WebAuthnAuthentication] whose principal is the WebAuthn user entity. The rest of the app
 * identifies the logged-in user by `@AuthenticationPrincipal MergedUserPrincipal`, so this wraps
 * the result into the same [Authentication] a magic-link login produces. The entity name is the
 * merged user id (see [WebauthnUserEntityRepository]).
 */
class MergedUserWebAuthnAuthenticationProvider(
    private val delegate: WebAuthnAuthenticationProvider,
    private val authenticationService: AuthenticationService,
) : AuthenticationProvider {
    override fun authenticate(authentication: Authentication): Authentication? {
        val result = delegate.authenticate(authentication) as? WebAuthnAuthentication ?: return null
        val userId = result.principal.name.toLongOrNull() ?: throw BadCredentialsException("Passkey is not linked to a user")
        val principal = authenticationService.loadPrincipal(userId) ?: throw BadCredentialsException("Passkey user no longer exists")
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities)
    }

    override fun supports(authentication: Class<*>): Boolean = delegate.supports(authentication)
}
