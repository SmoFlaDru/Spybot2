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
