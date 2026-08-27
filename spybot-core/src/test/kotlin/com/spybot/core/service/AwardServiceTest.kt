package com.spybot.core.service

import com.spybot.core.config.SpybotProperties
import com.spybot.core.model.TopUserWeek
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AwardServiceTest {
    @Test
    fun `runEndOfWeekAwards creates awards events and messages`() {
        val queryService: SpybotQueryService = mock()
        whenever(queryService.weeklyAwardCandidates()).thenReturn(
            listOf(
                TopUserWeek(12.0, "Alice", 1),
                TopUserWeek(11.0, "Bob", 2),
                TopUserWeek(10.0, "Cara", 3),
            ),
        )
        whenever(queryService.mergedUserName(1)).thenReturn("Alice")
        whenever(queryService.mergedUserName(2)).thenReturn("Bob")
        whenever(queryService.mergedUserName(3)).thenReturn("Cara")
        whenever(queryService.countAwardsForUser(1)).thenReturn(1)
        whenever(queryService.countAwardsForUser(2)).thenReturn(1)
        whenever(queryService.countAwardsForUser(3)).thenReturn(1)
        whenever(queryService.countAwardsForUserByPoints(1, 3)).thenReturn(1)
        whenever(queryService.countAwardsForUserByPoints(2, 2)).thenReturn(1)
        whenever(queryService.countAwardsForUserByPoints(3, 1)).thenReturn(1)

        val service = AwardService(queryService, SpybotProperties(publicBaseUrl = "https://spybot.local"))

        val awarded = service.runEndOfWeekAwards()

        assertEquals(3, awarded)
        verify(queryService).createAward(1, 3)
        verify(queryService).createAward(2, 2)
        verify(queryService).createAward(3, 1)
        verify(queryService, times(3)).createNewsEvent(any(), any())
        verify(queryService, times(3)).replaceQueuedMessage(any(), eq("AWARD_USER_OF_WEEK"), any())
    }
}
