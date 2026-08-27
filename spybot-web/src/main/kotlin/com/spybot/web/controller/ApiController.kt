package com.spybot.web.controller

import com.spybot.core.service.SpybotQueryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ApiController(
    private val queryService: SpybotQueryService,
) {
    @GetMapping("/api/v1/live")
    fun liveApi() = queryService.liveApi()

    @GetMapping("/api/v1/widget", "/widget_legacy")
    fun widgetLegacy() = queryService.widgetLegacy()
}
