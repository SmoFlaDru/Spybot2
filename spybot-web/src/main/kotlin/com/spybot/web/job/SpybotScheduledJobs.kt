package com.spybot.web.job

import com.spybot.core.service.AwardService
import com.spybot.core.service.StatisticsQueries
import com.spybot.web.service.RecordsService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SpybotScheduledJobs(
    private val statisticsQueries: StatisticsQueries,
    private val awardService: AwardService,
    private val recordsService: RecordsService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 59 * * * *")
    fun recordHourlyActivity() {
        log.info("Recording hourly activity snapshot")
        statisticsQueries.recordHourlyActivity()
    }

    @Scheduled(cron = "0 59 23 * * SUN")
    fun endOfWeekAwards() {
        val candidates = awardService.runEndOfWeekAwards()
        log.info("Weekly awards job completed with {} awarded users", candidates)
    }

    // Shortly after startup so the hall of fame isn't empty for up to an hour after a deploy,
    // then hourly. Its own schedule rather than chained onto the :59 snapshot above, so a slow
    // scan of the activity history can never delay that snapshot.
    @Scheduled(initialDelayString = "PT15S", fixedDelayString = "PT1H")
    fun refreshRecords() {
        recordsService.refresh()
    }
}
