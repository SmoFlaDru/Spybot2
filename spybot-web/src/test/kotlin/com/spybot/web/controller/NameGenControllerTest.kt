package com.spybot.web.controller

import com.spybot.core.model.LikedNameView
import com.spybot.core.service.LikedNameService
import com.spybot.web.service.namegen.GeneratedName
import com.spybot.web.service.namegen.NameGenService
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.http.HttpStatus
import org.springframework.ui.ConcurrentModel
import org.springframework.web.server.ResponseStatusException

class NameGenControllerTest {
    private val nameGenService = Mockito.mock(NameGenService::class.java)
    private val likedNameService = Mockito.mock(LikedNameService::class.java)
    private val response = Mockito.mock(HttpServletResponse::class.java)
    private val controller = NameGenController(nameGenService, likedNameService)

    @Test
    fun `liking a generated name records it from the pool entry and tells the top list to refresh`() {
        val generated = GeneratedName("Carry Potter", "Harry Potter", "carry", "Fictional character", "International", 0.97)
        val liked = LikedNameView("Carry Potter", "Harry Potter", "carry", 3)
        Mockito.`when`(nameGenService.find("Carry Potter")).thenReturn(generated)
        Mockito.`when`(likedNameService.like("Carry Potter", "Harry Potter", "carry")).thenReturn(liked)
        val model = ConcurrentModel()

        val viewName = controller.like("Carry Potter", model, response)

        assertEquals("fragments/namegen_liked", viewName)
        assertSame(liked, model.getAttribute("liked"))
        verify(response).setHeader("HX-Trigger", "namegen_likes_changed")
    }

    @Test
    fun `names the generator cannot produce are rejected, not stored`() {
        Mockito.`when`(nameGenService.find("Totally Madeup")).thenReturn(null)

        val error = assertThrows<ResponseStatusException> { controller.like("Totally Madeup", ConcurrentModel(), response) }

        assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        verify(likedNameService, never()).like(Mockito.anyString(), Mockito.anyString(), Mockito.anyString())
    }

    @Test
    fun `top list maps to its fragment with the thirty most liked names`() {
        val top = listOf(LikedNameView("Nuke Skywalker", "Luke Skywalker", "nuke", 9))
        Mockito.`when`(likedNameService.top(30)).thenReturn(top)
        val model = ConcurrentModel()

        val viewName = controller.top(model)

        assertEquals("fragments/namegen_top", viewName)
        assertSame(top, model.getAttribute("topNames"))
    }
}
