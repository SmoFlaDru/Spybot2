package com.spybot.web.controller

import com.spybot.core.model.LikedNameView
import com.spybot.core.model.Liker
import com.spybot.core.model.MergedUserView
import com.spybot.core.model.NameLikeStatus
import com.spybot.core.security.MergedUserPrincipal
import com.spybot.core.service.LikedNameService
import com.spybot.web.filter.VisitorIdFilter
import com.spybot.web.service.namegen.GeneratedName
import com.spybot.web.service.namegen.NameGenService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.server.ResponseStatusException

class NameGenControllerTest {
    private val nameGenService = Mockito.mock(NameGenService::class.java)
    private val likedNameService = Mockito.mock(LikedNameService::class.java)
    private val controller = NameGenController(nameGenService, likedNameService)
    private val generated = GeneratedName("Carry Potter", "Harry Potter", "carry", "Fictional character", "International", 0.97)
    private val visitor = Liker.Visitor("0123456789abcdef0123456789abcdef")

    private fun anonymousRequest() =
        MockHttpServletRequest("POST", "/namegen/like").apply { setAttribute(VisitorIdFilter.ATTRIBUTE, visitor.visitorId) }

    private fun loggedIn(id: Long) =
        MergedUserPrincipal(MergedUserView(id, "Benno", obsolete = false, isSuperuser = false, lastLogin = null))

    @Test
    fun `anonymous visitors like as their cookie identity`() {
        Mockito.`when`(nameGenService.find("Carry Potter")).thenReturn(generated)
        Mockito.`when`(likedNameService.like("Carry Potter", "Harry Potter", "carry", visitor)).thenReturn(NameLikeStatus(3, true))
        val response = MockHttpServletResponse()

        controller.like("Carry Potter", null, anonymousRequest(), response)

        val html = response.contentAsString
        assertTrue("Carry Potter" in html, html)
        assertTrue(">3<" in html || " 3" in html, "like count must be rendered: $html")
        assertTrue("/namegen/unlike" in html, "a liked name offers the unlike action: $html")
        assertEquals("namegen_likes_changed", response.getHeader("HX-Trigger"))
    }

    @Test
    fun `logged-in users like as themselves, not as the cookie`() {
        Mockito.`when`(nameGenService.find("Carry Potter")).thenReturn(generated)
        Mockito.`when`(likedNameService.like("Carry Potter", "Harry Potter", "carry", Liker.User(42))).thenReturn(NameLikeStatus(1, true))

        controller.like("Carry Potter", loggedIn(42), anonymousRequest(), MockHttpServletResponse())

        verify(likedNameService).like("Carry Potter", "Harry Potter", "carry", Liker.User(42))
    }

    @Test
    fun `unlike replies with the button back in its unliked state`() {
        Mockito.`when`(nameGenService.find("Carry Potter")).thenReturn(generated)
        Mockito.`when`(likedNameService.unlike("Carry Potter", visitor)).thenReturn(NameLikeStatus(2, false))
        val response = MockHttpServletResponse()

        controller.unlike("Carry Potter", null, anonymousRequest(), response)

        val html = response.contentAsString
        assertTrue("/namegen/like" in html, "an unliked name offers the like action: $html")
        assertFalse("/namegen/unlike" in html, html)
        assertEquals("namegen_likes_changed", response.getHeader("HX-Trigger"))
    }

    @Test
    fun `names the generator cannot produce are rejected, not stored`() {
        Mockito.`when`(nameGenService.find("Totally Madeup")).thenReturn(null)

        val error =
            assertThrows<ResponseStatusException> {
                controller.like("Totally Madeup", null, anonymousRequest(), MockHttpServletResponse())
            }

        assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        Mockito.verifyNoInteractions(likedNameService)
    }

    @Test
    fun `top list is rendered for the viewer so their own likes show as such`() {
        val top = listOf(LikedNameView("Nuke Skywalker", "Luke Skywalker", "nuke", 9, likedByMe = true))
        Mockito.`when`(likedNameService.top(30, visitor)).thenReturn(top)
        val response = MockHttpServletResponse()

        controller.top(null, anonymousRequest(), response)

        val html = response.contentAsString
        assertTrue("Nuke Skywalker" in html, html)
        assertTrue("/namegen/unlike" in html, "the viewer's own like renders as liked: $html")
    }
}
