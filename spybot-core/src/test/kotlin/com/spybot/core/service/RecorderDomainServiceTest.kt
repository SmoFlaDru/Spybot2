package com.spybot.core.service

import com.spybot.core.config.SpybotProperties
import com.spybot.core.model.QueuedClientMessageView
import com.spybot.core.model.TeamSpeakClientSnapshot
import com.spybot.core.model.TeamSpeakIdentity
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RecorderDomainServiceTest {
    @Test
    fun `handleInitialClients creates login link and flushes queued messages`() {
        val queryService: SpybotQueryService = mock()
        val gateway: RecorderMessageGateway = mock()
        val identity = TeamSpeakIdentity(5, 7, "Alice", "Alice")
        whenever(queryService.openSessions()).thenReturn(emptyList())
        whenever(queryService.findIdentityByUniqueIdentifier("uid-1")).thenReturn(identity)
        whenever(queryService.renameIdentity(identity, "Alice")).thenReturn(identity)
        whenever(queryService.queuedMessagesForMergedUser(7)).thenReturn(
            listOf(QueuedClientMessageView(11, 7, "Queued text", "AWARD_USER_OF_WEEK")),
        )

        val service = RecorderDomainService(queryService, SpybotProperties(publicBaseUrl = "https://spybot.local"))

        service.handleInitialClients(
            listOf(TeamSpeakClientSnapshot(42, 3, 1, "Alice", "0", "uid-1")),
            gateway,
        )

        verify(queryService).markClientSessionStarted(5, 3, 42, true)
        verify(queryService).createLoginLink(eq(7L), any())
        verify(gateway).sendTextMessage(42, "Queued text")
        verify(queryService).deleteQueuedMessage(11)
    }

    @Test
    fun `handleInitialClients ignores bot users`() {
        val queryService: SpybotQueryService = mock()
        val gateway: RecorderMessageGateway = mock()
        whenever(queryService.openSessions()).thenReturn(emptyList())

        val service = RecorderDomainService(queryService, SpybotProperties(publicBaseUrl = "https://spybot.local"))
        service.handleInitialClients(
            listOf(TeamSpeakClientSnapshot(42, 3, 1, "TS Bot", "1", "uid-bot")),
            gateway,
        )

        verify(queryService, never()).createTeamSpeakIdentity(any(), any(), any())
    }
}
