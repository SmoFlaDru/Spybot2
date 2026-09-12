package com.spybot.web.controller

import com.spybot.core.config.SpybotProperties
import com.spybot.core.model.MergedUserView
import com.spybot.core.model.WebauthnCredential
import com.spybot.core.security.MergedUserPrincipal
import com.spybot.core.service.PasskeyQueries
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime

class PasskeySignalControllerTest {
    private val passkeyQueries = Mockito.mock(PasskeyQueries::class.java)
    private val controller = PasskeySignalController(passkeyQueries, SpybotProperties(publicBaseUrl = "https://spybot.bensge.com"))
    private val principal =
        MergedUserPrincipal(MergedUserView(id = 708, name = "bensge", obsolete = false, isSuperuser = false, lastLogin = null))

    private fun credential(
        handle: String,
        id: String,
    ) = WebauthnCredential(708, handle, id, "pk", 0, false, emptyList(), false, false, "", "att", "cd", "n", "", OffsetDateTime.now(), null)

    @Test
    fun `accepted lists credential ids per handle, including handles that have none left`() {
        Mockito.`when`(passkeyQueries.webauthnUserHandlesForUser(708)).thenReturn(listOf("handle-old", "handle-new"))
        Mockito
            .`when`(
                passkeyQueries.webauthnCredentialsForUser(708),
            ).thenReturn(listOf(credential("handle-new", "cred-1"), credential("handle-new", "cred-2")))

        val accepted = controller.accepted(principal)

        assertEquals("spybot.bensge.com", accepted.rpId)
        assertEquals("708", accepted.name)
        assertEquals("bensge", accepted.displayName)
        assertEquals(
            listOf(
                PasskeySignalController.HandleCredentials("handle-old", emptyList()),
                PasskeySignalController.HandleCredentials("handle-new", listOf("cred-1", "cred-2")),
            ),
            accepted.handles,
        )
    }

    @Test
    fun `known reports whether the server still has the credential`() {
        Mockito.`when`(passkeyQueries.findWebauthnCredential("gone")).thenReturn(null)
        Mockito.`when`(passkeyQueries.findWebauthnCredential("here")).thenReturn(credential("h", "here"))

        assertFalse(controller.known("gone").known)
        assertTrue(controller.known("here").known)
    }

    @Test
    fun `accepted answers 401 when the session went away mid-request`() {
        val error = assertThrows<ResponseStatusException> { controller.accepted(null) }
        assertEquals(HttpStatus.UNAUTHORIZED, error.statusCode)
    }
}
