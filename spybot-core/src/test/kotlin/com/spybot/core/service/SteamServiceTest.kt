package com.spybot.core.service

import com.spybot.core.config.SpybotProperties
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.web.reactive.function.client.WebClient

class SteamServiceTest {
    @Test
    fun `an unset or blank Steam API key resolves nothing instead of calling Steam`() {
        // application.yml defaults STEAM_API_KEY to an empty string, which used to be sent to Steam
        // as a real key and come back as a 403 exception.
        for (key in listOf(null, "", "  ")) {
            val builder = Mockito.mock(WebClient.Builder::class.java)
            val service = SteamService(builder, SpybotProperties(steamApiKey = key))

            assertTrue(service.getSteamUsersPlayingInfo(listOf("76561198012345678")).isEmpty(), "key=$key")
            Mockito.verifyNoInteractions(builder)
        }
    }
}
