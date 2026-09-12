package com.spybot.web.controller

import com.spybot.core.model.MergedUserView
import com.spybot.core.model.OnlineStatus
import com.spybot.core.model.SteamAccountInfo
import com.spybot.core.security.MergedUserPrincipal
import com.spybot.core.service.SpybotQueryService
import com.spybot.core.service.SteamService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.http.HttpStatus

class ProfileControllerTest {
    private val principal =
        MergedUserPrincipal(
            MergedUserView(id = 1, name = "Benno", obsolete = false, isSuperuser = false, lastLogin = null),
        )

    @Test
    fun `addSteamId rejects a steam id the Steam API cannot resolve`() {
        val queryService = Mockito.mock(SpybotQueryService::class.java)
        val steamService = Mockito.mock(SteamService::class.java)
        Mockito.`when`(steamService.getSteamUsersPlayingInfo(listOf("123456789"))).thenReturn(emptyList())
        val controller = ProfileController(queryService, steamService)

        val response = controller.addSteamId(principal, "123456789", "Some Name")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        Mockito.verify(queryService, Mockito.never()).addSteamId(anyLong(), anyLong(), anyString())
    }

    @Test
    fun `addSteamId stores the steam id when the Steam API resolves it`() {
        val queryService = Mockito.mock(SpybotQueryService::class.java)
        val steamService = Mockito.mock(SteamService::class.java)
        Mockito.`when`(steamService.getSteamUsersPlayingInfo(listOf("123456789"))).thenReturn(
            listOf(SteamAccountInfo(steamId = "123456789", gameId = 0, gameName = "", avatarUrl = "", onlineStatus = OnlineStatus.ONLINE)),
        )
        val controller = ProfileController(queryService, steamService)

        val response = controller.addSteamId(principal, "123456789", "Some Name")

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        Mockito.verify(queryService).addSteamId(1, 123456789L, "Some Name")
    }

    @Test
    fun `renamePasskey trims the name, updates it and tells HTMX to refresh the list`() {
        val queryService = Mockito.mock(SpybotQueryService::class.java)
        Mockito.`when`(queryService.renamePasskey(7L, 42L, "Work laptop")).thenReturn(true)
        val controller = ProfileController(queryService, Mockito.mock(SteamService::class.java))

        val response = controller.renamePasskey(principal(7L), 42L, "  Work laptop ")

        assertEquals(204, response.statusCode.value())
        assertEquals("passkeys_changed", response.headers.getFirst("HX-Trigger"))
    }

    @Test
    fun `renamePasskey refuses a passkey that is not the user's`() {
        val queryService = Mockito.mock(SpybotQueryService::class.java)
        Mockito.`when`(queryService.renamePasskey(7L, 42L, "Mine now")).thenReturn(false)
        val controller = ProfileController(queryService, Mockito.mock(SteamService::class.java))

        val response = controller.renamePasskey(principal(7L), 42L, "Mine now")

        assertEquals(403, response.statusCode.value())
    }

    private fun principal(id: Long) =
        com.spybot.core.security.MergedUserPrincipal(
            com.spybot.core.model
                .MergedUserView(id = id, name = "user$id", obsolete = false, isSuperuser = false, lastLogin = null),
        )
}
