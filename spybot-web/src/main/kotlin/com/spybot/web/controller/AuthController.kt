package com.spybot.web.controller

import com.spybot.core.service.AuthenticationService
import com.spybot.core.service.SpybotQueryService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.view.RedirectView

@Controller
class AuthController(
    private val authenticationService: AuthenticationService,
    private val queryService: SpybotQueryService,
) {
    @GetMapping("/link_auth")
    fun linkAuth(
        @RequestParam(required = false) code: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): RedirectView {
        val principal = authenticationService.authenticateByLoginCode(code)
        if (principal != null) {
            val authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                    principal,
                    null,
                    principal.authorities,
                )
            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = authentication
            SecurityContextHolder.setContext(context)
            HttpSessionSecurityContextRepository().saveContext(context, request, response)
            // Someone who just went through the TeamSpeak login and has no passkey yet is at the
            // best possible moment to add one; the profile page offers it once.
            if (queryService.passkeysForUser(principal.id).isEmpty()) {
                return RedirectView("/profile?passkey-prompt")
            }
        }
        return RedirectView("/")
    }
}
