package com.spybot.web.security

import com.spybot.core.model.WebauthnCredential
import com.spybot.core.service.SpybotQueryService
import com.webauthn4j.converter.AttestationObjectConverter
import com.webauthn4j.converter.util.ObjectConverter
import org.slf4j.LoggerFactory
import org.springframework.security.web.webauthn.api.AuthenticatorTransport
import org.springframework.security.web.webauthn.api.Bytes
import org.springframework.security.web.webauthn.api.CredentialRecord
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType
import org.springframework.security.web.webauthn.management.UserCredentialRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64

/**
 * Stores Spring Security's [CredentialRecord]s in spybot_userpasskey. Spring calls [save] both
 * for a new registration and after every successful login (updated signature counter, backup
 * state, last use), so it is an upsert keyed on the credential id.
 */
@Component
class WebauthnCredentialRepository(
    private val queryService: SpybotQueryService,
) : UserCredentialRepository {
    private val log = LoggerFactory.getLogger(javaClass)
    private val attestationObjectConverter = AttestationObjectConverter(ObjectConverter())

    override fun findByCredentialId(credentialId: Bytes): CredentialRecord? =
        queryService.findWebauthnCredential(credentialId.toBase64UrlString())?.toRecord()

    override fun findByUserId(userId: Bytes): List<CredentialRecord> =
        queryService.webauthnCredentialsForHandle(userId.toBase64UrlString()).map { it.toRecord() }

    override fun save(credentialRecord: CredentialRecord) {
        val handle = credentialRecord.userEntityUserId.toBase64UrlString()
        val userId =
            queryService.findWebauthnUserIdByHandle(handle)
                ?: throw IllegalStateException("WebAuthn user handle $handle is not mapped to a user")
        val existing = queryService.findWebauthnCredential(credentialRecord.credentialId.toBase64UrlString())
        val aaguid = existing?.aaguid ?: aaguidOf(credentialRecord)
        queryService.saveWebauthnCredential(
            WebauthnCredential(
                userId = userId,
                userHandle = handle,
                credentialId = credentialRecord.credentialId.toBase64UrlString(),
                publicKeyCose = encode(credentialRecord.publicKey.bytes),
                signatureCount = credentialRecord.signatureCount,
                uvInitialized = credentialRecord.isUvInitialized,
                transports = credentialRecord.transports.orEmpty().map { it.value },
                backupEligible = credentialRecord.isBackupEligible,
                backupState = credentialRecord.isBackupState,
                aaguid = aaguid,
                // Spring's CredentialRecord declares these nullable, but registration always sets
                // them and authentication can't work without the attestation object.
                attestationObject = requireNotNull(credentialRecord.attestationObject) { "attestation object missing" }.toBase64UrlString(),
                attestationClientDataJson =
                    requireNotNull(
                        credentialRecord.attestationClientDataJSON,
                    ) { "client data missing" }.toBase64UrlString(),
                name = existing?.name ?: credentialRecord.label.orEmpty().ifBlank { "Passkey" },
                platform = existing?.platform ?: PasskeyProviders.nameFor(aaguid),
                addedOn = existing?.addedOn ?: (credentialRecord.created ?: Instant.now()).atOffset(ZoneOffset.UTC),
                lastUsed = credentialRecord.lastUsed?.atOffset(ZoneOffset.UTC) ?: existing?.lastUsed,
            ),
        )
    }

    override fun delete(credentialId: Bytes) {
        queryService.deleteWebauthnCredential(credentialId.toBase64UrlString())
    }

    private fun aaguidOf(record: CredentialRecord): String =
        try {
            attestationObjectConverter
                .convert(requireNotNull(record.attestationObject).bytes)
                ?.authenticatorData
                ?.attestedCredentialData
                ?.aaguid
                ?.toString()
                .orEmpty()
        } catch (e: Exception) {
            log.warn("Could not read the AAGUID from a passkey's attestation object", e)
            ""
        }

    private fun WebauthnCredential.toRecord(): CredentialRecord =
        ImmutableCredentialRecord
            .builder()
            .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
            .credentialId(decode(credentialId))
            .userEntityUserId(decode(userHandle))
            .publicKey(ImmutablePublicKeyCose(Base64.getUrlDecoder().decode(publicKeyCose)))
            .signatureCount(signatureCount)
            .uvInitialized(uvInitialized)
            .transports(transports.map { AuthenticatorTransport.valueOf(it) }.toSet())
            .backupEligible(backupEligible)
            .backupState(backupState)
            .attestationObject(decode(attestationObject))
            .attestationClientDataJSON(decode(attestationClientDataJson))
            .label(name)
            .created(addedOn.toInstant())
            .apply { lastUsed?.let { lastUsed(it.toInstant()) } }
            .build()

    private fun decode(value: String): Bytes = Bytes(Base64.getUrlDecoder().decode(value))

    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/** Human-readable names for the passkey providers most likely to show up here, keyed by AAGUID. */
object PasskeyProviders {
    private val names =
        mapOf(
            "fbfc3007-154e-4ecc-8c0b-6e020557d7bd" to "iCloud Keychain",
            "ea9b8d66-4d01-1d21-3ce4-b6b48cb575d4" to "Google Password Manager",
            "adce0002-35bc-c60a-648b-0b25f1f05503" to "Chrome on Mac",
            "08987058-cadc-4b81-b6e1-30de50dcbe96" to "Windows Hello",
            "9ddd1817-af5a-4672-a2b9-3e3dd95000a9" to "Windows Hello",
            "6028b017-b1d4-4c02-b4b3-afcdafc96bb2" to "Windows Hello",
            "bada5566-a7aa-401f-bd96-45619a55120d" to "1Password",
            "d548826e-79b4-db40-a3d8-11116f7e8349" to "Bitwarden",
            "531126d6-e717-415c-9320-3d9aa6981239" to "Dashlane",
            "fdb141b2-5d84-443e-8a35-4698c205a502" to "KeePassXC",
            "50726f74-6f6e-5061-7373-50726f746f6e" to "Proton Pass",
            "53414d53-554e-4700-0000-000000000000" to "Samsung Pass",
            "b5397666-4885-aa6b-cebf-e52262a439a2" to "Chromium",
            "dd4ec289-e01d-41c9-bb89-70fa845d4bf2" to "iCloud Keychain (managed)",
        )

    fun nameFor(aaguid: String): String = names[aaguid.lowercase()] ?: ""
}
