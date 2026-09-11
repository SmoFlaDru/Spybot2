package com.spybot.web.controller

import com.spybot.core.model.Liker
import com.spybot.core.model.NameLikeStatus
import com.spybot.core.service.LikedNameService
import com.spybot.core.service.SpybotQueryService
import com.spybot.web.filter.VisitorIdFilter
import com.spybot.web.service.SpybotPageService
import com.spybot.web.service.namegen.GeneratedName
import com.spybot.web.service.namegen.NameGenService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.ui.ConcurrentModel

class FragmentControllerTest {
    @Test
    fun `namegen fragment renders a fresh name with the viewer's like state`() {
        val nameGenService = Mockito.mock(NameGenService::class.java)
        val likedNameService = Mockito.mock(LikedNameService::class.java)
        val model = ConcurrentModel()
        val generated = GeneratedName("Nuke Skywalker", "Luke Skywalker", "nuke", "Fictional character", "International", 0.98)
        val status = NameLikeStatus(likes = 4, likedByMe = true)
        val request = MockHttpServletRequest("GET", "/namegen_fragment").apply { setAttribute(VisitorIdFilter.ATTRIBUTE, "0123456789abcdef0123456789abcdef") }
        Mockito.`when`(nameGenService.generate()).thenReturn(generated)
        Mockito.`when`(likedNameService.status("Nuke Skywalker", Liker.Visitor("0123456789abcdef0123456789abcdef"))).thenReturn(status)

        val controller =
            FragmentController(
                Mockito.mock(SpybotPageService::class.java),
                Mockito.mock(SpybotQueryService::class.java),
                nameGenService,
                likedNameService,
            )
        val viewName = controller.nameGeneratorFragment(null, model, request)

        assertEquals("fragments/namegen_fragment", viewName)
        assertSame(generated, model.getAttribute("generatedName"))
        assertSame(status, model.getAttribute("likeStatus"))
    }
}
