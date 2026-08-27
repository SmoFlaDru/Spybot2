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
) : TeamSpeakGateway, com.spybot.core.service.RecorderMessageGateway {
    private val log = LoggerFactory.getLogger(javaClass)
    private val stopping = AtomicBoolean(false)

    override fun start() {
        stopping.set(false)
        while (!stopping.get()) {
            try {
                client.connect()
                client.registerEvents()
                client.setNickname(properties.teamspeak.nickname)
                domainService.syncChannels(client.getChannels())
                domainService.handleInitialClients(client.getClients(), this)
                while (!stopping.get()) {
                    client.waitForEvent()?.let { event ->
                        domainService.handleEvent(event, this) { channelId -> client.getChannelName(channelId) }
                    }
                }
            } catch (error: Exception) {
                if (!stopping.get()) {
                    log.warn("Recorder connection failed, retrying", error)
                    Thread.sleep(1000)
                }
            } finally {
                client.close()
            }
        }
    }

    override fun stop() {
        stopping.set(true)
        client.close()
    }

    override fun pokeClient(clientId: Int, message: String) {
        client.pokeClient(clientId, message)
    }

    override fun sendTextMessage(clientId: Int, message: String) {
        client.sendTextMessage(clientId, message)
    }
}
