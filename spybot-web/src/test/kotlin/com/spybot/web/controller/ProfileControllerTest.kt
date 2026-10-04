package com.spybot.web.controller

import com.spybot.core.model.MergedUserView
import com.spybot.core.model.OnlineStatus
import com.spybot.core.model.SteamAccountInfo
import com.spybot.core.security.MergedUserPrincipal
import com.spybot.core.service.PasskeyQueries
import com.spybot.core.service.SteamIdQueries
import com.spybot.core.service.SteamService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
        val steamIdQueries = Mockito.mock(SteamIdQueries::class.java)
        val steamService = Mockito.mock(SteamService::class.java)
        Mockito.`when`(steamService.getSteamUsersPlayingInfo(listOf("123456789"))).thenReturn(emptyList())
        val controller = ProfileController(Mockito.mock(PasskeyQueries::class.java), steamIdQueries, steamService)

        val response = controller.addSteamId(principal, "123456789", "Some Name")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        Mockito.verify(steamIdQueries, Mockito.never()).addSteamId(anyLong(), anyLong(), anyString())
    }

    @Test
    fun `addSteamId stores the steam id when the Steam API resolves it`() {
        val steamIdQueries = Mockito.mock(SteamIdQueries::class.java)
        val steamService = Mockito.mock(SteamService::class.java)
        Mockito.`when`(steamService.getSteamUsersPlayingInfo(listOf("123456789"))).thenReturn(
            listOf(SteamAccountInfo(steamId = "123456789", gameId = 0, gameName = "", avatarUrl = "", onlineStatus = OnlineStatus.ONLINE)),
        )
        val controller = ProfileController(Mockito.mock(PasskeyQueries::class.java), steamIdQueries, steamService)

        val response = controller.addSteamId(principal, "123456789", "Some Name")

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        Mockito.verify(steamIdQueries).addSteamId(1, 123456789L, "Some Name")
    }

    @Test
    fun `addSteamId explains what a valid Steam ID looks like and never calls Steam for a malformed one`() {
        val steamIdQueries = Mockito.mock(SteamIdQueries::class.java)
        val steamService = Mockito.mock(SteamService::class.java)
        val controller = ProfileController(Mockito.mock(PasskeyQueries::class.java), steamIdQueries, steamService)

        // Too short, letters, a URL pasted in, and 20 digits that overflow a Long.
        for (malformed in listOf("1234", "abc12345", "https://steamcommunity.com/profiles/1", "99999999999999999999")) {
            val response = controller.addSteamId(principal, malformed, "Some Name")

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode, malformed)
            assertTrue(response.body!!.startsWith("That doesn't look like a valid Steam ID."), malformed)
            assertTrue("5 to 20 digits" in response.body!!, malformed)
        }
        Mockito.verifyNoInteractions(steamService)
        Mockito.verify(steamIdQueries, Mockito.never()).addSteamId(anyLong(), anyLong(), anyString())
    }

    @Test
    fun `addSteamId asks for missing fields and caps the account name length`() {
        val steamService = Mockito.mock(SteamService::class.java)
        val controller = ProfileController(Mockito.mock(PasskeyQueries::class.java), Mockito.mock(SteamIdQueries::class.java), steamService)

        assertEquals("Please enter the Steam account ID.", controller.addSteamId(principal, "  ", "Some Name").body)
        assertEquals("Please enter an account name.", controller.addSteamId(principal, "123456789", "   ").body)
        assertEquals(
            "The account name can be at most 128 characters long.",
            controller.addSteamId(principal, "123456789", "x".repeat(129)).body,
        )
        Mockito.verifyNoInteractions(steamService)
    }

    @Test
    fun `addSteamId trims the submitted values before checking and storing them`() {
        val steamIdQueries = Mockito.mock(SteamIdQueries::class.java)
        val steamService = Mockito.mock(SteamService::class.java)
        Mockito.`when`(steamService.getSteamUsersPlayingInfo(listOf("123456789"))).thenReturn(
            listOf(SteamAccountInfo(steamId = "123456789", gameId = 0, gameName = "", avatarUrl = "", onlineStatus = OnlineStatus.ONLINE)),
        )
        val controller = ProfileController(Mockito.mock(PasskeyQueries::class.java), steamIdQueries, steamService)

        val response = controller.addSteamId(principal, " 123456789 ", "  Some Name ")

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        Mockito.verify(steamIdQueries).addSteamId(1, 123456789L, "Some Name")
    }

    @Test
    fun `addSteamId answers 503 with a readable message when Steam cannot be reached`() {
        val steamIdQueries = Mockito.mock(SteamIdQueries::class.java)
        val steamService = Mockito.mock(SteamService::class.java)
        Mockito.`when`(steamService.getSteamUsersPlayingInfo(listOf("123456789"))).thenThrow(IllegalStateException("timeout"))
        val controller = ProfileController(Mockito.mock(PasskeyQueries::class.java), steamIdQueries, steamService)

        val response = controller.addSteamId(principal, "123456789", "Some Name")

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals("Steam could not be reached to verify this ID. Please try again in a moment.", response.body)
        Mockito.verify(steamIdQueries, Mockito.never()).addSteamId(anyLong(), anyLong(), anyString())
    }

    @Test
    fun `renamePasskey trims the name, updates it and tells HTMX to refresh the list`() {
        val passkeyQueries = Mockito.mock(PasskeyQueries::class.java)
        Mockito.`when`(passkeyQueries.renamePasskey(7L, 42L, "Work laptop")).thenReturn(true)
        val controller = ProfileController(passkeyQueries, Mockito.mock(SteamIdQueries::class.java), Mockito.mock(SteamService::class.java))

        val response = controller.renamePasskey(principal(7L), 42L, "  Work laptop ")

        assertEquals(204, response.statusCode.value())
        assertEquals("passkeys_changed", response.headers.getFirst("HX-Trigger"))
    }

    @Test
    fun `renamePasskey refuses a passkey that is not the user's`() {
        val passkeyQueries = Mockito.mock(PasskeyQueries::class.java)
        Mockito.`when`(passkeyQueries.renamePasskey(7L, 42L, "Mine now")).thenReturn(false)
        val controller = ProfileController(passkeyQueries, Mockito.mock(SteamIdQueries::class.java), Mockito.mock(SteamService::class.java))

        val response = controller.renamePasskey(principal(7L), 42L, "Mine now")

        assertEquals(403, response.statusCode.value())
    }

    private fun principal(id: Long) =
        com.spybot.core.security.MergedUserPrincipal(
            com.spybot.core.model
                .MergedUserView(id = id, name = "user$id", obsolete = false, isSuperuser = false, lastLogin = null),
        )
}
