package com.spybot.web.controller

import com.spybot.core.model.Liker
import com.spybot.core.model.NameLikeStatus
import com.spybot.core.service.LikedNameService
import com.spybot.core.service.PasskeyQueries
import com.spybot.core.service.StatisticsQueries
import com.spybot.core.service.SteamIdQueries
import com.spybot.web.filter.VisitorIdFilter
import com.spybot.web.service.SpybotPageService
import com.spybot.web.service.namegen.GeneratedName
import com.spybot.web.service.namegen.NameGenService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class FragmentControllerTest {
    @Test
    fun `namegen fragment renders a fresh name with the viewer's like state`() {
        val nameGenService = Mockito.mock(NameGenService::class.java)
        val likedNameService = Mockito.mock(LikedNameService::class.java)
        val generated = GeneratedName("Nuke Skywalker", "Luke Skywalker", "nuke", "Fictional character", "International", 0.98)
        val request =
            MockHttpServletRequest("GET", "/namegen_fragment").apply {
                setAttribute(VisitorIdFilter.ATTRIBUTE, "0123456789abcdef0123456789abcdef")
            }
        val response = MockHttpServletResponse()
        Mockito.`when`(nameGenService.generate()).thenReturn(generated)
        Mockito
            .`when`(likedNameService.status("Nuke Skywalker", Liker.Visitor("0123456789abcdef0123456789abcdef")))
            .thenReturn(NameLikeStatus(likes = 4, likedByMe = true))

        val controller =
            FragmentController(
                Mockito.mock(SpybotPageService::class.java),
                Mockito.mock(PasskeyQueries::class.java),
                Mockito.mock(StatisticsQueries::class.java),
                Mockito.mock(SteamIdQueries::class.java),
                nameGenService,
                likedNameService,
            )
        controller.nameGeneratorFragment(null, request, response)

        val html = response.contentAsString
        assertEquals("text/html;charset=UTF-8", response.contentType)
        assertTrue("Nuke Skywalker" in html, html)
        assertTrue("Luke Skywalker" in html, html)
        assertTrue("hx-get=\"/namegen_fragment\"" in html, "reroll button must target the fragment endpoint")
    }
}
