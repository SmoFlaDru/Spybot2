package com.spybot.web.service.namegen

import com.spybot.core.model.Liker
import com.spybot.core.security.MergedUserPrincipal
import com.spybot.web.filter.VisitorIdFilter
import jakarta.servlet.http.HttpServletRequest

object Likers {
    /** Logged-in users like as themselves (across devices); everyone else as the browser's visitor cookie. */
    fun of(
        principal: MergedUserPrincipal?,
        request: HttpServletRequest,
    ): Liker = principal?.let { Liker.User(it.id) } ?: Liker.Visitor(VisitorIdFilter.visitorId(request))
}
