package com.spybot.recorder.service

import com.spybot.core.config.SpybotProperties
import com.spybot.core.service.RecorderDomainService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class RecorderLoopGatewayTest {
    private val gateway =
        RecorderLoopGateway(
            client = Mockito.mock(TeamSpeakQueryClient::class.java),
            domainService = Mockito.mock(RecorderDomainService::class.java),
            properties = SpybotProperties(),
        )

    @Test
    fun `backoff doubles with each consecutive failure`() {
        assertEquals(1_000L, gateway.backoffDelayMs(0))
        assertEquals(2_000L, gateway.backoffDelayMs(1))
        assertEquals(4_000L, gateway.backoffDelayMs(2))
        assertEquals(8_000L, gateway.backoffDelayMs(3))
        assertEquals(16_000L, gateway.backoffDelayMs(4))
    }

    @Test
    fun `backoff is capped and does not keep growing forever`() {
        val atCap = gateway.backoffDelayMs(8)

        assertEquals(atCap, gateway.backoffDelayMs(9))
        assertEquals(atCap, gateway.backoffDelayMs(100))
    }

    @Test
    fun `backoff never exceeds the configured maximum`() {
        for (failures in 0..200) {
            assert(gateway.backoffDelayMs(failures) <= 300_000L) {
                "backoff for $failures consecutive failures exceeded the cap"
            }
        }
    }
}
