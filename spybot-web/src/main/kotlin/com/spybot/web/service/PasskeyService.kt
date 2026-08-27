package com.spybot.web.service

import com.spybot.core.config.SpybotProperties
import com.spybot.core.service.AuthenticationService
import com.spybot.core.service.SpybotQueryService
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.authenticator.AuthenticatorImpl
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.PublicKeyCredentialType
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.stereotype.Service
import java.io.Serializable
import java.net.URI
import java.time.Instant
import java.util.Base64

@Service
class PasskeyService(
    private val properties: SpybotProperties,
    private val queryService: SpybotQueryService,
    private val authenticationService: AuthenticationService,
) {
    private val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager()
    private val objectConverter = ObjectConverter()
    private val attestedCredentialDataConverter = AttestedCredentialDataConverter(objectConverter)

    fun generateAuthenticationOptions(request: HttpServletRequest): Map<String, Any> {
        val state = PasskeySessionState(challenge = challenge())
        request.session.setAttribute(PASSKEY_STATE_KEY, state)
        return mapOf(
            "publicKey" to
                mapOf(
                    "challenge" to state.challenge,
                    "timeout" to 60000,
                    "rpId" to rpId(),
                    "allowCredentials" to emptyList<Map<String, Any>>(),
                    "userVerification" to "preferred",
                ),
        )
    }

    fun generateRegistrationOptions(
        userId: Long,
        displayName: String,
        request: HttpServletRequest,
    ): Map<String, Any> {
        val state = PasskeySessionState(challenge = challenge(), userId = userId)
        request.session.setAttribute(PASSKEY_STATE_KEY, state)
        val excludeCredentials =
            queryService.passkeyCredentialsForUser(userId).map {
                mapOf(
                    "id" to it.credentialId,
                    "type" to "public-key",
                )
            }
        return mapOf(
            "publicKey" to
                mapOf(
                    "challenge" to state.challenge,
                    "rp" to
                        mapOf(
                            "name" to properties.fidoServerName,
                            "id" to rpId(),
                        ),
                    "user" to
                        mapOf(
                            "id" to encodeBase64Url(userId.toString().toByteArray()),
                            "name" to displayName,
                            "displayName" to displayName,
                        ),
                    "pubKeyCredParams" to
                        listOf(
                            mapOf("type" to PublicKeyCredentialType.PUBLIC_KEY.value, "alg" to COSEAlgorithmIdentifier.ES256.value),
                            mapOf("type" to PublicKeyCredentialType.PUBLIC_KEY.value, "alg" to COSEAlgorithmIdentifier.RS256.value),
                        ),
                    "timeout" to 60000,
                    "excludeCredentials" to excludeCredentials,
                    "authenticatorSelection" to
                        mapOf(
                            "residentKey" to "preferred",
                            "userVerification" to "preferred",
                        ),
                ),
        )
    }

    fun verifyRegistration(
        request: HttpServletRequest,
        responseJson: String,
    ): Map<String, Any?> =
        runCatching {
            val state =
                request.session.getAttribute(PASSKEY_STATE_KEY) as? PasskeySessionState
                    ?: return mapOf("status" to "ERR", "verified" to false, "message" to "FIDO Status can't be found, please try again")
            val userId =
                state.userId ?: return mapOf("status" to "ERR", "verified" to false, "message" to "Missing passkey registration user")
            val serverProperty = serverProperty(request, state.challenge)
            val registrationData =
                webAuthnManager.verifyRegistrationResponseJSON(
                    responseJson,
                    RegistrationParameters(serverProperty, false),
                )
            val attestedCredentialData = AuthenticatorImpl.createFromRegistrationData(registrationData).attestedCredentialData
            val encodedToken = encodeBase64Url(attestedCredentialDataConverter.convert(attestedCredentialData))
            val credentialId = encodeBase64Url(attestedCredentialData.credentialId)
            val userAgent = parseUserAgent(request.getHeader("User-Agent"))
            queryService.createPasskey(
                userId = userId,
                name = userAgent.deviceName,
                platform = userAgent.platform,
                credentialId = credentialId,
                token = encodedToken,
                addedOn = Instant.now(),
            )
            request.session.removeAttribute(PASSKEY_STATE_KEY)
            return mapOf("status" to "OK", "verified" to true)
        }.getOrElse {
            mapOf("status" to "ERR", "verified" to false, "message" to "Error on server, please try again later")
        }

    fun verifyAuthentication(
        request: HttpServletRequest,
        response: HttpServletResponse,
        responseJson: String,
    ): Map<String, Any?> =
        runCatching {
            val state =
                request.session.getAttribute(PASSKEY_STATE_KEY) as? PasskeySessionState
                    ?: return mapOf("verified" to false, "message" to "Missing authentication challenge")
            val credentialId =
                objectConverter.jsonMapper
                    .readTree(responseJson)
                    .path("id")
                    .asText()
            val stored =
                queryService.findPasskeyByCredentialId(credentialId)
                    ?: return mapOf("verified" to false, "message" to "Unknown passkey")
            val attestedCredentialData = attestedCredentialDataConverter.convert(decodeBase64Url(stored.token))
            val authenticator = AuthenticatorImpl(attestedCredentialData, null, 0)
            webAuthnManager.verifyAuthenticationResponseJSON(
                responseJson,
                AuthenticationParameters(
                    serverProperty(request, state.challenge),
                    authenticator,
                    false,
                ),
            )

            queryService.updatePasskeyLastUsed(stored.id)
            request.session.setAttribute(
                "passkey",
                mapOf(
                    "passkey" to true,
                    "name" to stored.name,
                    "id" to stored.id,
                    "platform" to stored.platform,
                ),
            )
            loginUser(stored.userId, request, response)
            request.session.removeAttribute(PASSKEY_STATE_KEY)
            return mapOf("verified" to true, "user" to stored.userId)
        }.getOrElse {
            mapOf("verified" to false, "message" to "Passkey authentication failed")
        }

    private fun loginUser(
        userId: Long,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val principal = authenticationService.loadPrincipal(userId) ?: return
        val authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities)
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
        HttpSessionSecurityContextRepository().saveContext(context, request, response)
    }

    private fun serverProperty(
        request: HttpServletRequest,
        challenge: String,
    ): ServerProperty {
        val forwardedProto = request.getHeader("X-Forwarded-Proto") ?: request.scheme
        val hostHeader = request.getHeader("X-Forwarded-Host") ?: request.getHeader("Host") ?: request.serverName
        val origin = Origin.create("$forwardedProto://$hostHeader")
        return ServerProperty(origin, rpId(), DefaultChallenge(decodeBase64Url(challenge)))
    }

    private fun rpId(): String = URI(properties.publicBaseUrl).host

    private fun challenge(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return encodeBase64Url(bytes)
    }

    private fun encodeBase64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun decodeBase64Url(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

    private fun parseUserAgent(userAgent: String?): ParsedUserAgent {
        val ua = userAgent.orEmpty()
        val os =
            when {
                "Mac OS X" in ua || "Macintosh" in ua -> "macOS"
                "Windows" in ua -> "Windows"
                "Android" in ua -> "Android"
                "iPhone" in ua || "iPad" in ua || "iOS" in ua -> "iOS"
                "Linux" in ua -> "Linux"
                else -> "Unknown OS"
            }
        val browser =
            when {
                "Edg/" in ua -> "Edge"
                "Firefox/" in ua -> "Firefox"
                "Chrome/" in ua -> "Chrome"
                "Safari/" in ua && "Chrome/" !in ua -> "Safari"
                else -> "Unknown Browser"
            }
        val device =
            when {
                "iPhone" in ua -> "iPhone"
                "iPad" in ua -> "iPad"
                "Android" in ua -> "Android device"
                "Macintosh" in ua -> "Mac"
                "Windows" in ua -> "Windows PC"
                "Linux" in ua -> "Linux device"
                else -> "This device"
            }
        return ParsedUserAgent(deviceName = device, platform = "$browser on $os")
    }

    private data class ParsedUserAgent(
        val deviceName: String,
        val platform: String,
    )

    data class PasskeySessionState(
        val challenge: String,
        val userId: Long? = null,
    ) : Serializable

    companion object {
        private const val PASSKEY_STATE_KEY = "fido2_state"
    }
}
