package com.spybot.core.service

import com.spybot.core.security.MergedUserPrincipal
import org.springframework.stereotype.Service

@Service
class AuthenticationService(
    private val mergedUserQueries: MergedUserQueries,
) {
    fun loadPrincipal(userId: Long): MergedUserPrincipal? = mergedUserQueries.findMergedUserById(userId)?.let(::MergedUserPrincipal)

    fun authenticateByLoginCode(code: String?): MergedUserPrincipal? {
        if (code.isNullOrBlank()) {
            return null
        }
        return mergedUserQueries.findMergedUserByLoginCode(code)?.let(::MergedUserPrincipal)
    }
}
