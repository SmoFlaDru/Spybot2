package com.spybot.web.service

import com.spybot.core.model.RecordsView
import com.spybot.core.service.RecordsQueries
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the hall of fame's records. Computing them scans the whole activity history, so they
 * are refreshed by a scheduled job (shortly after startup, then hourly) rather than on every
 * page view; [current] is null until the first refresh has finished and the page says so.
 */
@Service
class RecordsService(
    private val recordsQueries: RecordsQueries,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val records = AtomicReference<RecordsView?>(null)

    fun current(): RecordsView? = records.get()

    fun refresh() {
        records.set(recordsQueries.records())
        log.info("Refreshed hall of fame records")
    }
}
