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
        val awardQueries: AwardQueries = mock()
        val mergedUserQueries: MergedUserQueries = mock()
        val newsEventQueries: NewsEventQueries = mock()
        val queuedMessageQueries: QueuedMessageQueries = mock()
        val statisticsQueries: StatisticsQueries = mock()
        whenever(statisticsQueries.weeklyAwardCandidates()).thenReturn(
            listOf(
                TopUserWeek(12.0, "Alice", 1),
                TopUserWeek(11.0, "Bob", 2),
                TopUserWeek(10.0, "Cara", 3),
            ),
        )
        whenever(mergedUserQueries.mergedUserName(1)).thenReturn("Alice")
        whenever(mergedUserQueries.mergedUserName(2)).thenReturn("Bob")
        whenever(mergedUserQueries.mergedUserName(3)).thenReturn("Cara")
        whenever(awardQueries.countAwardsForUser(1)).thenReturn(1)
        whenever(awardQueries.countAwardsForUser(2)).thenReturn(1)
        whenever(awardQueries.countAwardsForUser(3)).thenReturn(1)
        whenever(awardQueries.countAwardsForUserByPoints(1, 3)).thenReturn(1)
        whenever(awardQueries.countAwardsForUserByPoints(2, 2)).thenReturn(1)
        whenever(awardQueries.countAwardsForUserByPoints(3, 1)).thenReturn(1)

        val service = AwardService(awardQueries, mergedUserQueries, newsEventQueries, queuedMessageQueries, statisticsQueries, SpybotProperties(publicBaseUrl = "https://spybot.local"))

        val awarded = service.runEndOfWeekAwards()

        assertEquals(3, awarded)
        verify(awardQueries).createAward(1, 3)
        verify(awardQueries).createAward(2, 2)
        verify(awardQueries).createAward(3, 1)
        verify(newsEventQueries, times(3)).createNewsEvent(any(), any())
        verify(queuedMessageQueries, times(3)).replaceQueuedMessage(any(), eq("AWARD_USER_OF_WEEK"), any())
    }
}
