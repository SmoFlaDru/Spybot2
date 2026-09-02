package com.spybot.core.service

import com.spybot.core.config.SpybotProperties
import com.spybot.core.model.OpenSessionView
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

    @Test
    fun `handleInitialClients closes only the stale row, not a sibling row for the same user`() {
        // Regression test: a user can briefly have two open tsuseractivity rows for the same
        // tsUserId (e.g. a reconnect's ClientEnter is processed before the old connection's
        // ClientLeave). Closing "the stale session" must not also close a sibling row that still
        // matches a currently connected client, or that live session is silently dropped from the
        // live view with nothing left to recreate it.
        val queryService: SpybotQueryService = mock()
        val gateway: RecorderMessageGateway = mock()
        val staleSession = OpenSessionView(id = 100, tsUserId = 5, clientId = 42, channelId = 3, tsUserName = "Alice")
        val liveSession = OpenSessionView(id = 101, tsUserId = 5, clientId = 43, channelId = 3, tsUserName = "Alice")
        whenever(queryService.openSessions()).thenReturn(listOf(staleSession, liveSession))

        val service = RecorderDomainService(queryService, SpybotProperties(publicBaseUrl = "https://spybot.local"))

        service.handleInitialClients(
            listOf(TeamSpeakClientSnapshot(43, 3, 1, "Alice", "0", "uid-1")),
            gateway,
        )

        verify(queryService).closeOpenSession(100, 5, -2)
        verify(queryService, never()).closeOpenSession(eq(101), any(), any())
        verify(queryService, never()).closeOpenSessionsForUser(any(), any())
    }
}
