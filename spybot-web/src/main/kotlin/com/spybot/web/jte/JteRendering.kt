package com.spybot.web.jte

import gg.jte.html.HtmlTemplateOutput
import gg.jte.html.OwaspHtmlTemplateOutput
import gg.jte.output.PrintWriterOutput
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.servlet.support.RequestContextUtils

/**
 * Renders a generated jte template straight into this response. [render] is expected to call a
 * generated `Jte*Generated.render(output, null, ...)` function, which is how a controller gets the
 * template's parameters checked by the compiler instead of matched by name at request time.
 */
fun HttpServletResponse.renderJte(render: (HtmlTemplateOutput) -> Unit) {
    contentType = "text/html;charset=UTF-8"
    render(OwaspHtmlTemplateOutput(PrintWriterOutput(writer)))
}

/** The CSRF filter stores the token under this request attribute; the layout's chrome carries it. */
fun HttpServletRequest.csrfToken(): CsrfToken? = getAttribute("_csrf") as CsrfToken?

/** A flash attribute set on the previous request via RedirectAttributes.addFlashAttribute. */
fun HttpServletRequest.flashMessage(name: String): String? = RequestContextUtils.getInputFlashMap(this)?.get(name) as String?
