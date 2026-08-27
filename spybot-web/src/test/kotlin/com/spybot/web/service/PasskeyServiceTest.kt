package com.spybot.web.service

import com.spybot.core.config.SpybotProperties
import com.spybot.core.service.AuthenticationService
import com.spybot.core.service.SpybotQueryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.mock.web.MockHttpServletRequest

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
}
