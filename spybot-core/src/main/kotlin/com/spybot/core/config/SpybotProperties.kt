package com.spybot.core.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "spybot")
data class SpybotProperties(
    val publicBaseUrl: String = "https://localhost",
    val fidoServerName: String = "Spybot local",
    val steamApiKey: String? = null,
    val teamspeak: TeamSpeakProperties = TeamSpeakProperties(),
) {
    data class TeamSpeakProperties(
        val host: String = "localhost",
        val port: Int = 10011,
        val serverIp: String = "localhost",
        val user: String? = null,
        val password: String? = null,
        val nickname: String = "Spybot_2",
    )
}
