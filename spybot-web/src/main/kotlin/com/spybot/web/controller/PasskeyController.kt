package com.spybot.web.controller

import com.spybot.core.security.MergedUserPrincipal
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import com.spybot.web.service.PasskeyService

@RestController
@RequestMapping("/passkeys")
class PasskeyController(
    private val passkeyService: PasskeyService,
) {
    @GetMapping("/generate-authentication-options")
    fun generateAuthenticationOptions(request: HttpServletRequest): Map<String, Any> = passkeyService.generateAuthenticationOptions(request)

    @GetMapping("/generate-registration-options")
    fun generateRegistrationOptions(
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        request: HttpServletRequest,
    ): Map<String, Any> =
        passkeyService.generateRegistrationOptions(
            userId = principal.id,
            displayName = principal.displayName,
            request = request,
        )

    @PostMapping("/verify-registration")
    fun verifyRegistration(
        request: HttpServletRequest,
        @RequestBody body: String,
    ): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(passkeyService.verifyRegistration(request, body))

    @PostMapping("/verify-authentication")
    fun verifyAuthentication(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @RequestBody body: String,
    ): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(passkeyService.verifyAuthentication(request, response, body))
}
