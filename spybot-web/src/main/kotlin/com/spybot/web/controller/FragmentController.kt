package com.spybot.web.controller

import com.spybot.core.security.MergedUserPrincipal
import com.spybot.core.service.LikedNameService
import com.spybot.core.service.PasskeyQueries
import com.spybot.core.service.StatisticsQueries
import com.spybot.core.service.SteamIdQueries
import com.spybot.web.jte.renderJte
import com.spybot.web.service.SpybotPageService
import com.spybot.web.service.namegen.Likers
import com.spybot.web.service.namegen.NameGenService
import gg.jte.generated.fragments.Jteactivity_fragmentGenerated
import gg.jte.generated.fragments.Jteadd_steamid_modalGenerated
import gg.jte.generated.fragments.Jtelive_fragmentGenerated
import gg.jte.generated.fragments.Jtenamegen_fragmentGenerated
import gg.jte.generated.fragments.Jteprofile_passkey_renameGenerated
import gg.jte.generated.fragments.Jteprofile_passkeysGenerated
import gg.jte.generated.fragments.Jteprofile_steamidsGenerated
import gg.jte.generated.fragments.Jterecent_events_fragmentGenerated
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException

/** HTMX partials: each renders one fragment template, never the page layout. */
@Controller
class FragmentController(
    private val pageService: SpybotPageService,
    private val passkeyQueries: PasskeyQueries,
    private val statisticsQueries: StatisticsQueries,
    private val steamIdQueries: SteamIdQueries,
    private val nameGenService: NameGenService,
    private val likedNameService: LikedNameService,
) {
    @GetMapping("/live_fragment")
    fun liveFragment(response: HttpServletResponse) =
        response.renderJte { out ->
            val (channels, clients) = pageService.live()
            Jtelive_fragmentGenerated.render(out, null, channels = channels, clients = clients)
        }

    @GetMapping("/activity_fragment")
    fun activityFragment(
        @RequestParam(name = "timespan", defaultValue = "7") timeSpan: Int,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        Jteactivity_fragmentGenerated.render(out, null, activityChart = statisticsQueries.activityChart(timeSpan))
    }

    @GetMapping("/recent_events_fragment")
    fun recentEventsFragment(
        @RequestParam(name = "start", defaultValue = "0") start: Int,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        Jterecent_events_fragmentGenerated.render(out, null, recentEvents = statisticsQueries.recentEvents(start))
    }

    @GetMapping("/profile/steamid/all")
    fun profileSteamIdsFragment(
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        Jteprofile_steamidsGenerated.render(out, null, steamIds = steamIdQueries.steamIdsForUser(principal.id))
    }

    @GetMapping("/profile/steamid")
    fun addSteamIdModal(response: HttpServletResponse) =
        response.renderJte { out ->
            Jteadd_steamid_modalGenerated.render(out, null)
        }

    @GetMapping("/profile/passkey/all")
    fun profilePasskeysFragment(
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        Jteprofile_passkeysGenerated.render(out, null, passkeys = passkeyQueries.passkeysForUser(principal.id))
    }

    @GetMapping("/profile/passkey/{id}/rename")
    fun renamePasskeyForm(
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        @PathVariable id: Long,
        response: HttpServletResponse,
    ) {
        val passkey =
            passkeyQueries.passkeysForUser(principal.id).firstOrNull { it.id == id }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        response.renderJte { out ->
            Jteprofile_passkey_renameGenerated.render(out, null, passkey = passkey)
        }
    }

    @GetMapping("/namegen_fragment")
    fun nameGeneratorFragment(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        val generatedName = nameGenService.generate()
        Jtenamegen_fragmentGenerated.render(
            out,
            null,
            generatedName = generatedName,
            likeStatus = likedNameService.status(generatedName.displayName, Likers.of(principal, request)),
        )
    }
}
