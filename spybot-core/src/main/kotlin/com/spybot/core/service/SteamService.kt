package com.spybot.core.service

import com.spybot.core.config.SpybotProperties
import com.spybot.core.model.OnlineStatus
import com.spybot.core.model.SteamAccountInfo
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class SteamService(
    private val webClientBuilder: WebClient.Builder,
    private val properties: SpybotProperties,
) {
    private val cache = ConcurrentHashMap<String, CachedSteamResponse>()

    fun getSteamUsersPlayingInfo(steamIds: List<String>): List<SteamAccountInfo> {
        if (steamIds.isEmpty()) {
            return emptyList()
        }

        val normalizedIds = steamIds.distinct().take(100)
        val cacheKey = normalizedIds.sorted().joinToString(",")
        val cached = cache[cacheKey]
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return cached.value
        }

        val apiKey = properties.steamApiKey ?: return emptyList()
        val response =
            webClientBuilder
                .baseUrl("https://api.steampowered.com")
                .build()
                .get()
                .uri { builder ->
                    builder
                        .path("/ISteamUser/GetPlayerSummaries/v0002/")
                        .queryParam("key", apiKey)
                        .queryParam("steamids", normalizedIds.joinToString(","))
                        .build()
                }.retrieve()
                .bodyToMono(SteamPlayersResponse::class.java)
                .block(Duration.ofSeconds(10))
                ?.response
                ?.players
                .orEmpty()
                .map {
                    SteamAccountInfo(
                        steamId = it.steamid.orEmpty(),
                        gameId = it.gameid ?: 0,
                        gameName = it.gameextrainfo.orEmpty(),
                        avatarUrl = it.avatar.orEmpty(),
                        onlineStatus = OnlineStatus.fromCode(it.personastate ?: 0),
                    )
                }

        cache[cacheKey] = CachedSteamResponse(response, Instant.now().plusSeconds(10))
        return response
    }

    private data class CachedSteamResponse(
        val value: List<SteamAccountInfo>,
        val expiresAt: Instant,
    )

    private data class SteamPlayersResponse(
        val response: PlayersEnvelope? = null,
    )

    private data class PlayersEnvelope(
        val players: List<PlayerSummary> = emptyList(),
    )

    private data class PlayerSummary(
        val steamid: String? = null,
        val gameid: Int? = null,
        val gameextrainfo: String? = null,
        val avatar: String? = null,
        val personastate: Int? = null,
    )
}
