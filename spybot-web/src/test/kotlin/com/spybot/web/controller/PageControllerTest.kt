package com.spybot.web.controller

import com.spybot.core.model.ActiveUsersStat
import com.spybot.core.model.ActivityChartView
import com.spybot.core.model.BusiestDayRecord
import com.spybot.core.model.ChannelPopularityEntry
import com.spybot.core.model.HallOfFameEntry
import com.spybot.core.model.HomePageView
import com.spybot.core.model.LikedNameView
import com.spybot.core.model.Liker
import com.spybot.core.model.NameLikeStatus
import com.spybot.core.model.PeakUsersRecord
import com.spybot.core.model.RecentEventView
import com.spybot.core.model.RecentEventsPayload
import com.spybot.core.model.RecordsView
import com.spybot.core.model.SelectorOption
import com.spybot.core.model.StreakRecord
import com.spybot.core.model.TopUserWeek
import com.spybot.core.model.UserRecord
import com.spybot.core.model.WeekTrendView
import com.spybot.core.service.LikedNameService
import com.spybot.core.service.PasskeyQueries
import com.spybot.core.service.StatisticsQueries
import com.spybot.core.service.SteamIdQueries
import com.spybot.web.filter.VisitorIdFilter
import com.spybot.web.jte.PageChromeFactory
import com.spybot.web.service.ChangelogService
import com.spybot.web.service.RecordsService
import com.spybot.web.service.SpybotPageService
import com.spybot.web.service.namegen.GeneratedName
import com.spybot.web.service.namegen.NameGenService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.LocalDate
import java.time.OffsetDateTime

class PageControllerTest {
    private val pageService = Mockito.mock(SpybotPageService::class.java)
    private val passkeyQueries = Mockito.mock(PasskeyQueries::class.java)
    private val statisticsQueries = Mockito.mock(StatisticsQueries::class.java)
    private val steamIdQueries = Mockito.mock(SteamIdQueries::class.java)
    private val nameGenService = Mockito.mock(NameGenService::class.java)
    private val likedNameService = Mockito.mock(LikedNameService::class.java)
    private val recordsService = Mockito.mock(RecordsService::class.java)
    private val chrome = PageChromeFactory(pageService, gitProperties = null, buildProperties = null)
    private val controller =
        PageController(
            pageService,
            passkeyQueries,
            statisticsQueries,
            steamIdQueries,
            ChangelogService(),
            nameGenService,
            likedNameService,
            recordsService,
            chrome,
        )

    private val visitor = Liker.Visitor("0123456789abcdef0123456789abcdef")

    private fun render(block: (HttpServletRequest, HttpServletResponse) -> Unit): String {
        val response = MockHttpServletResponse()
        val request = MockHttpServletRequest().apply { setAttribute(VisitorIdFilter.ATTRIBUTE, visitor.visitorId) }
        block(request, response)
        assertEquals("text/html;charset=UTF-8", response.contentType)
        return response.contentAsString
    }

    @Test
    fun `home renders the page inside the layout with its stats`() {
        val homePage =
            HomePageView(
                activityChart =
                    ActivityChartView(
                        points = emptyList(),
                        options = listOf(SelectorOption(text = "7 days", value = 7, active = true)),
                        activeOptionText = "7 days",
                    ),
                timeOfDay = listOf("08:00" to 1.5),
                topUsersOfWeek = listOf(TopUserWeek(time = 12.0, userName = "Benno", userId = 1)),
                activeUsers = ActiveUsersStat(usersThisWeek = 5, usersToday = 2),
                weekTrend =
                    WeekTrendView(
                        currentWeekSum = 24.0,
                        compareWeekSum = 18.0,
                        fraction = 1.33,
                        deltaPercent = "+33%",
                    ),
                weekComparison = emptyList(),
                channelPopularity = listOf(ChannelPopularityEntry(name = "Lobby", percentage = 50.0)),
                recentEvents =
                    RecentEventsPayload(
                        events =
                            listOf(
                                RecentEventView(
                                    id = 1,
                                    text = "Something happened",
                                    websiteLink = null,
                                    date = OffsetDateTime.parse("2026-04-21T12:00:00Z"),
                                    isRecent = true,
                                ),
                            ),
                        hasMore = false,
                        start = 0,
                    ),
            )
        Mockito.`when`(pageService.loggedInUser(null)).thenReturn(null)
        Mockito.`when`(pageService.home(7)).thenReturn(homePage)

        val html = render { request, response -> controller.home(7, null, request, response) }

        assertTrue("<title>Home" in html || "Home" in html, html.take(300))
        assertTrue("Benno" in html, "top user of the week must be rendered")
        assertTrue("Something happened" in html, "recent events fragment must be rendered inside the page")
        assertTrue("Log in" in html, "an anonymous viewer sees the login link in the layout")
    }

    @Test
    fun `namegen renders the generated name, its like state and the top list`() {
        val generated = GeneratedName("Carry Potter", "Harry Potter", "carry", "Fictional character", "International", 0.97)
        Mockito.`when`(pageService.loggedInUser(null)).thenReturn(null)
        Mockito.`when`(nameGenService.generate()).thenReturn(generated)
        Mockito.`when`(likedNameService.status("Carry Potter", visitor)).thenReturn(NameLikeStatus(likes = 2, likedByMe = false))
        Mockito.`when`(likedNameService.top(NameGenController.TOP_LIMIT, visitor)).thenReturn(emptyList())

        val html = render { request, response -> controller.nameGenerator(null, request, response) }

        assertTrue("Carry Potter" in html, html.take(300))
        assertTrue("Harry Potter" in html)
    }

    @Test
    fun `hall of fame renders the records next to the top users`() {
        Mockito.`when`(pageService.loggedInUser(null)).thenReturn(null)
        Mockito.`when`(statisticsQueries.hallOfFame()).thenReturn(
            listOf(
                HallOfFameEntry(userId = 1, user = "Benno", time = 3_660.0, numGoldAwards = 1, numSilverAwards = 0, numBronzeAwards = 2),
            ),
        )
        Mockito.`when`(recordsService.current()).thenReturn(
            RecordsView(
                longestStreaks =
                    listOf(
                        StreakRecord(
                            userId = 2,
                            userName = "Justus",
                            startDay = LocalDate.of(2024, 3, 1),
                            endDay = LocalDate.of(2024, 3, 21),
                            length = 21,
                        ),
                    ),
                currentStreaks =
                    listOf(
                        StreakRecord(
                            userId = 1,
                            userName = "Benno",
                            startDay = LocalDate.of(2026, 9, 10),
                            endDay = LocalDate.of(2026, 9, 13),
                            length = 4,
                        ),
                    ),
                longestSessions =
                    listOf(
                        UserRecord(userId = 2, userName = "Justus", value = 14 * 3600.0 + 180, date = LocalDate.of(2023, 12, 31)),
                    ),
                bestWeeks = listOf(UserRecord(userId = 1, userName = "Benno", value = 61.4, date = LocalDate.of(2022, 7, 4))),
                peakUsers = PeakUsersRecord(users = 17, at = OffsetDateTime.parse("2021-01-02T21:30:00Z")),
                busiestDay = BusiestDayRecord(day = LocalDate.of(2021, 1, 2), hours = 88.2),
                computedAt = OffsetDateTime.parse("2026-09-13T10:05:00Z"),
            ),
        )

        val html = render { request, response -> controller.hallOfFame(null, request, response) }

        assertTrue("1 h 1 min" in html, "top users use the shared duration formatting")
        assertTrue("21 days" in html && "1 Mar 2024 – 21 Mar 2024" in html, "longest streak with its dates")
        assertTrue("14 h 3 min" in html, "longest session")
        assertTrue("61 h" in html && "week of 4 Jul 2022" in html, "best week")
        assertTrue("17 users" in html, "peak users")
        assertTrue("88 h online in total" in html, "busiest day")
        assertTrue("Current streaks" in html, "current streaks card")
        assertTrue("since 10 Sep 2026" in html, html.substringAfter("Current streaks").take(1500))
        assertTrue("4 days" in html, "current streak length")
        assertTrue(
            "<relative-time datetime=\"2026-09-13T10:05Z\" tense=\"past\">at 10:05 UTC</relative-time>" in html,
            "records footer is a relative time with the absolute time as fallback",
        )
    }

    @Test
    fun `hall of fame says so while the records have not been computed yet`() {
        Mockito.`when`(pageService.loggedInUser(null)).thenReturn(null)
        Mockito.`when`(statisticsQueries.hallOfFame()).thenReturn(emptyList())
        Mockito.`when`(recordsService.current()).thenReturn(null)

        val html = render { request, response -> controller.hallOfFame(null, request, response) }

        assertTrue("Records are being computed" in html, html.take(300))
        assertTrue("Current streaks" !in html)
    }
}
