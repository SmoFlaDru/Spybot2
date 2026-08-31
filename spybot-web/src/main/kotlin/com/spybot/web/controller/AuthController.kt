package com.spybot.web.controller

import com.spybot.core.service.AuthenticationService
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
        }
        return RedirectView("/")
    }
}
