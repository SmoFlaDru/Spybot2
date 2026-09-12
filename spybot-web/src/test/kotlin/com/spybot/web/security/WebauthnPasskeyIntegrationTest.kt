package com.spybot.web.security

import com.spybot.core.service.PasskeyQueries
import com.spybot.core.service.RecorderQueries
import com.spybot.web.service.AdminService
import com.webauthn4j.converter.AttestationObjectConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.attestation.AttestationObject
import com.webauthn4j.data.attestation.authenticator.AAGUID
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData
import com.webauthn4j.data.attestation.authenticator.EC2COSEKey
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement
import com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.web.webauthn.api.AttestationConveyancePreference
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse
import org.springframework.security.web.webauthn.api.AuthenticatorAttestationResponse
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria
import org.springframework.security.web.webauthn.api.Bytes
import org.springframework.security.web.webauthn.api.ImmutableAuthenticationExtensionsClientOutputs
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose
import org.springframework.security.web.webauthn.api.PublicKeyCredential
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions
import org.springframework.security.web.webauthn.api.PublicKeyCredentialParameters
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement
import org.springframework.security.web.webauthn.api.UserVerificationRequirement
import org.springframework.security.web.webauthn.management.ImmutableRelyingPartyRegistrationRequest
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.json.JsonMapper
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64

/**
 * Drives Spring Security's relying-party operations against the real repositories and database,
 * the way the WebAuthn endpoints do - minus HTTP. Registration uses the exact payload a Safari
 * client sent in production (captured in a HAR; a "none" attestation and a public key for a
 * credential that was never stored). Login uses a synthetic authenticator whose private key the
 * test holds, so a full assertion can be signed.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spybot.public-base-url=https://spybot.bensge.com", "spybot.fido-server-name=Spybot"],
)
class WebauthnPasskeyIntegrationTest {
    @Autowired
    private lateinit var relyingParty: WebAuthnRelyingPartyOperations

    @Autowired
    private lateinit var userEntities: WebauthnUserEntityRepository

    @Autowired
    private lateinit var credentials: WebauthnCredentialRepository

    @Autowired
    private lateinit var passkeyQueries: PasskeyQueries

    @Autowired
    private lateinit var recorderQueries: RecorderQueries

    @Autowired
    private lateinit var adminService: AdminService

    private val rpId = "spybot.bensge.com"
    private val origin = "https://spybot.bensge.com"
    private val jackson =
        JsonMapper
            .builder()
            .addModule(
                org.springframework.security.web.webauthn.jackson
                    .WebauthnJacksonModule(),
            ).build()

    private fun newUser(name: String): Long =
        recorderQueries.createTeamSpeakIdentity(name, name.hashCode() and 0xffff, "uid-$name").mergedUserId

    @Test
    fun `a user gets one random handle, reused for every registration`() {
        val userId = newUser("handle-user")

        val first = userEntities.findByUsername(userId.toString())!!
        val second = userEntities.findByUsername(userId.toString())!!

        assertEquals(32, first.id.bytes.size)
        assertEquals(first.id, second.id)
        assertEquals(userId.toString(), first.name, "Spring loads the account by the entity name, so it must be the user id")
        assertEquals("handle-user", first.displayName)
        assertEquals(userId.toString(), userEntities.findById(first.id)!!.name)
    }

    @Test
    fun `registers the real Safari payload from the HAR and stores a full credential record`() {
        val userId = newUser("safari-user")
        val user = userEntities.findByUsername(userId.toString())!!
        val harChallenge = Bytes(Base64.getUrlDecoder().decode("QW7ExO2kU24kbeQKRrcZRruudsnLRjP5mbdydw3y4Y0"))
        val options =
            PublicKeyCredentialCreationOptions
                .builder()
                .rp(
                    PublicKeyCredentialRpEntity
                        .builder()
                        .id(rpId)
                        .name("Spybot")
                        .build(),
                ).user(user)
                .challenge(harChallenge)
                .pubKeyCredParams(PublicKeyCredentialParameters.ES256, PublicKeyCredentialParameters.RS256)
                .authenticatorSelection(
                    AuthenticatorSelectionCriteria
                        .builder()
                        .residentKey(
                            ResidentKeyRequirement.REQUIRED,
                        ).userVerification(UserVerificationRequirement.PREFERRED)
                        .build(),
                ).attestation(AttestationConveyancePreference.NONE)
                .build()
        val credentialJson = javaClass.getResource("/passkey/verify-registration-safari.json")!!.readText()
        val credential: PublicKeyCredential<AuthenticatorAttestationResponse> =
            jackson.readValue(
                credentialJson,
                jackson.typeFactory.constructParametricType(PublicKeyCredential::class.java, AuthenticatorAttestationResponse::class.java),
            )

        val record =
            relyingParty.registerCredential(
                ImmutableRelyingPartyRegistrationRequest(options, RelyingPartyPublicKey(credential, "Mac (Safari)")),
            )

        val stored = credentials.findByCredentialId(record.credentialId)!!
        assertEquals(user.id, stored.userEntityUserId)
        assertEquals("Mac (Safari)", stored.label)
        assertEquals(setOf("internal", "hybrid"), stored.transports.map { it.value }.toSet())
        assertTrue(stored.isBackupEligible, "Safari's iCloud Keychain passkey is backup-eligible")
        val listed = passkeyQueries.passkeysForUser(userId).single()
        assertEquals("Mac (Safari)", listed.name)
        assertEquals("iCloud Keychain", listed.platform, "the provider is derived from the AAGUID in the attestation")
        assertEquals(1, credentials.findByUserId(user.id).size)
    }

    @Test
    fun `a passkey registered before a merge still logs in, as the merged user`() {
        val alice = newUser("alice")
        val bob = newUser("bob")
        val authenticator = SyntheticAuthenticator()
        val aliceHandle = userEntities.findByUsername(alice.toString())!!.id
        credentials.save(authenticator.credentialRecord(aliceHandle))

        // Before the merge the passkey resolves to Alice...
        assertEquals(alice.toString(), relyingParty.authenticate(authenticator.assertion(aliceHandle)).name)

        // ...then Alice is merged into Bob.
        adminService.mergeUsers(targetId = bob, sourceIds = listOf(alice))

        // The authenticator still presents Alice's handle; the server now maps it to Bob.
        val loggedIn = relyingParty.authenticate(authenticator.assertion(aliceHandle))
        assertEquals(bob.toString(), loggedIn.name)
        assertEquals("bob", loggedIn.displayName)
        assertEquals(bob, passkeyQueries.findWebauthnUserIdByHandle(aliceHandle.toBase64UrlString()))
        assertEquals(1, passkeyQueries.passkeysForUser(bob).size)
        assertEquals(0, passkeyQueries.passkeysForUser(alice).size)

        // Whichever handle Bob registers new passkeys under now, it resolves to Bob, and the
        // exclude list for a new registration covers the inherited passkey as well.
        val bobHandle = userEntities.findByUsername(bob.toString())!!.id
        assertEquals(bob.toString(), userEntities.findById(bobHandle)!!.name)
        assertEquals(1, credentials.findByUserId(bobHandle).size)
        assertEquals(1, credentials.findByUserId(aliceHandle).size)
    }

    @Test
    fun `login updates the signature counter and last-used timestamp`() {
        val userId = newUser("counter-user")
        val authenticator = SyntheticAuthenticator()
        val handle = userEntities.findByUsername(userId.toString())!!.id
        credentials.save(authenticator.credentialRecord(handle))

        relyingParty.authenticate(authenticator.assertion(handle, signCount = 7))

        val stored = credentials.findByCredentialId(authenticator.credentialId)!!
        assertEquals(7, stored.signatureCount)
        assertNotNull(stored.lastUsed)
        assertNotNull(passkeyQueries.passkeysForUser(userId).single().lastUsed)
    }

    @Test
    fun `an assertion for an unknown credential is rejected`() {
        val authenticator = SyntheticAuthenticator()
        assertThrows<Exception> { relyingParty.authenticate(authenticator.assertion(Bytes.random())) }
        assertNull(credentials.findByCredentialId(authenticator.credentialId))
    }

    /** An EC P-256 software authenticator: enough to register a record and sign assertions. */
    private inner class SyntheticAuthenticator {
        private val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val credentialId: Bytes = Bytes.random()

        fun credentialRecord(userHandle: Bytes): ImmutableCredentialRecord {
            val cose = EC2COSEKey.create(keyPair.public as ECPublicKey, COSEAlgorithmIdentifier.ES256)
            val objectConverter = ObjectConverter()
            val coseBytes = objectConverter.cborConverter.writeValueAsBytes(cose)
            // Spring reads the public key back out of the attestation object on login, so the
            // record needs a real (if unattested) one: authenticator data with the credential.
            val attestedData = AttestedCredentialData(AAGUID.ZERO, credentialId.bytes, cose)
            val authenticatorData =
                AuthenticatorData<RegistrationExtensionAuthenticatorOutput>(
                    MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray()),
                    (
                        AuthenticatorData.BIT_UP.toInt() or AuthenticatorData.BIT_UV.toInt() or AuthenticatorData.BIT_BE.toInt() or
                            AuthenticatorData.BIT_BS.toInt() or
                            AuthenticatorData.BIT_AT.toInt()
                    ).toByte(),
                    0,
                    attestedData,
                )
            val attestationObject =
                AttestationObjectConverter(
                    objectConverter,
                ).convertToBytes(AttestationObject(authenticatorData, NoneAttestationStatement()))
            val registrationClientData =
                """{"type":"webauthn.create","challenge":"${Bytes.random().toBase64UrlString()}","origin":"$origin","crossOrigin":false}"""
                    .toByteArray()
            return ImmutableCredentialRecord
                .builder()
                .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                .credentialId(credentialId)
                .userEntityUserId(userHandle)
                .publicKey(ImmutablePublicKeyCose(coseBytes))
                .signatureCount(0)
                .uvInitialized(true)
                .transports(emptySet())
                .backupEligible(true)
                .backupState(true)
                .attestationObject(Bytes(attestationObject))
                .attestationClientDataJSON(Bytes(registrationClientData))
                .label("Synthetic authenticator")
                .created(Instant.now())
                .build()
        }

        fun assertion(
            userHandle: Bytes,
            signCount: Long = 1,
        ): RelyingPartyAuthenticationRequest {
            val challenge = Bytes.random()
            val requestOptions =
                PublicKeyCredentialRequestOptions
                    .builder()
                    .challenge(challenge)
                    .rpId(rpId)
                    .build()
            val clientDataJson =
                """{"type":"webauthn.get","challenge":"${challenge.toBase64UrlString()}","origin":"$origin","crossOrigin":false}"""
                    .toByteArray()
            val sha256 = MessageDigest.getInstance("SHA-256")
            val authenticatorData =
                sha256.digest(rpId.toByteArray()) +
                    byteArrayOf(0x1d) + // UP | UV | BE | BS
                    byteArrayOf((signCount shr 24).toByte(), (signCount shr 16).toByte(), (signCount shr 8).toByte(), signCount.toByte())
            val signature =
                Signature
                    .getInstance("SHA256withECDSA")
                    .apply {
                        initSign(keyPair.private as ECPrivateKey)
                        update(authenticatorData + sha256.digest(clientDataJson))
                    }.sign()
            val response =
                AuthenticatorAssertionResponse
                    .builder()
                    .clientDataJSON(Bytes(clientDataJson))
                    .authenticatorData(Bytes(authenticatorData))
                    .signature(Bytes(signature))
                    .userHandle(userHandle)
                    .build()

            @Suppress("UNCHECKED_CAST")
            val credential =
                PublicKeyCredential
                    .builder<AuthenticatorAssertionResponse>()
                    .id(credentialId.toBase64UrlString())
                    .rawId(credentialId)
                    .type(PublicKeyCredentialType.PUBLIC_KEY)
                    .response(response)
                    .clientExtensionResults(ImmutableAuthenticationExtensionsClientOutputs(emptyList()))
                    .build() as PublicKeyCredential<AuthenticatorAssertionResponse>
            return RelyingPartyAuthenticationRequest(requestOptions, credential)
        }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17.2")

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
