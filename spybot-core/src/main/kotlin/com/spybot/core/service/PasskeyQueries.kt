package com.spybot.core.service

import com.spybot.core.jooq.notNull
import com.spybot.core.model.PasskeyView
import com.spybot.core.model.WebauthnCredential
import com.spybot.jooq.tables.records.SpybotUserpasskeyRecord
import com.spybot.jooq.tables.references.SPYBOT_USERPASSKEY
import com.spybot.jooq.tables.references.SPYBOT_WEBAUTHN_USER_HANDLE
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.jooq.DSLContext
import org.jooq.Records.mapping
import org.jooq.impl.DSL
import org.springframework.stereotype.Service

/** WebAuthn passkeys stored per merged user. */
@Service
class PasskeyQueries(
    private val dsl: DSLContext,
) {
    fun passkeysForUser(userId: Long): List<PasskeyView> =
        dsl
            .select(
                SPYBOT_USERPASSKEY.ID.notNull(),
                SPYBOT_USERPASSKEY.NAME.notNull(),
                SPYBOT_USERPASSKEY.PLATFORM.notNull(),
                SPYBOT_USERPASSKEY.ADDED_ON,
                SPYBOT_USERPASSKEY.LAST_USED,
                SPYBOT_USERPASSKEY.BACKUP_STATE.notNull(),
            ).from(SPYBOT_USERPASSKEY)
            .where(SPYBOT_USERPASSKEY.USER_ID.eq(userId))
            .orderBy(SPYBOT_USERPASSKEY.ADDED_ON.desc())
            .fetch(mapping(::PasskeyView))

    fun deletePasskey(
        userId: Long,
        passkeyId: Long,
    ): Boolean =
        dsl
            .deleteFrom(SPYBOT_USERPASSKEY)
            .where(SPYBOT_USERPASSKEY.ID.eq(passkeyId))
            .and(SPYBOT_USERPASSKEY.USER_ID.eq(userId))
            .execute() > 0

    fun renamePasskey(
        userId: Long,
        passkeyId: Long,
        name: String,
    ): Boolean =
        dsl
            .update(SPYBOT_USERPASSKEY)
            .set(SPYBOT_USERPASSKEY.NAME, name)
            .where(SPYBOT_USERPASSKEY.ID.eq(passkeyId))
            .and(SPYBOT_USERPASSKEY.USER_ID.eq(userId))
            .execute() > 0

    fun findWebauthnCredential(credentialId: String): WebauthnCredential? =
        dsl
            .selectFrom(SPYBOT_USERPASSKEY)
            .where(SPYBOT_USERPASSKEY.CREDENTIAL_ID.eq(credentialId))
            .fetchOne()
            ?.toWebauthnCredential()

    /** Every credential of the user a handle resolves to, across all of that user's handles. */
    fun webauthnCredentialsForHandle(handle: String): List<WebauthnCredential> {
        val userId = findWebauthnUserIdByHandle(handle) ?: return emptyList()
        return dsl
            .selectFrom(SPYBOT_USERPASSKEY)
            .where(SPYBOT_USERPASSKEY.USER_ID.eq(userId))
            .orderBy(SPYBOT_USERPASSKEY.ADDED_ON.desc())
            .fetch()
            .map { it.toWebauthnCredential() }
    }

    /** Inserts a new credential or updates the mutable parts of an existing one (counter, flags, last use). */
    fun saveWebauthnCredential(credential: WebauthnCredential) {
        dsl
            .insertInto(SPYBOT_USERPASSKEY)
            .set(SPYBOT_USERPASSKEY.USER_ID, credential.userId)
            .set(SPYBOT_USERPASSKEY.USER_HANDLE, credential.userHandle)
            .set(SPYBOT_USERPASSKEY.CREDENTIAL_ID, credential.credentialId)
            .set(SPYBOT_USERPASSKEY.PUBLIC_KEY_COSE, credential.publicKeyCose)
            .set(SPYBOT_USERPASSKEY.SIGNATURE_COUNT, credential.signatureCount)
            .set(SPYBOT_USERPASSKEY.UV_INITIALIZED, credential.uvInitialized)
            .set(SPYBOT_USERPASSKEY.TRANSPORTS, credential.transports.joinToString(","))
            .set(SPYBOT_USERPASSKEY.BACKUP_ELIGIBLE, credential.backupEligible)
            .set(SPYBOT_USERPASSKEY.BACKUP_STATE, credential.backupState)
            .set(SPYBOT_USERPASSKEY.AAGUID, credential.aaguid)
            .set(SPYBOT_USERPASSKEY.ATTESTATION_OBJECT, credential.attestationObject)
            .set(SPYBOT_USERPASSKEY.ATTESTATION_CLIENT_DATA_JSON, credential.attestationClientDataJson)
            .set(SPYBOT_USERPASSKEY.NAME, credential.name)
            .set(SPYBOT_USERPASSKEY.PLATFORM, credential.platform)
            .set(SPYBOT_USERPASSKEY.ENABLED, true)
            .set(SPYBOT_USERPASSKEY.ADDED_ON, credential.addedOn)
            .set(SPYBOT_USERPASSKEY.LAST_USED, credential.lastUsed)
            .onConflict(SPYBOT_USERPASSKEY.CREDENTIAL_ID)
            .doUpdate()
            .set(SPYBOT_USERPASSKEY.SIGNATURE_COUNT, credential.signatureCount)
            .set(SPYBOT_USERPASSKEY.UV_INITIALIZED, credential.uvInitialized)
            .set(SPYBOT_USERPASSKEY.BACKUP_STATE, credential.backupState)
            .set(SPYBOT_USERPASSKEY.LAST_USED, credential.lastUsed)
            .execute()
    }

    fun deleteWebauthnCredential(credentialId: String): Boolean =
        dsl
            .deleteFrom(SPYBOT_USERPASSKEY)
            .where(SPYBOT_USERPASSKEY.CREDENTIAL_ID.eq(credentialId))
            .execute() > 0

    fun findWebauthnUserIdByHandle(handle: String): Long? =
        dsl
            .select(SPYBOT_WEBAUTHN_USER_HANDLE.MERGED_USER_ID)
            .from(SPYBOT_WEBAUTHN_USER_HANDLE)
            .where(SPYBOT_WEBAUTHN_USER_HANDLE.HANDLE.eq(handle))
            .fetchOne(SPYBOT_WEBAUTHN_USER_HANDLE.MERGED_USER_ID)

    /**
     * The handle new passkeys are registered under: the user's oldest one, created on demand.
     * Older handles a merged-in account brought along stay valid for the passkeys that carry them.
     */
    fun findOrCreateWebauthnUserHandle(
        userId: Long,
        newHandle: () -> String,
    ): String {
        val existing =
            dsl
                .select(SPYBOT_WEBAUTHN_USER_HANDLE.HANDLE)
                .from(SPYBOT_WEBAUTHN_USER_HANDLE)
                .where(SPYBOT_WEBAUTHN_USER_HANDLE.MERGED_USER_ID.eq(userId))
                .orderBy(SPYBOT_WEBAUTHN_USER_HANDLE.CREATED.asc())
                .limit(1)
                .fetchOne(SPYBOT_WEBAUTHN_USER_HANDLE.HANDLE)
        if (existing != null) return existing
        val handle = newHandle()
        saveWebauthnUserHandle(handle, userId)
        return handle
    }

    fun saveWebauthnUserHandle(
        handle: String,
        userId: Long,
    ) {
        dsl
            .insertInto(SPYBOT_WEBAUTHN_USER_HANDLE)
            .set(SPYBOT_WEBAUTHN_USER_HANDLE.HANDLE, handle)
            .set(SPYBOT_WEBAUTHN_USER_HANDLE.MERGED_USER_ID, userId)
            .set(SPYBOT_WEBAUTHN_USER_HANDLE.CREATED, DSL.currentOffsetDateTime())
            .onConflict(SPYBOT_WEBAUTHN_USER_HANDLE.HANDLE)
            .doUpdate()
            .set(SPYBOT_WEBAUTHN_USER_HANDLE.MERGED_USER_ID, userId)
            .execute()
    }

    fun deleteWebauthnUserHandle(handle: String) {
        dsl
            .deleteFrom(SPYBOT_WEBAUTHN_USER_HANDLE)
            .where(SPYBOT_WEBAUTHN_USER_HANDLE.HANDLE.eq(handle))
            .execute()
    }

    private fun SpybotUserpasskeyRecord.toWebauthnCredential(): WebauthnCredential =
        WebauthnCredential(
            userId = userId!!,
            userHandle = userHandle!!,
            credentialId = credentialId!!,
            publicKeyCose = publicKeyCose!!,
            signatureCount = signatureCount ?: 0L,
            uvInitialized = uvInitialized ?: false,
            transports = transports.orEmpty().split(',').filter { it.isNotBlank() },
            backupEligible = backupEligible ?: false,
            backupState = backupState ?: false,
            aaguid = aaguid.orEmpty(),
            attestationObject = attestationObject!!,
            attestationClientDataJson = attestationClientDataJson!!,
            name = name.orEmpty(),
            platform = platform.orEmpty(),
            addedOn = addedOn ?: OffsetDateTime.now(ZoneOffset.UTC),
            lastUsed = lastUsed,
        )
}
