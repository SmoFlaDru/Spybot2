package com.spybot.web.controller

import com.spybot.core.model.AdminMergedUserRow
import com.spybot.core.model.AdminNewsEventRow
import com.spybot.core.model.MergedUserView
import com.spybot.core.security.MergedUserPrincipal
import com.spybot.web.jte.PageChromeFactory
import com.spybot.web.service.AdminService
import com.spybot.web.service.SpybotPageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.FlashMap
import java.time.OffsetDateTime

/** Renders every admin page for real so the templates are exercised, including flash messages. */
class AdminControllerTest {
    private val adminService = Mockito.mock(AdminService::class.java)
    private val pageService = Mockito.mock(SpybotPageService::class.java)
    private val controller = AdminController(adminService, PageChromeFactory(pageService, null, null))
    private val admin = MergedUserPrincipal(MergedUserView(1, "Benno", obsolete = false, isSuperuser = true, lastLogin = null))

    private fun render(
        flash: Map<String, String> = emptyMap(),
        block: (MockHttpServletRequest, MockHttpServletResponse) -> Unit,
    ): String {
        Mockito.`when`(pageService.loggedInUser(admin)).thenReturn(admin.user)
        val request = MockHttpServletRequest()
        if (flash.isNotEmpty()) request.setAttribute(DispatcherServlet.INPUT_FLASH_MAP_ATTRIBUTE, FlashMap().apply { putAll(flash) })
        val response = MockHttpServletResponse()
        block(request, response)
        assertEquals("text/html;charset=UTF-8", response.contentType)
        return response.contentAsString
    }

    @Test
    fun `dashboard renders the overview inside the admin layout`() {
        Mockito
            .`when`(
                adminService.overview(),
            ).thenReturn(AdminService.AdminOverview(mergedUsersCount = 12, tsUsersCount = 34, newsEventsCount = 5))

        val html = render { request, response -> controller.dashboard(admin, request, response) }

        assertTrue("12" in html && "34" in html, html.take(200))
        assertTrue("Benno" in html, "the logged-in admin shows in the layout")
    }

    @Test
    fun `merged users page renders the query and rows`() {
        Mockito.`when`(adminService.mergedUsers("ben")).thenReturn(listOf(AdminMergedUserRow(7, "Benno", false, true, 2, null)))

        val html = render { request, response -> controller.mergedUsers("ben", admin, request, response) }

        assertTrue("Benno" in html && "ben" in html, html.take(200))
    }

    @Test
    fun `news events page shows the flash message from a redirect`() {
        Mockito
            .`when`(
                adminService.newsEvents(null),
            ).thenReturn(listOf(AdminNewsEventRow(3, "Some news", null, OffsetDateTime.parse("2026-09-12T10:00:00Z"))))

        val html =
            render(
                flash = mapOf("successMessage" to "News event created"),
            ) { request, response -> controller.newsEvents(null, admin, request, response) }

        assertTrue("News event created" in html, "flash message must be rendered")
        assertTrue("Some news" in html)
    }

    @Test
    fun `news event form renders in create and edit mode`() {
        Mockito
            .`when`(
                adminService.newsEventById(3),
            ).thenReturn(AdminNewsEventRow(3, "Edit me", "https://x", OffsetDateTime.parse("2026-09-12T10:00:00Z")))

        val create = render { request, response -> controller.newsEventNew(admin, request, response) }
        val edit = render { request, response -> controller.newsEventEdit(3, admin, request, response) }

        assertTrue("_csrf" in create, "forms carry the CSRF field")
        assertTrue("Edit me" in edit)
    }

    @Test
    fun `merge users form renders users and an error flash`() {
        Mockito.`when`(adminService.mergedUsers(null)).thenReturn(listOf(AdminMergedUserRow(7, "Benno", false, true, 2, null)))

        val html =
            render(
                flash = mapOf("errorMessage" to "Target user is required"),
            ) { request, response -> controller.mergeUsersForm(admin, request, response) }

        assertTrue("Target user is required" in html)
        assertTrue("Benno" in html)
    }
}
