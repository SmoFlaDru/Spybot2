package com.spybot.web.controller

import com.spybot.core.security.MergedUserPrincipal
import com.spybot.core.service.SpybotQueryService
import com.spybot.web.service.ChangelogService
import com.spybot.web.service.SpybotPageService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus

@Controller
class PageController(
    private val pageService: SpybotPageService,
    private val queryService: SpybotQueryService,
    private val changelogService: ChangelogService,
) {
    @GetMapping("/")
    fun home(
        @RequestParam(name = "timespan", defaultValue = "7") timeSpan: Int,
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        model: Model,
        request: HttpServletRequest,
    ): String {
        model.addAttribute("loggedInUser", pageService.loggedInUser(principal))
        model.addAttribute("home", pageService.home(timeSpan))
        model.addAttribute("csrf", request.getAttribute("_csrf"))
        return "pages/home"
    }

    @GetMapping("/live/")
    fun live(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        model: Model,
        request: HttpServletRequest,
    ): String {
        val (channels, clients) = pageService.live()
        model.addAttribute("loggedInUser", pageService.loggedInUser(principal))
        model.addAttribute("channels", channels)
        model.addAttribute("clients", clients)
        model.addAttribute("csrf", request.getAttribute("_csrf"))
        return "pages/live"
    }

    @GetMapping("/timeline")
    fun timeline(
        @RequestParam(name = "range", defaultValue = "6") rangeHours: Int,
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        model: Model,
        request: HttpServletRequest,
    ): String {
        val (timeRange, series) = queryService.timeline(rangeHours)
        model.addAttribute("loggedInUser", pageService.loggedInUser(principal))
        model.addAttribute("timeRange", timeRange)
        model.addAttribute("activityByUser", series)
        model.addAttribute("csrf", request.getAttribute("_csrf"))
        return "pages/timeline"
    }

    @GetMapping("/halloffame")
    fun hallOfFame(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        model: Model,
        request: HttpServletRequest,
    ): String {
        model.addAttribute("loggedInUser", pageService.loggedInUser(principal))
        model.addAttribute("topUsers", queryService.hallOfFame())
        model.addAttribute("csrf", request.getAttribute("_csrf"))
        return "pages/halloffame"
    }

    @GetMapping("/u/{userId}")
    fun user(
        @PathVariable userId: Long,
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        model: Model,
        request: HttpServletRequest,
    ): String {
        val page = pageService.userPage(userId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        model.addAttribute("loggedInUser", pageService.loggedInUser(principal))
        model.addAttribute("page", page)
        model.addAttribute("csrf", request.getAttribute("_csrf"))
        return "pages/user"
    }

    @GetMapping("/profile")
    fun profile(
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        model: Model,
        request: HttpServletRequest,
    ): String {
        model.addAttribute("loggedInUser", principal.user)
        model.addAttribute("passkeys", queryService.passkeysForUser(principal.id))
        model.addAttribute("steamIds", queryService.steamIdsForUser(principal.id))
        model.addAttribute("csrf", request.getAttribute("_csrf"))
        return "pages/profile"
    }

    @GetMapping("/login")
    fun login(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        model: Model,
        request: HttpServletRequest,
    ): String {
        model.addAttribute("loggedInUser", pageService.loggedInUser(principal))
        model.addAttribute("csrf", request.getAttribute("_csrf"))
        return "pages/login"
    }

    @GetMapping("/login_teamspeak")
    fun loginTeamspeak(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        model: Model,
        request: HttpServletRequest,
    ): String {
        model.addAttribute("loggedInUser", pageService.loggedInUser(principal))
        model.addAttribute("csrf", request.getAttribute("_csrf"))
        return "pages/login_teamspeak"
    }

    @GetMapping("/changelog")
    fun changelog(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        model: Model,
        request: HttpServletRequest,
    ): String {
        model.addAttribute("loggedInUser", pageService.loggedInUser(principal))
        model.addAttribute("entries", changelogService.entries())
        model.addAttribute("csrf", request.getAttribute("_csrf"))
        return "pages/changelog"
    }
}
