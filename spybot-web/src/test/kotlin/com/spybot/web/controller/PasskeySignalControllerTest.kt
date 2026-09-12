package com.spybot.web.controller

import com.spybot.core.config.SpybotProperties
import com.spybot.core.model.MergedUserView
import com.spybot.core.model.WebauthnCredential
import com.spybot.core.security.MergedUserPrincipal
import com.spybot.core.service.SpybotQueryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.OffsetDateTime

class PasskeySignalControllerTest {
    private val queryService = Mockito.mock(SpybotQueryService::class.java)
    private val controller = PasskeySignalController(queryService, SpybotProperties(publicBaseUrl = "https://spybot.bensge.com"))
    private val principal = MergedUserPrincipal(MergedUserView(id = 708, name = "bensge", obsolete = false, isSuperuser = false, lastLogin = null))

    private fun credential(
        handle: String,
        id: String,
    ) = WebauthnCredential(708, handle, id, "pk", 0, false, emptyList(), false, false, "", "att", "cd", "n", "", OffsetDateTime.now(), null)

    @Test
    fun `accepted lists credential ids per handle, including handles that have none left`() {
        Mockito.`when`(queryService.webauthnUserHandlesForUser(708)).thenReturn(listOf("handle-old", "handle-new"))
        Mockito.`when`(queryService.webauthnCredentialsForUser(708)).thenReturn(listOf(credential("handle-new", "cred-1"), credential("handle-new", "cred-2")))

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
        Mockito.`when`(queryService.findWebauthnCredential("gone")).thenReturn(null)
        Mockito.`when`(queryService.findWebauthnCredential("here")).thenReturn(credential("h", "here"))

        assertFalse(controller.known("gone").known)
        assertTrue(controller.known("here").known)
    }
}
