package com.spybot.web.security

import com.spybot.core.service.SpybotQueryService
import org.springframework.security.web.webauthn.api.Bytes
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository
import org.springframework.stereotype.Component
import java.util.Base64

/**
 * Maps WebAuthn user handles to merged users for Spring Security.
 *
 * Spring identifies the account by the entity's [PublicKeyCredentialUserEntity.getName], which it
 * hands to the UserDetailsService - so that is the numeric merged-user id, the same value
 * [com.spybot.core.security.MergedUserPrincipal.getUsername] returns. The display name is what
 * the OS passkey prompt shows.
 *
 * Handles are aliases, not a one-to-one key: after two accounts are merged, the source's handles
 * point at the target, so a passkey that still carries the old handle logs the user into the
 * merged account. [findById] therefore returns an entity whose id is the handle that was asked
 * for (Spring compares it against the credential record) and whose name is whichever user
 * currently owns it.
 */
@Component
class WebauthnUserEntityRepository(
    private val queryService: SpybotQueryService,
) : PublicKeyCredentialUserEntityRepository {
    override fun findById(id: Bytes): PublicKeyCredentialUserEntity? {
        val userId = queryService.findWebauthnUserIdByHandle(id.toBase64UrlString()) ?: return null
        val user = queryService.findMergedUserById(userId) ?: return null
        return entity(id, userId, user.name)
    }

    override fun findByUsername(username: String): PublicKeyCredentialUserEntity? {
        val userId = username.toLongOrNull() ?: return null
        val user = queryService.findMergedUserById(userId) ?: return null
        val handle = queryService.findOrCreateWebauthnUserHandle(userId) { Bytes.random().toBase64UrlString() }
        return entity(handleBytes(handle), userId, user.name)
    }

    override fun save(userEntity: PublicKeyCredentialUserEntity) {
        val userId = userEntity.name.toLongOrNull() ?: throw IllegalArgumentException("WebAuthn user entity name must be a merged user id: ${userEntity.name}")
        queryService.saveWebauthnUserHandle(userEntity.id.toBase64UrlString(), userId)
    }

    override fun delete(id: Bytes) {
        queryService.deleteWebauthnUserHandle(id.toBase64UrlString())
    }

    private fun entity(
        handle: Bytes,
        userId: Long,
        displayName: String,
    ): PublicKeyCredentialUserEntity =
        ImmutablePublicKeyCredentialUserEntity
            .builder()
            .id(handle)
            .name(userId.toString())
            .displayName(displayName)
            .build()

    companion object {
        fun handleBytes(handle: String): Bytes = Bytes(Base64.getUrlDecoder().decode(handle))
    }
}
