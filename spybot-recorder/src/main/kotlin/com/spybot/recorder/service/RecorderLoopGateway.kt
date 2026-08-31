package com.spybot.recorder.service

import com.spybot.core.config.SpybotProperties
import com.spybot.core.service.RecorderDomainService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

@Component
class RecorderLoopGateway(
    private val client: TeamSpeakQueryClient,
    private val domainService: RecorderDomainService,
    private val properties: SpybotProperties,
) : TeamSpeakGateway,
    com.spybot.core.service.RecorderMessageGateway {
    private val log = LoggerFactory.getLogger(javaClass)
    private val stopping = AtomicBoolean(false)

    override fun start() {
        stopping.set(false)
        var consecutiveFailures = 0
        while (!stopping.get()) {
            try {
                client.connect()
                client.registerEvents()
                client.setNickname(properties.teamspeak.nickname)
                domainService.syncChannels(client.getChannels())
                domainService.handleInitialClients(client.getClients(), this)
                consecutiveFailures = 0
                while (!stopping.get()) {
                    client.waitForEvent()?.let { event ->
                        domainService.handleEvent(event, this) { channelId -> client.getChannelName(channelId) }
                    }
                }
            } catch (error: Exception) {
                if (!stopping.get()) {
                    val delayMs = backoffDelayMs(consecutiveFailures)
                    consecutiveFailures++
                    log.warn("Recorder connection failed, retrying in {}ms", delayMs, error)
                    Thread.sleep(delayMs)
                }
            } finally {
                client.close()
            }
        }
    }

    /**
     * Doubles from INITIAL_BACKOFF_MS with each consecutive failure, capped at MAX_BACKOFF_MS.
     * Keeps a persistently failing connection (e.g. the TeamSpeak server's query flood
     * protection) from being hammered with a reconnect attempt every second, which would only
     * extend the ban further.
     */
    internal fun backoffDelayMs(consecutiveFailures: Int): Long {
        val shift = consecutiveFailures.coerceAtMost(MAX_BACKOFF_SHIFT)
        return (INITIAL_BACKOFF_MS shl shift).coerceAtMost(MAX_BACKOFF_MS)
    }

    override fun stop() {
        stopping.set(true)
        client.close()
    }

    override fun pokeClient(
        clientId: Int,
        message: String,
    ) {
        client.pokeClient(clientId, message)
    }

    override fun sendTextMessage(
        clientId: Int,
        message: String,
    ) {
        client.sendTextMessage(clientId, message)
    }

    companion object {
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 300_000L
        private const val MAX_BACKOFF_SHIFT = 8
    }
}
