package com.spybot.web.controller

import com.spybot.core.service.LikedNameService
import com.spybot.web.service.namegen.NameGenService
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException

/**
 * Likes for generated names. Liking replies with the button's "liked" state and fires
 * `namegen_likes_changed`, which the top list listens for to re-render itself in the new order.
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
        model: Model,
        response: HttpServletResponse,
    ): String {
        // Only names the generator itself produces can be liked - the table is not a free-text store.
        val generated = nameGenService.find(name) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown name")
        val liked = likedNameService.like(generated.displayName, generated.realName, generated.slang)
        model.addAttribute("liked", liked)
        response.setHeader("HX-Trigger", LIKES_CHANGED_EVENT)
        return "fragments/namegen_liked"
    }

    @GetMapping("/top")
    fun top(model: Model): String {
        model.addAttribute("topNames", likedNameService.top(TOP_LIMIT))
        return "fragments/namegen_top"
    }

    companion object {
        const val TOP_LIMIT = 30
        const val LIKES_CHANGED_EVENT = "namegen_likes_changed"
    }
}
