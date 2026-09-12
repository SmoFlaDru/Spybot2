package com.spybot.web.jte

import com.spybot.core.model.MergedUserView
import com.spybot.core.security.MergedUserPrincipal
import com.spybot.web.service.SpybotPageService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.info.GitProperties
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Everything the page layout needs that isn't page content: who is logged in, the CSRF token for
 * forms and HTMX, and the build the footer shows. One value instead of four parameters threaded
 * through every page template.
 */
data class PageChrome(
    val loggedInUser: MergedUserView?,
    val csrf: CsrfToken?,
    val commitHash: String? = null,
    val buildTime: Instant? = null,
)

@Component
class PageChromeFactory(
    private val pageService: SpybotPageService,
    private val gitProperties: GitProperties?,
    private val buildProperties: BuildProperties?,
) {
    fun of(
        principal: MergedUserPrincipal?,
        request: HttpServletRequest,
    ): PageChrome =
        PageChrome(
            loggedInUser = pageService.loggedInUser(principal),
            csrf = request.csrfToken(),
            commitHash = gitProperties?.shortCommitId,
            buildTime = buildProperties?.time,
        )
}
