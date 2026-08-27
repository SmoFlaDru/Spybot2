package com.spybot.web.controller

import com.spybot.core.security.MergedUserPrincipal
import com.spybot.web.service.AdminService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.constraints.NotBlank
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus

@Controller
@RequestMapping("/admin")
class AdminController(
    private val adminService: AdminService,
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
        model: Model,
        request: HttpServletRequest,
    ): String {
        model.addAttribute("loggedInUser", principal.user)
        model.addAttribute("csrf", request.getAttribute("_csrf"))
        model.addAttribute("activePage", "admin")
        model.addAttribute("overview", adminService.overview())
        return "pages/admin_dashboard"
    }

    @GetMapping("/merged-users")
    fun mergedUsers(
        @RequestParam(required = false) q: String?,
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        model: Model,
        request: HttpServletRequest,
    ): String {
        model.addAttribute("loggedInUser", principal.user)
        model.addAttribute("csrf", request.getAttribute("_csrf"))
        model.addAttribute("activePage", "admin")
        model.addAttribute("query", q.orEmpty())
        model.addAttribute("users", adminService.mergedUsers(q))
        return "pages/admin_merged_users"
    }

    @GetMapping("/ts-users")
    fun tsUsers(
        @RequestParam(required = false) q: String?,
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        model: Model,
        request: HttpServletRequest,
    ): String {
        model.addAttribute("loggedInUser", principal.user)
        model.addAttribute("csrf", request.getAttribute("_csrf"))
        model.addAttribute("activePage", "admin")
        model.addAttribute("query", q.orEmpty())
        model.addAttribute("users", adminService.tsUsers(q))
        return "pages/admin_ts_users"
    }

    @GetMapping("/news-events")
    fun newsEvents(
        @RequestParam(required = false) q: String?,
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        model: Model,
        request: HttpServletRequest,
    ): String {
        model.addAttribute("loggedInUser", principal.user)
        model.addAttribute("csrf", request.getAttribute("_csrf"))
        model.addAttribute("activePage", "admin")
        model.addAttribute("query", q.orEmpty())
        model.addAttribute("events", adminService.newsEvents(q))
        ensureFlashAttributesPresent(model)
        return "pages/admin_news_events"
    }

    @GetMapping("/news-events/new")
    fun newsEventNew(
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        model: Model,
        request: HttpServletRequest,
    ): String {
        model.addAttribute("loggedInUser", principal.user)
        model.addAttribute("csrf", request.getAttribute("_csrf"))
        model.addAttribute("activePage", "admin")
        model.addAttribute("form", NewsEventForm())
        model.addAttribute("editMode", false)
        model.addAttribute("eventId", null)
        ensureFlashAttributesPresent(model, includeSuccess = false)
        return "pages/admin_news_event_form"
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
        model: Model,
        request: HttpServletRequest,
    ): String {
        val event = adminService.newsEventById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        model.addAttribute("loggedInUser", principal.user)
        model.addAttribute("csrf", request.getAttribute("_csrf"))
        model.addAttribute("activePage", "admin")
        model.addAttribute("form", NewsEventForm(text = event.text, websiteLink = event.websiteLink))
        model.addAttribute("editMode", true)
        model.addAttribute("eventId", event.id)
        ensureFlashAttributesPresent(model, includeSuccess = false)
        return "pages/admin_news_event_form"
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
        model: Model,
        request: HttpServletRequest,
    ): String {
        model.addAttribute("loggedInUser", principal.user)
        model.addAttribute("csrf", request.getAttribute("_csrf"))
        model.addAttribute("activePage", "admin")
        model.addAttribute("form", MergeUsersForm())
        model.addAttribute("mergedUsers", adminService.mergedUsers(null))
        ensureFlashAttributesPresent(model)
        return "pages/admin_merge_users"
    }

    private fun ensureFlashAttributesPresent(
        model: Model,
        includeSuccess: Boolean = true,
    ) {
        if (includeSuccess && !model.containsAttribute("successMessage")) {
            model.addAttribute("successMessage", null)
        }
        if (!model.containsAttribute("errorMessage")) {
            model.addAttribute("errorMessage", null)
        }
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
                    "Login links=${result.movedLoginLinks}, Passkeys=${result.movedPasskeys}. " +
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
