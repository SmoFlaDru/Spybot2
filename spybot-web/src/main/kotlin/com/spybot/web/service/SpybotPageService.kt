package com.spybot.web.service

import com.spybot.core.model.HomePageView
import com.spybot.core.model.LiveClientView
import com.spybot.core.model.UserPageView
import com.spybot.core.security.MergedUserPrincipal
import com.spybot.core.service.SpybotQueryService
import com.spybot.core.service.SteamService
import org.springframework.stereotype.Service

@Service
class SpybotPageService(
    private val queryService: SpybotQueryService,
    private val steamService: SteamService,
) {
    fun home(timeSpan: Int): HomePageView =
        HomePageView(
            activityChart = queryService.activityChart(timeSpan),
            timeOfDay = queryService.timeOfDayHistogram(),
            topUsersOfWeek = queryService.topUsersOfWeek(),
            activeUsers = queryService.activeUsersStat(),
            weekTrend = queryService.weekTrend(),
            weekComparison = queryService.weekComparison(),
            channelPopularity = queryService.channelPopularity(),
            recentEvents = queryService.recentEvents(0),
        )

    fun live(): Pair<List<com.spybot.core.model.ChannelView>, List<LiveClientView>> {
        val (channels, clients) = queryService.liveClients()
        val steamAccounts =
            steamService
                .getSteamUsersPlayingInfo(
                    clients.flatMap { it.steamIds }.distinct(),
                ).associateBy { it.steamId }

        val enriched =
            clients.map { client ->
                val matchingAccount =
                    client.steamIds
                        .mapNotNull { steamAccounts[it] }
                        .firstOrNull { it.onlineStatus != com.spybot.core.model.OnlineStatus.OFFLINE }

                client.copy(
                    game = matchingAccount?.gameName,
                    avatar = matchingAccount?.avatarUrl,
                )
            }
        return channels to enriched
    }

    fun userPage(userId: Long): UserPageView? {
        val base = queryService.userPage(userId) ?: return null
        if (!base.headline.online) {
            return base
        }

        val steamAccounts =
            steamService.getSteamUsersPlayingInfo(
                queryService.steamIdsForUser(userId).map { it.steamId.toString() },
            )
        val active = steamAccounts.firstOrNull { it.onlineStatus != com.spybot.core.model.OnlineStatus.OFFLINE }
        return base.copy(
            gameId = active?.gameId ?: 0,
            gameName = active?.gameName.orEmpty(),
        )
    }

    fun loggedInUser(principal: MergedUserPrincipal?): com.spybot.core.model.MergedUserView? = principal?.user
}
