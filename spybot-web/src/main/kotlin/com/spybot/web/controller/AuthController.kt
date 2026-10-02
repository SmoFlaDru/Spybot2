package com.spybot.web.controller

import com.spybot.core.service.AuthenticationService
import com.spybot.core.service.PasskeyQueries
import com.spybot.web.security.PostLoginRedirect
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
    private val passkeyQueries: PasskeyQueries,
    private val postLoginRedirect: PostLoginRedirect,
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
            // best possible moment to add one; the profile page offers it once. A page they were
            // trying to reach when they got sent to log in comes first, though.
            val landingPage = if (passkeyQueries.passkeysForUser(principal.id).isEmpty()) "/profile?passkey-prompt" else "/"
            return RedirectView(postLoginRedirect.consume(request, response, fallback = landingPage))
        }
        return RedirectView("/")
    }
}
