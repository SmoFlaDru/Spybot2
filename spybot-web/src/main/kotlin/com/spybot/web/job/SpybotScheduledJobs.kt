package com.spybot.web.job

import com.spybot.core.service.AwardService
import com.spybot.core.service.SpybotQueryService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SpybotScheduledJobs(
    private val queryService: SpybotQueryService,
    private val awardService: AwardService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 59 * * * *")
    fun recordHourlyActivity() {
        log.info("Recording hourly activity snapshot")
        queryService.recordHourlyActivity()
    }

    @Scheduled(cron = "0 59 23 * * SUN")
    fun endOfWeekAwards() {
        val candidates = awardService.runEndOfWeekAwards()
        log.info("Weekly awards job completed with {} awarded users", candidates)
    }
}
