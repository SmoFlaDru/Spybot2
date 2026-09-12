package com.spybot.web.controller

import com.spybot.core.model.ActiveUsersStat
import com.spybot.core.model.ActivityChartView
import com.spybot.core.model.ChannelPopularityEntry
import com.spybot.core.model.HomePageView
import com.spybot.core.model.LikedNameView
import com.spybot.core.model.Liker
import com.spybot.core.model.NameLikeStatus
import com.spybot.core.model.RecentEventView
import com.spybot.core.model.RecentEventsPayload
import com.spybot.core.model.SelectorOption
import com.spybot.core.model.TopUserWeek
import com.spybot.core.model.WeekTrendView
import com.spybot.core.service.LikedNameService
import com.spybot.core.service.PasskeyQueries
import com.spybot.core.service.StatisticsQueries
import com.spybot.core.service.SteamIdQueries
import com.spybot.web.filter.VisitorIdFilter
import com.spybot.web.jte.PageChromeFactory
import com.spybot.web.service.ChangelogService
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
import java.time.OffsetDateTime

class PageControllerTest {
    private val pageService = Mockito.mock(SpybotPageService::class.java)
    private val passkeyQueries = Mockito.mock(PasskeyQueries::class.java)
    private val statisticsQueries = Mockito.mock(StatisticsQueries::class.java)
    private val steamIdQueries = Mockito.mock(SteamIdQueries::class.java)
    private val nameGenService = Mockito.mock(NameGenService::class.java)
    private val likedNameService = Mockito.mock(LikedNameService::class.java)
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
}
