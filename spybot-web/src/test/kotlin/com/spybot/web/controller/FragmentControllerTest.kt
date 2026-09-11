package com.spybot.web.controller

import com.spybot.core.service.SpybotQueryService
import com.spybot.web.service.SpybotPageService
import com.spybot.web.service.namegen.GeneratedName
import com.spybot.web.service.namegen.NameGenService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.ui.ConcurrentModel

class FragmentControllerTest {
    @Test
    fun `namegen fragment maps to the fragment view with a fresh generated name`() {
        val nameGenService = Mockito.mock(NameGenService::class.java)
        val model = ConcurrentModel()
        val generated = GeneratedName("Nuke Skywalker", "Luke Skywalker", "nuke", "Fictional character", "International", 0.98)
        Mockito.`when`(nameGenService.generate()).thenReturn(generated)

        val controller =
            FragmentController(
                Mockito.mock(SpybotPageService::class.java),
                Mockito.mock(SpybotQueryService::class.java),
                nameGenService,
            )
        val viewName = controller.nameGeneratorFragment(model)

        assertEquals("fragments/namegen_fragment", viewName)
        assertSame(generated, model.getAttribute("generatedName"))
    }
}
