package com.spybot.core.service

import com.spybot.core.security.MergedUserPrincipal
import org.springframework.stereotype.Service

@Service
class AuthenticationService(
    private val queryService: SpybotQueryService,
) {
    fun loadPrincipal(userId: Long): MergedUserPrincipal? = queryService.findMergedUserById(userId)?.let(::MergedUserPrincipal)

    fun authenticateByLoginCode(code: String?): MergedUserPrincipal? {
        if (code.isNullOrBlank()) {
            return null
        }
        return queryService.findMergedUserByLoginCode(code)?.let(::MergedUserPrincipal)
    }
}
