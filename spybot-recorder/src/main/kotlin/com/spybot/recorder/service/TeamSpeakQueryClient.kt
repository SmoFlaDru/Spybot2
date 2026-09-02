package com.spybot.recorder.service

import com.spybot.core.model.TeamSpeakChannelSnapshot
import com.spybot.core.model.TeamSpeakClientSnapshot
import com.spybot.core.model.TeamSpeakEvent

interface TeamSpeakQueryClient {
    fun connect()

    fun registerEvents()

    fun setNickname(name: String)

    fun getClients(): List<TeamSpeakClientSnapshot>

    fun getChannels(): List<TeamSpeakChannelSnapshot>

    fun getChannelName(channelId: Int): String?

    fun waitForEvent(): TeamSpeakEvent?

    /**
     * Sends a harmless no-op command if the connection has been idle long enough that the
     * TeamSpeak server would otherwise drop it. TS3 ServerQuery closes connections that go
     * ~5 minutes without receiving any command, which used to happen constantly whenever a
     * channel sat quiet - fragmenting session tracking on every forced reconnect.
     */
    fun keepAlive()

    fun pokeClient(
        clientId: Int,
        message: String,
    )

    fun sendTextMessage(
        clientId: Int,
        message: String,
    )

    fun close()
}
