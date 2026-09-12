package com.spybot.web.service

import com.spybot.core.config.SpybotProperties
import com.spybot.core.service.AuthenticationService
import com.spybot.core.service.SpybotQueryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockHttpServletRequest
import java.time.Instant

class PasskeyServiceTest {
    @Test
    fun `generate authentication options stores wrapped challenge in session`() {
        val service =
            PasskeyService(
                SpybotProperties(publicBaseUrl = "https://spybot.local"),
                mock(SpybotQueryService::class.java),
                mock(AuthenticationService::class.java),
            )
        val request =
            MockHttpServletRequest().apply {
                scheme = "https"
                serverName = "spybot.local"
                serverPort = 443
                addHeader("Host", "spybot.local")
            }

        val body = service.generateAuthenticationOptions(request)

        val publicKey = body["publicKey"] as Map<*, *>
        assertEquals("spybot.local", publicKey["rpId"])
        assertTrue((publicKey["challenge"] as String).isNotBlank())
        val state = request.session!!.getAttribute("fido2_state") as PasskeyService.PasskeySessionState
        assertEquals(publicKey["challenge"], state.challenge)
    }

    /**
     * Regression test for passkey registration failing in production. The payload is the real
     * verify-registration request a Safari client sent (captured in a HAR): a "none" attestation
     * and a public key for a credential that was never stored, nothing secret. The challenge it
     * was created against is fixed into the session state below.
     */
    private val harChallenge = "QW7ExO2kU24kbeQKRrcZRruudsnLRjP5mbdydw3y4Y0"
    private val harBody = javaClass.getResource("/passkey/verify-registration-safari.json")!!.readText()

    private fun registrationRequest(forwardedProto: String) =
        MockHttpServletRequest().apply {
            scheme = "http"
            serverName = "spybot-web"
            serverPort = 8000
            addHeader("Host", "spybot.bensge.com")
            addHeader("X-Forwarded-Proto", forwardedProto)
            addHeader(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.4 Safari/605.1.15",
            )
            session!!.setAttribute("fido2_state", PasskeyService.PasskeySessionState(challenge = harChallenge, userId = 708))
        }

    @Test
    fun `registration succeeds behind a proxy that reports http even though the browser used https`() {
        // Production: TLS terminates in front of Caddy, so the app sees X-Forwarded-Proto: http.
        // The browser signed https://spybot.bensge.com; the configured public base URL must win.
        val queryService = mock(SpybotQueryService::class.java)
        `when`(
            queryService.createPasskey(
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(Instant::class.java) ?: Instant.EPOCH,
            ),
        ).thenReturn(1L)
        val service =
            PasskeyService(
                SpybotProperties(publicBaseUrl = "https://spybot.bensge.com"),
                queryService,
                mock(AuthenticationService::class.java),
            )

        val result = service.verifyRegistration(registrationRequest(forwardedProto = "http"), harBody)

        assertEquals(mapOf("status" to "OK", "verified" to true), result)
        // Mockito's eq() returns null, which Kotlin refuses for non-null parameters; the elvis keeps the matcher registered.
        verify(queryService).createPasskey(
            eq(708L),
            eq("Mac") ?: "",
            eq("Safari on macOS") ?: "",
            eq("GkafmU-UmJqR3WkzAUlpfd0pRXc") ?: "",
            anyString(),
            any(Instant::class.java) ?: Instant.EPOCH,
        )
    }

    @Test
    fun `registration is rejected when neither configured nor forwarded origin matches`() {
        val queryService = mock(SpybotQueryService::class.java)
        val service =
            PasskeyService(
                SpybotProperties(publicBaseUrl = "https://spybot.localhost"),
                queryService,
                mock(AuthenticationService::class.java),
            )

        val result = service.verifyRegistration(registrationRequest(forwardedProto = "http"), harBody)

        assertEquals("ERR", result["status"])
        verify(queryService, never()).createPasskey(
            anyLong(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any(Instant::class.java) ?: Instant.EPOCH,
        )
    }
}
