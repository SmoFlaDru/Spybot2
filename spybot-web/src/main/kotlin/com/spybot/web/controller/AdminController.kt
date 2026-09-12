package com.spybot.web.controller

import com.spybot.core.security.MergedUserPrincipal
import com.spybot.web.jte.PageChromeFactory
import com.spybot.web.jte.flashMessage
import com.spybot.web.jte.renderJte
import com.spybot.web.service.AdminService
import gg.jte.generated.pages.Jteadmin_dashboardGenerated
import gg.jte.generated.pages.Jteadmin_merge_usersGenerated
import gg.jte.generated.pages.Jteadmin_merged_usersGenerated
import gg.jte.generated.pages.Jteadmin_news_event_formGenerated
import gg.jte.generated.pages.Jteadmin_news_eventsGenerated
import gg.jte.generated.pages.Jteadmin_ts_usersGenerated
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/admin")
class AdminController(
    private val adminService: AdminService,
    private val chrome: PageChromeFactory,
) {
    data class MergeUsersForm(
        var targetId: Long? = null,
        var sourceIds: List<Long> = emptyList(),
    )

    data class NewsEventForm(
        @field:NotBlank
        var text: String = "",
        var websiteLink: String? = null,
    )

    @GetMapping
    fun dashboard(
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        Jteadmin_dashboardGenerated.render(out, null, chrome = chrome.of(principal, request), overview = adminService.overview())
    }

    @GetMapping("/merged-users")
    fun mergedUsers(
        @RequestParam(required = false) q: String?,
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        Jteadmin_merged_usersGenerated.render(
            out,
            null,
            chrome = chrome.of(principal, request),
            query = q.orEmpty(),
            users = adminService.mergedUsers(q),
        )
    }

    @GetMapping("/ts-users")
    fun tsUsers(
        @RequestParam(required = false) q: String?,
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        Jteadmin_ts_usersGenerated.render(
            out,
            null,
            chrome = chrome.of(principal, request),
            query = q.orEmpty(),
            users = adminService.tsUsers(q),
        )
    }

    @GetMapping("/news-events")
    fun newsEvents(
        @RequestParam(required = false) q: String?,
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        Jteadmin_news_eventsGenerated.render(
            out,
            null,
            chrome = chrome.of(principal, request),
            query = q.orEmpty(),
            events = adminService.newsEvents(q),
            successMessage = request.flashMessage("successMessage"),
            errorMessage = request.flashMessage("errorMessage"),
        )
    }

    @GetMapping("/news-events/new")
    fun newsEventNew(
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        Jteadmin_news_event_formGenerated.render(
            out,
            null,
            chrome = chrome.of(principal, request),
            form = NewsEventForm(),
            editMode = false,
            eventId = null,
            errorMessage = request.flashMessage("errorMessage"),
        )
    }

    @PostMapping("/news-events")
    fun newsEventCreate(
        @ModelAttribute("form") form: NewsEventForm,
        redirectAttributes: RedirectAttributes,
    ): String {
        if (form.text.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Text is required")
            return "redirect:/admin/news-events/new"
        }
        adminService.createNewsEvent(form.text.trim(), form.websiteLink)
        redirectAttributes.addFlashAttribute("successMessage", "News event created")
        return "redirect:/admin/news-events"
    }

    @GetMapping("/news-events/{id}/edit")
    fun newsEventEdit(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val event = adminService.newsEventById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        response.renderJte { out ->
            Jteadmin_news_event_formGenerated.render(
                out,
                null,
                chrome = chrome.of(principal, request),
                form = NewsEventForm(text = event.text, websiteLink = event.websiteLink),
                editMode = true,
                eventId = event.id,
                errorMessage = request.flashMessage("errorMessage"),
            )
        }
    }

    @PostMapping("/news-events/{id}")
    fun newsEventUpdate(
        @PathVariable id: Long,
        @ModelAttribute("form") form: NewsEventForm,
        redirectAttributes: RedirectAttributes,
    ): String {
        if (form.text.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Text is required")
            return "redirect:/admin/news-events/$id/edit"
        }
        val updated = adminService.updateNewsEvent(id, form.text.trim(), form.websiteLink)
        if (!updated) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        redirectAttributes.addFlashAttribute("successMessage", "News event updated")
        return "redirect:/admin/news-events"
    }

    @PostMapping("/news-events/{id}/delete")
    fun newsEventDelete(
        @PathVariable id: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        if (!adminService.deleteNewsEvent(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        redirectAttributes.addFlashAttribute("successMessage", "News event deleted")
        return "redirect:/admin/news-events"
    }

    @GetMapping("/merge-users")
    fun mergeUsersForm(
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        Jteadmin_merge_usersGenerated.render(
            out,
            null,
            chrome = chrome.of(principal, request),
            form = MergeUsersForm(),
            mergedUsers = adminService.mergedUsers(null),
            successMessage = request.flashMessage("successMessage"),
            errorMessage = request.flashMessage("errorMessage"),
        )
    }

    @PostMapping("/merge-users")
    fun mergeUsers(
        @ModelAttribute("form") form: MergeUsersForm,
        redirectAttributes: RedirectAttributes,
    ): String {
        val targetId = form.targetId
        if (targetId == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Target user is required")
            return "redirect:/admin/merge-users"
        }

        val sourceIds = form.sourceIds.distinct().filter { it != targetId }
        if (sourceIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select at least one source user")
            return "redirect:/admin/merge-users"
        }

        return try {
            val result = adminService.mergeUsers(targetId, sourceIds)
            val summary =
                "Merged ${result.sourceIds.size} user(s) into #${result.targetId}. " +
                    "Moved TS users=${result.movedTsUsers}, Steam IDs=${result.movedSteamIds}, " +
                    "Awards=${result.movedAwards}, Messages=${result.movedQueuedMessages}, " +
                    "Login links=${result.movedLoginLinks}, Passkeys=${result.movedPasskeys}, Name likes=${result.movedNameLikes}. " +
                    "Obsoleted merged users=${result.obsoletedMergedUsers}."
            redirectAttributes.addFlashAttribute("successMessage", summary)
            "redirect:/admin/merge-users"
        } catch (ex: IllegalArgumentException) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.message ?: "Merge validation failed")
            "redirect:/admin/merge-users"
        } catch (ex: IllegalStateException) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.message ?: "Merge failed")
            "redirect:/admin/merge-users"
        }
    }
}
