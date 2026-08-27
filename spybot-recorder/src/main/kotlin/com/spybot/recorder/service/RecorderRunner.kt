package com.spybot.recorder.service

import com.spybot.core.config.SpybotProperties
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

@Component
class RecorderRunner(
    private val gateway: TeamSpeakGateway,
    private val properties: SpybotProperties,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)
    private val stopping = AtomicBoolean(false)

    override fun run(args: ApplicationArguments) {
        log.info(
            "Starting TeamSpeak recorder for {}:{} as {}",
            properties.teamspeak.host,
            properties.teamspeak.port,
            properties.teamspeak.nickname,
        )
        gateway.start()
    }

    @PreDestroy
    fun shutdown() {
        if (stopping.compareAndSet(false, true)) {
            log.info("Stopping TeamSpeak recorder")
            gateway.stop()
        }
    }
}
