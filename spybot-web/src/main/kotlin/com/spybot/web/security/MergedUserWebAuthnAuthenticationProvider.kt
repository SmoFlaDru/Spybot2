package com.spybot.web.security

import com.spybot.core.service.AuthenticationService
import com.spybot.core.service.PasskeyQueries
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationRequestToken
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations

/**
 * Authenticates a passkey assertion and produces the same [Authentication] a magic-link login
 * does, with the app's `MergedUserPrincipal` as principal.
 *
 * This replaces Spring's `WebAuthnAuthenticationProvider`, which looks the account up by the
 * user entity's *name* through the UserDetailsService. That would force the name - the
 * "username" password managers show next to a passkey - to be the numeric user id. Resolving
 * the account by the user handle instead keeps the name free to be the human-readable account
 * name (see [WebauthnUserEntityRepository]).
 */
class MergedUserWebAuthnAuthenticationProvider(
    private val relyingParty: WebAuthnRelyingPartyOperations,
    private val passkeyQueries: PasskeyQueries,
    private val authenticationService: AuthenticationService,
) : AuthenticationProvider {
    override fun authenticate(authentication: Authentication): Authentication? {
        val token = authentication as? WebAuthnAuthenticationRequestToken ?: return null
        val userEntity =
            try {
                relyingParty.authenticate(token.webAuthnRequest)
            } catch (e: RuntimeException) {
                // Spring's operations report a bad assertion - unknown credential, wrong
                // signature, replayed counter - as plain runtime exceptions. Without translating
                // them the filter answers 500 instead of 401, and the login page never gets to
                // tell the browser to forget a passkey the server has deleted.
                throw BadCredentialsException(e.message ?: "Passkey assertion rejected", e)
            }
        val userId =
            passkeyQueries.findWebauthnUserIdByHandle(userEntity.id.toBase64UrlString())
                ?: throw BadCredentialsException("Passkey is not linked to a user")
        val principal = authenticationService.loadPrincipal(userId) ?: throw BadCredentialsException("Passkey user no longer exists")
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities)
    }

    override fun supports(authentication: Class<*>): Boolean =
        WebAuthnAuthenticationRequestToken::class.java.isAssignableFrom(authentication)
}
