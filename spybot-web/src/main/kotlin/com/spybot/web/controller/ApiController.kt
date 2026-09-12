package com.spybot.web.controller

import com.spybot.core.service.StatisticsQueries
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ApiController(
    private val statisticsQueries: StatisticsQueries,
) {
    @GetMapping("/api/v1/live")
    fun liveApi() = statisticsQueries.liveApi()

    @GetMapping("/api/v1/widget", "/widget_legacy")
    fun widgetLegacy() = statisticsQueries.widgetLegacy()
}
