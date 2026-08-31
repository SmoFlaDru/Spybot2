package com.spybot.web.filter

import com.spybot.core.security.MergedUserPrincipal
import com.spybot.core.service.SpybotQueryService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class LastSeenFilter(
    private val queryService: SpybotQueryService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val principal = request.userPrincipal
        if (principal is org.springframework.security.core.Authentication &&
            principal.principal is MergedUserPrincipal
        ) {
            queryService.touchLastSeen((principal.principal as MergedUserPrincipal).id)
        }
        filterChain.doFilter(request, response)
    }
}
