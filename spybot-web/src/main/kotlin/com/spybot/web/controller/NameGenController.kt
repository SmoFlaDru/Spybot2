package com.spybot.web.controller

import com.spybot.core.model.NameLikeStatus
import com.spybot.core.security.MergedUserPrincipal
import com.spybot.core.service.LikedNameService
import com.spybot.web.jte.renderJte
import com.spybot.web.service.namegen.Likers
import com.spybot.web.service.namegen.NameGenService
import gg.jte.generated.fragments.Jtenamegen_like_buttonGenerated
import gg.jte.generated.fragments.Jtenamegen_topGenerated
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
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
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        // Only names the generator itself produces can be liked - the table is not a free-text store.
        val generated = nameGenService.find(name) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown name")
        val status = likedNameService.like(generated.displayName, generated.realName, generated.slang, Likers.of(principal, request))
        likeButton(generated.displayName, status, response)
    }

    @PostMapping("/unlike")
    fun unlike(
        @RequestParam("name") @NotBlank name: String,
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val generated = nameGenService.find(name) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown name")
        val status = likedNameService.unlike(generated.displayName, Likers.of(principal, request))
        likeButton(generated.displayName, status, response)
    }

    @GetMapping("/top")
    fun top(
        @AuthenticationPrincipal principal: MergedUserPrincipal?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = response.renderJte { out ->
        Jtenamegen_topGenerated.render(out, null, topNames = likedNameService.top(TOP_LIMIT, Likers.of(principal, request)))
    }

    private fun likeButton(
        name: String,
        status: NameLikeStatus,
        response: HttpServletResponse,
    ) {
        response.setHeader("HX-Trigger", LIKES_CHANGED_EVENT)
        response.renderJte { out ->
            Jtenamegen_like_buttonGenerated.render(
                out,
                null,
                name = name,
                likes = status.likes,
                likedByMe = status.likedByMe,
                inList = false,
            )
        }
    }

    companion object {
        const val TOP_LIMIT = 30
        const val LIKES_CHANGED_EVENT = "namegen_likes_changed"
    }
}
