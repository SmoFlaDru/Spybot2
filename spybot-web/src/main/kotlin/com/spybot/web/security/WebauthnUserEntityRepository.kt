package com.spybot.web.security

import com.spybot.core.service.MergedUserQueries
import com.spybot.core.service.PasskeyQueries
import org.springframework.security.web.webauthn.api.Bytes
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository
import org.springframework.stereotype.Component
import java.util.Base64

/**
 * Maps WebAuthn user handles to merged users for Spring Security.
 *
 * The entity's name and display name are both the account name: password managers show the
 * name as the passkey's "username", so it must be human-readable. Nothing resolves an account
 * from the name - [MergedUserWebAuthnAuthenticationProvider] goes by the handle - which also
 * means a rename leaves existing passkeys working. Spring asks [findByUsername] with
 * `Authentication.getName()`, which for this app is the numeric merged-user id (see
 * [com.spybot.core.security.MergedUserPrincipal.getUsername]).
 *
 * Handles are aliases, not a one-to-one key: after two accounts are merged, the source's handles
 * point at the target, so a passkey that still carries the old handle logs the user into the
 * merged account. [findById] therefore returns an entity whose id is the handle that was asked
 * for (Spring compares it against the credential record) and whose name is whichever user
 * currently owns it.
 */
@Component
class WebauthnUserEntityRepository(
    private val mergedUserQueries: MergedUserQueries,
    private val passkeyQueries: PasskeyQueries,
) : PublicKeyCredentialUserEntityRepository {
    override fun findById(id: Bytes): PublicKeyCredentialUserEntity? {
        val userId = passkeyQueries.findWebauthnUserIdByHandle(id.toBase64UrlString()) ?: return null
        val user = mergedUserQueries.findMergedUserById(userId) ?: return null
        return entity(id, user.name)
    }

    override fun findByUsername(username: String): PublicKeyCredentialUserEntity? {
        val userId = username.toLongOrNull() ?: return null
        val user = mergedUserQueries.findMergedUserById(userId) ?: return null
        val handle = passkeyQueries.findOrCreateWebauthnUserHandle(userId) { Bytes.random().toBase64UrlString() }
        return entity(handleBytes(handle), user.name)
    }

    /**
     * Only reached if Spring had to create an entity itself because [findByUsername] returned
     * null, i.e. the logged-in user no longer exists. Spring names such an entity after
     * `Authentication.getName()`, the user id.
     */
    override fun save(userEntity: PublicKeyCredentialUserEntity) {
        val userId =
            userEntity.name.toLongOrNull()
                ?: throw IllegalArgumentException("WebAuthn user entity name must be a merged user id: ${userEntity.name}")
        passkeyQueries.saveWebauthnUserHandle(userEntity.id.toBase64UrlString(), userId)
    }

    override fun delete(id: Bytes) {
        passkeyQueries.deleteWebauthnUserHandle(id.toBase64UrlString())
    }

    private fun entity(
        handle: Bytes,
        name: String,
    ): PublicKeyCredentialUserEntity =
        ImmutablePublicKeyCredentialUserEntity
            .builder()
            .id(handle)
            .name(name)
            .displayName(name)
            .build()

    companion object {
        fun handleBytes(handle: String): Bytes = Bytes(Base64.getUrlDecoder().decode(handle))
    }
}
