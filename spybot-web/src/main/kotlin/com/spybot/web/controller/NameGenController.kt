package com.spybot.web.controller

import com.spybot.core.security.MergedUserPrincipal
import com.spybot.core.service.LikedNameService
import com.spybot.web.service.namegen.Likers
import com.spybot.web.service.namegen.NameGenService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException

/**
 * Likes for generated names, one per person per name. Both actions reply with the button in its
 * new state and fire `namegen_likes_changed`, which the top list listens for to re-render itself
 * in the new order. Like and unlike are separate, idempotent endpoints rather than a toggle so a
 * double-click cannot quietly undo itself.
 */
@Controller
@Validated
@RequestMapping("/namegen")
class NameGenController(
    private val nameGenService: NameGenService,
    private val likedNameService: LikedNameService,
) {
    @PostMapping("/like")
    fun like(
        @RequestParam("name") @NotBlank name: String,
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        model: Model,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): String {
        // Only names the generator itself produces can be liked - the table is not a free-text store.
        val generated = nameGenService.find(name) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown name")
        val status = likedNameService.like(generated.displayName, generated.realName, generated.slang, Likers.of(principal, request))
        return likeButton(generated.displayName, status, model, response)
    }

    @PostMapping("/unlike")
    fun unlike(
        @RequestParam("name") @NotBlank name: String,
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        model: Model,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): String {
        val generated = nameGenService.find(name) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown name")
        val status = likedNameService.unlike(generated.displayName, Likers.of(principal, request))
        return likeButton(generated.displayName, status, model, response)
    }

    @GetMapping("/top")
    fun top(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        model: Model,
        request: HttpServletRequest,
    ): String {
        model.addAttribute("topNames", likedNameService.top(TOP_LIMIT, Likers.of(principal, request)))
        return "fragments/namegen_top"
    }

    private fun likeButton(
        name: String,
        status: com.spybot.core.model.NameLikeStatus,
        model: Model,
        response: HttpServletResponse,
    ): String {
        model.addAttribute("name", name)
        model.addAttribute("likes", status.likes)
        model.addAttribute("likedByMe", status.likedByMe)
        model.addAttribute("inList", false)
        response.setHeader("HX-Trigger", LIKES_CHANGED_EVENT)
        return "fragments/namegen_like_button"
    }

    companion object {
        const val TOP_LIMIT = 30
        const val LIKES_CHANGED_EVENT = "namegen_likes_changed"
    }
}
