package com.spybot.web.controller

import com.spybot.core.config.SpybotProperties
import com.spybot.core.security.MergedUserPrincipal
import com.spybot.core.service.SpybotQueryService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * Data for the WebAuthn Signal API (`PublicKeyCredential.signal*`), which lets the site tell the
 * browser's passkey manager which passkeys are still valid so it can drop stale entries. The
 * browser is the one doing the signalling; these endpoints just answer what it needs to know.
 */
@RestController
@RequestMapping("/passkeys")
class PasskeySignalController(
    private val queryService: SpybotQueryService,
    private val properties: SpybotProperties,
) {
    data class HandleCredentials(
        val userId: String,
        val credentialIds: List<String>,
    )

    data class AcceptedCredentials(
        val rpId: String,
        val name: String,
        val displayName: String,
        val handles: List<HandleCredentials>,
    )

    data class KnownCredential(
        val known: Boolean,
    )

    /**
     * Everything the logged-in user's passkey manager should keep, per user handle. A handle with
     * no credentials is listed too: signalling an empty list is how the manager learns to remove
     * the last one.
     */
    @GetMapping("/accepted")
    fun accepted(
        @AuthenticationPrincipal principal: MergedUserPrincipal,
    ): AcceptedCredentials {
        val byHandle = queryService.webauthnCredentialsForUser(principal.id).groupBy { it.userHandle }
        return AcceptedCredentials(
            rpId = URI(properties.publicBaseUrl).host,
            name = principal.username,
            displayName = principal.displayName,
            handles =
                queryService.webauthnUserHandlesForUser(principal.id).map { handle ->
                    HandleCredentials(handle, byHandle[handle].orEmpty().map { it.credentialId })
                },
        )
    }

    /**
     * After a login the server rejected: does this credential exist at all? If not, the browser
     * can drop it from its passkey manager. Credential ids are random and unguessable, so this
     * doesn't leak anything a caller couldn't already prove by holding the passkey.
     */
    @GetMapping("/known")
    fun known(
        @RequestParam credentialId: String,
    ): KnownCredential = KnownCredential(queryService.findWebauthnCredential(credentialId) != null)
}
