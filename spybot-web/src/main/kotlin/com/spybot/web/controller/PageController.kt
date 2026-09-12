package com.spybot.web.controller

import com.spybot.core.security.MergedUserPrincipal
import com.spybot.core.service.LikedNameService
import com.spybot.core.service.PasskeyQueries
import com.spybot.core.service.StatisticsQueries
import com.spybot.core.service.SteamIdQueries
import com.spybot.web.jte.PageChromeFactory
import com.spybot.web.jte.renderJte
import com.spybot.web.service.ChangelogService
import com.spybot.web.service.SpybotPageService
import com.spybot.web.service.namegen.Likers
import com.spybot.web.service.namegen.NameGenService
import gg.jte.generated.pages.JteChangelogGenerated
import gg.jte.generated.pages.JteHallOfFameGenerated
import gg.jte.generated.pages.JteHomeGenerated
import gg.jte.generated.pages.JteLiveGenerated
import gg.jte.generated.pages.JteLoginGenerated
import gg.jte.generated.pages.JteLoginTeamSpeakGenerated
import gg.jte.generated.pages.JteNameGenGenerated
import gg.jte.generated.pages.JteProfileGenerated
import gg.jte.generated.pages.JteTimelineGenerated
import gg.jte.generated.pages.JteUserGenerated
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException

@Controller
class PageController(
    private val pageService: SpybotPageService,
    private val passkeyQueries: PasskeyQueries,
    private val statisticsQueries: StatisticsQueries,
    private val steamIdQueries: SteamIdQueries,
    private val changelogService: ChangelogService,
    private val nameGenService: NameGenService,
    private val likedNameService: LikedNameService,
    private val chrome: PageChromeFactory,
) {
    @GetMapping("/")
    fun home(
        @RequestParam(name = "timespan", defaultValue = "7") timeSpan: Int,
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        JteHomeGenerated.render(out, null, chrome = chrome.of(principal, request), home = pageService.home(timeSpan))
    }

    @GetMapping("/live/")
    fun live(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        val (channels, clients) = pageService.live()
        JteLiveGenerated.render(out, null, chrome = chrome.of(principal, request), channels = channels, clients = clients)
    }

    @GetMapping("/timeline")
    fun timeline(
        @RequestParam(name = "range", defaultValue = "6") rangeHours: Int,
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        val (timeRange, series) = statisticsQueries.timeline(rangeHours)
        JteTimelineGenerated.render(out, null, chrome = chrome.of(principal, request), timeRange = timeRange, activityByUser = series)
    }

    @GetMapping("/halloffame")
    fun hallOfFame(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        JteHallOfFameGenerated.render(out, null, chrome = chrome.of(principal, request), topUsers = statisticsQueries.hallOfFame())
    }

    @GetMapping("/u/{userId}")
    fun user(
        @PathVariable userId: Long,
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val page = pageService.userPage(userId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        response.renderJte { out ->
            JteUserGenerated.render(out, null, chrome = chrome.of(principal, request), page = page)
        }
    }

    @GetMapping("/profile")
    fun profile(
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        val passkeys = passkeyQueries.passkeysForUser(principal.id)
        JteProfileGenerated.render(
            out,
            null,
            chrome = chrome.of(principal, request),
            user = principal.user,
            passkeys = passkeys,
            steamIds = steamIdQueries.steamIdsForUser(principal.id),
            passkeyPrompt = request.getParameter("passkey-prompt") != null && passkeys.isEmpty(),
        )
    }

    @GetMapping("/login")
    fun login(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        JteLoginGenerated.render(out, null, chrome = chrome.of(principal, request))
    }

    @GetMapping("/login_teamspeak")
    fun loginTeamspeak(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        JteLoginTeamSpeakGenerated.render(out, null, chrome = chrome.of(principal, request))
    }

    @GetMapping("/changelog")
    fun changelog(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        JteChangelogGenerated.render(out, null, chrome = chrome.of(principal, request), entries = changelogService.entries())
    }

    @GetMapping("/namegen")
    fun nameGenerator(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        val liker = Likers.of(principal, request)
        val generatedName = nameGenService.generate()
        JteNameGenGenerated.render(
            out,
            null,
            chrome = chrome.of(principal, request),
            generatedName = generatedName,
            likeStatus = likedNameService.status(generatedName.displayName, liker),
            topNames = likedNameService.top(NameGenController.TOP_LIMIT, liker),
        )
    }
}
