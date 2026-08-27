package com.spybot.core.service

import com.spybot.core.config.SpybotProperties
import com.spybot.core.model.OpenSessionView
import com.spybot.core.model.TeamSpeakChannelSnapshot
import com.spybot.core.model.TeamSpeakClientSnapshot
import com.spybot.core.model.TeamSpeakEvent
import com.spybot.core.model.TeamSpeakIdentity
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RecorderDomainService(
    private val queryService: SpybotQueryService,
    private val properties: SpybotProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun syncChannels(channels: List<TeamSpeakChannelSnapshot>) {
        queryService.upsertChannels(channels.map { it.copy(name = escapeTeamSpeak(it.name)) })
    }

    fun handleInitialClients(
        clients: List<TeamSpeakClientSnapshot>,
        gateway: RecorderMessageGateway,
    ) {
        val remaining = clients.toMutableList()
        queryService.openSessions().forEach { openSession ->
            val keepAlive = remaining.firstOrNull { it.clientId == openSession.clientId && it.channelId == openSession.channelId }
            if (keepAlive != null) {
                remaining.remove(keepAlive)
            } else {
                log.info("Closing stale session for {}", openSession.tsUserName)
                queryService.closeOpenSessionsForUser(openSession.tsUserId, -2)
            }
        }

        remaining.forEach { handleClientEnter(it, gateway, joined = true) }
    }

    fun handleEvent(
        event: TeamSpeakEvent,
        gateway: RecorderMessageGateway,
        channelNameLookup: (Int) -> String?,
    ) {
        when (event) {
            is TeamSpeakEvent.ClientEnter -> handleClientEnter(event.client, gateway, joined = true, channelNameLookup = channelNameLookup)
            is TeamSpeakEvent.ClientLeave -> {
                queryService.findIdentityByClientId(event.clientId)?.let {
                    queryService.closeOpenSessionsForUser(it.tsUserId, event.reasonId)
                }
            }

            is TeamSpeakEvent.ClientMove -> {
                channelNameLookup(event.channelToId)?.let {
                    queryService.updateChannelName(event.channelToId, escapeTeamSpeak(it))
                }
                queryService.findIdentityByClientId(event.clientId)?.let {
                    queryService.closeOpenSessionsForUser(it.tsUserId, event.reasonId)
                    queryService.markClientSessionStarted(it.tsUserId, event.channelToId, event.clientId, joined = false)
                }
            }
        }
    }

    private fun handleClientEnter(
        client: TeamSpeakClientSnapshot,
        gateway: RecorderMessageGateway,
        joined: Boolean,
        channelNameLookup: ((Int) -> String?)? = null,
    ) {
        if (client.clientType == "1") {
            return
        }

        channelNameLookup?.invoke(client.channelId)?.let {
            queryService.updateChannelName(client.channelId, escapeTeamSpeak(it))
        }

        val identity =
            queryService.findIdentityByUniqueIdentifier(client.uniqueIdentifier)
                ?.let { queryService.renameIdentity(it, client.nickname) }
                ?: queryService.createTeamSpeakIdentity(client.nickname, client.clientId, client.uniqueIdentifier)

        queryService.markClientSessionStarted(identity.tsUserId, client.channelId, client.clientId, joined)
        sendLoginLink(identity, client.clientId, gateway)
        sendQueuedMessages(identity, client.clientId, gateway)
    }

    private fun sendQueuedMessages(
        identity: TeamSpeakIdentity,
        clientId: Int,
        gateway: RecorderMessageGateway,
    ) {
        queryService.queuedMessagesForMergedUser(identity.mergedUserId).forEach { message ->
            gateway.pokeClient(
                clientId,
                "You got an important message from Spybot! Check out my private message for details",
            )
            gateway.sendTextMessage(clientId, message.text)
            queryService.deleteQueuedMessage(message.id)
        }
    }

    private fun sendLoginLink(
        identity: TeamSpeakIdentity,
        clientId: Int,
        gateway: RecorderMessageGateway,
    ) {
        val code = UUID.randomUUID().toString().replace("-", "")
        queryService.createLoginLink(identity.mergedUserId, code)
        gateway.sendTextMessage(
            clientId,
            "Log into your account on Spybot: ${properties.publicBaseUrl}/link_auth?code=$code",
        )
    }

    private fun escapeTeamSpeak(value: String): String =
        buildString(value.length) {
            value.forEach { char ->
                append(
                    when (char) {
                        '\\' -> "\\\\"
                        '/' -> "\\/"
                        ' ' -> "\\s"
                        '|' -> "\\p"
                        else -> char
                    },
                )
            }
        }
}

interface RecorderMessageGateway {
    fun pokeClient(clientId: Int, message: String)

    fun sendTextMessage(clientId: Int, message: String)
}
