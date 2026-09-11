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
import com.spybot.core.service.SpybotQueryService
import com.spybot.web.filter.VisitorIdFilter
import com.spybot.web.service.ChangelogService
import com.spybot.web.service.SpybotPageService
import com.spybot.web.service.namegen.GeneratedName
import com.spybot.web.service.namegen.NameGenService
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.ui.ConcurrentModel
import java.time.OffsetDateTime

class PageControllerTest {
    @Test
    fun `home maps to pages home view and populates model`() {
        val pageService = Mockito.mock(SpybotPageService::class.java)
        val queryService = Mockito.mock(SpybotQueryService::class.java)
        val request = Mockito.mock(HttpServletRequest::class.java)
        val csrfToken = Mockito.mock(org.springframework.security.web.csrf.CsrfToken::class.java)
        val model = ConcurrentModel()
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
        Mockito.`when`(request.getAttribute("_csrf")).thenReturn(csrfToken)

        val controller =
            PageController(
                pageService,
                queryService,
                ChangelogService(),
                Mockito.mock(NameGenService::class.java),
                Mockito.mock(LikedNameService::class.java),
            )
        val viewName = controller.home(7, null, model, request)

        assertEquals("pages/home", viewName)
        assertSame(homePage, model.getAttribute("home"))
        assertSame(csrfToken, model.getAttribute("csrf"))
        assertEquals(null, model.getAttribute("loggedInUser"))
    }

    @Test
    fun `namegen maps to pages namegen view and populates a generated name`() {
        val pageService = Mockito.mock(SpybotPageService::class.java)
        val queryService = Mockito.mock(SpybotQueryService::class.java)
        val nameGenService = Mockito.mock(NameGenService::class.java)
        val request = Mockito.mock(HttpServletRequest::class.java)
        val model = ConcurrentModel()
        val generated = GeneratedName("Carry Potter", "Harry Potter", "carry", "Fictional character", "International", 0.97)

        val likedNameService = Mockito.mock(LikedNameService::class.java)
        val visitor = Liker.Visitor("0123456789abcdef0123456789abcdef")
        val top = listOf(LikedNameView("Nuke Skywalker", "Luke Skywalker", "nuke", 9, likedByMe = false))
        val status = NameLikeStatus(likes = 2, likedByMe = true)
        Mockito.`when`(pageService.loggedInUser(null)).thenReturn(null)
        Mockito.`when`(nameGenService.generate()).thenReturn(generated)
        Mockito.`when`(request.getAttribute(VisitorIdFilter.ATTRIBUTE)).thenReturn(visitor.visitorId)
        Mockito.`when`(likedNameService.status("Carry Potter", visitor)).thenReturn(status)
        Mockito.`when`(likedNameService.top(30, visitor)).thenReturn(top)

        val controller = PageController(pageService, queryService, ChangelogService(), nameGenService, likedNameService)
        val viewName = controller.nameGenerator(null, model, request)

        assertEquals("pages/namegen", viewName)
        assertSame(generated, model.getAttribute("generatedName"))
        assertSame(status, model.getAttribute("likeStatus"))
        assertSame(top, model.getAttribute("topNames"))
    }
}
