package com.spybot.web.controller

import com.spybot.core.security.MergedUserPrincipal
import com.spybot.core.service.LikedNameService
import com.spybot.core.service.SpybotQueryService
import com.spybot.web.service.SpybotPageService
import com.spybot.web.service.namegen.Likers
import com.spybot.web.service.namegen.NameGenService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException

@Controller
class FragmentController(
    private val pageService: SpybotPageService,
    private val queryService: SpybotQueryService,
    private val nameGenService: NameGenService,
    private val likedNameService: LikedNameService,
) {
    @GetMapping("/live_fragment")
    fun liveFragment(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        model: Model,
        request: HttpServletRequest,
    ): String {
        val (channels, clients) = pageService.live()
        model.addAttribute("loggedInUser", pageService.loggedInUser(principal))
        model.addAttribute("channels", channels)
        model.addAttribute("clients", clients)
        model.addAttribute("csrf", request.getAttribute("_csrf"))
        return "fragments/live_fragment"
    }

    @GetMapping("/activity_fragment")
    fun activityFragment(
        @RequestParam(name = "timespan", defaultValue = "7") timeSpan: Int,
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        model: Model,
        request: HttpServletRequest,
    ): String {
        model.addAttribute("loggedInUser", pageService.loggedInUser(principal))
        model.addAttribute("activityChart", queryService.activityChart(timeSpan))
        model.addAttribute("csrf", request.getAttribute("_csrf"))
        return "fragments/activity_fragment"
    }

    @GetMapping("/recent_events_fragment")
    fun recentEventsFragment(
        @RequestParam(name = "start", defaultValue = "0") start: Int,
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        model: Model,
        request: HttpServletRequest,
    ): String {
        model.addAttribute("loggedInUser", pageService.loggedInUser(principal))
        model.addAttribute("recentEvents", queryService.recentEvents(start))
        model.addAttribute("csrf", request.getAttribute("_csrf"))
        return "fragments/recent_events_fragment"
    }

    @GetMapping("/profile/steamid/all")
    fun profileSteamIdsFragment(
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        model: Model,
        request: HttpServletRequest,
    ): String {
        model.addAttribute("loggedInUser", principal.user)
        model.addAttribute("steamIds", queryService.steamIdsForUser(principal.id))
        model.addAttribute("csrf", request.getAttribute("_csrf"))
        return "fragments/profile_steamids"
    }

    @GetMapping("/profile/steamid")
    fun addSteamIdModal(): String = "fragments/add_steamid_modal"

    @GetMapping("/profile/passkey/all")
    fun profilePasskeysFragment(
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        model: Model,
    ): String {
        model.addAttribute("passkeys", queryService.passkeysForUser(principal.id))
        return "fragments/profile_passkeys"
    }

    @GetMapping("/profile/passkey/{id}/rename")
    fun renamePasskeyForm(
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        @PathVariable id: Long,
        model: Model,
    ): String {
        val passkey =
            queryService.passkeysForUser(principal.id).firstOrNull { it.id == id }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        model.addAttribute("passkey", passkey)
        return "fragments/profile_passkey_rename"
    }

    @GetMapping("/namegen_fragment")
    fun nameGeneratorFragment(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        model: Model,
        request: HttpServletRequest,
    ): String {
        val generatedName = nameGenService.generate()
        model.addAttribute("generatedName", generatedName)
        model.addAttribute("likeStatus", likedNameService.status(generatedName.displayName, Likers.of(principal, request)))
        return "fragments/namegen_fragment"
    }
}
