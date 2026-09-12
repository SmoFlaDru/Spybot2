package com.spybot.web.controller

import com.spybot.core.security.MergedUserPrincipal
import com.spybot.core.service.LikedNameService
import com.spybot.core.service.SpybotQueryService
import com.spybot.web.jte.PageChromeFactory
import com.spybot.web.jte.renderJte
import com.spybot.web.service.ChangelogService
import com.spybot.web.service.SpybotPageService
import com.spybot.web.service.namegen.Likers
import com.spybot.web.service.namegen.NameGenService
import gg.jte.generated.pages.JtechangelogGenerated
import gg.jte.generated.pages.JtehalloffameGenerated
import gg.jte.generated.pages.JtehomeGenerated
import gg.jte.generated.pages.JteliveGenerated
import gg.jte.generated.pages.JteloginGenerated
import gg.jte.generated.pages.Jtelogin_teamspeakGenerated
import gg.jte.generated.pages.JtenamegenGenerated
import gg.jte.generated.pages.JteprofileGenerated
import gg.jte.generated.pages.JtetimelineGenerated
import gg.jte.generated.pages.JteuserGenerated
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
    private val queryService: SpybotQueryService,
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
        JtehomeGenerated.render(out, null, chrome = chrome.of(principal, request), home = pageService.home(timeSpan))
    }

    @GetMapping("/live/")
    fun live(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        val (channels, clients) = pageService.live()
        JteliveGenerated.render(out, null, chrome = chrome.of(principal, request), channels = channels, clients = clients)
    }

    @GetMapping("/timeline")
    fun timeline(
        @RequestParam(name = "range", defaultValue = "6") rangeHours: Int,
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        val (timeRange, series) = queryService.timeline(rangeHours)
        JtetimelineGenerated.render(out, null, chrome = chrome.of(principal, request), timeRange = timeRange, activityByUser = series)
    }

    @GetMapping("/halloffame")
    fun hallOfFame(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        JtehalloffameGenerated.render(out, null, chrome = chrome.of(principal, request), topUsers = queryService.hallOfFame())
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
            JteuserGenerated.render(out, null, chrome = chrome.of(principal, request), page = page)
        }
    }

    @GetMapping("/profile")
    fun profile(
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        val passkeys = queryService.passkeysForUser(principal.id)
        JteprofileGenerated.render(
            out,
            null,
            chrome = chrome.of(principal, request),
            user = principal.user,
            passkeys = passkeys,
            steamIds = queryService.steamIdsForUser(principal.id),
            passkeyPrompt = request.getParameter("passkey-prompt") != null && passkeys.isEmpty(),
        )
    }

    @GetMapping("/login")
    fun login(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        JteloginGenerated.render(out, null, chrome = chrome.of(principal, request))
    }

    @GetMapping("/login_teamspeak")
    fun loginTeamspeak(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        Jtelogin_teamspeakGenerated.render(out, null, chrome = chrome.of(principal, request))
    }

    @GetMapping("/changelog")
    fun changelog(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        JtechangelogGenerated.render(out, null, chrome = chrome.of(principal, request), entries = changelogService.entries())
    }

    @GetMapping("/namegen")
    fun nameGenerator(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        val liker = Likers.of(principal, request)
        val generatedName = nameGenService.generate()
        JtenamegenGenerated.render(
            out,
            null,
            chrome = chrome.of(principal, request),
            generatedName = generatedName,
            likeStatus = likedNameService.status(generatedName.displayName, liker),
            topNames = likedNameService.top(NameGenController.TOP_LIMIT, liker),
        )
    }
}
