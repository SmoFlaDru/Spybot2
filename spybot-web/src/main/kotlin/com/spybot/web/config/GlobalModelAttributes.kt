package com.spybot.web.config

import org.springframework.boot.info.BuildProperties
import org.springframework.boot.info.GitProperties
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute
import java.time.Instant

/**
 * Exposes build/commit metadata to every controller's model without threading it through every
 * controller's own method signature - it's global site metadata, not per-page data. Each page
 * template still needs its own `commitHash`/`buildTime` @param and to pass them through to
 * `@template.layout.base(...)`, since JTE sub-templates bind params from the caller's arguments,
 * not directly from the Spring model - only the top-level page template gets that for free.
 */
@ControllerAdvice
class GlobalModelAttributes(
    private val gitProperties: GitProperties?,
    private val buildProperties: BuildProperties?,
) {
    @ModelAttribute("commitHash")
    fun commitHash(): String? = gitProperties?.shortCommitId

    @ModelAttribute("buildTime")
    fun buildTime(): Instant? = buildProperties?.time
}
