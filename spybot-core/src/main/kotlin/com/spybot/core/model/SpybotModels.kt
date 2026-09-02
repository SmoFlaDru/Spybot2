package com.spybot.core.model

import java.time.LocalDate
import java.time.OffsetDateTime

data class SelectorOption(
    val text: String,
    val value: Int,
    val active: Boolean,
)

data class MergedUserView(
    val id: Long,
    val name: String,
    val obsolete: Boolean,
    val isSuperuser: Boolean,
    val lastLogin: OffsetDateTime?,
)

data class PasskeyView(
    val id: Long,
    val name: String,
    val platform: String,
    val addedOn: OffsetDateTime?,
    val lastUsed: OffsetDateTime?,
)

data class StoredPasskey(
    val id: Long,
    val userId: Long,
    val name: String,
    val platform: String,
    val addedOn: OffsetDateTime?,
    val lastUsed: OffsetDateTime?,
    val credentialId: String,
    val token: String,
    val enabled: Boolean,
)

data class SteamIdView(
    val id: Long,
    val steamId: Long,
    val accountName: String?,
)

data class ChannelView(
    val id: Int,
    val name: String?,
)

data class LiveClientView(
    val channelId: Int,
    val name: String?,
    val mergedUserId: Long?,
    val steamIds: List<String> = emptyList(),
    val game: String? = null,
    val avatar: String? = null,
)

data class LiveApiResponse(
    val clients: List<LiveApiUser>,
    val channels: List<LiveApiChannel>,
)

data class LiveApiUser(
    val name: String?,
    val channel_id: Int,
)

data class LiveApiChannel(
    val id: Int,
    val name: String?,
)

data class WidgetLegacyResponse(
    val activeClients: List<String?>,
    val inactiveClients: List<String?>,
)

data class DailyActivityPoint(
    val date: String,
    val activeHours: Double,
    val afkHours: Double,
)

data class ActivityChartView(
    val points: List<DailyActivityPoint>,
    val options: List<SelectorOption>,
    val activeOptionText: String,
)

data class TopUserWeek(
    val time: Double,
    val userName: String,
    val userId: Long,
)

data class WeekTrendView(
    val currentWeekSum: Double,
    val compareWeekSum: Double,
    val fraction: Double,
    val deltaPercent: String,
)

data class WeekComparisonPoint(
    val datetime: OffsetDateTime,
    val hoursCurrent: Double?,
    val hoursCompare: Double?,
)

data class ChannelPopularityEntry(
    val name: String,
    val percentage: Double,
)

data class RecentEventView(
    val id: Long,
    val text: String,
    val websiteLink: String?,
    val date: OffsetDateTime,
    val isRecent: Boolean,
)

data class RecentEventsPayload(
    val events: List<RecentEventView>,
    val hasMore: Boolean,
    val start: Int,
)

data class HallOfFameEntry(
    val userId: Long,
    val user: String,
    val time: Double,
    val numGoldAwards: Int,
    val numSilverAwards: Int,
    val numBronzeAwards: Int,
)

data class UserHeadline(
    val names: List<String>,
    val mergedUsername: String,
    val online: Boolean,
    val bronze: Int,
    val silver: Int,
    val gold: Int,
    val afkTime: Double,
    val onlineTime: Double,
    val lastSeen: OffsetDateTime?,
    val firstSeen: OffsetDateTime?,
)

data class StreakView(
    val startDay: LocalDate,
    val endDay: LocalDate,
    val length: Int,
)

data class MonthActivityPoint(
    val month: Int,
    val year: Int,
    val activity: Double,
)

data class TimeRangeView(
    val hours: Int,
    val options: List<SelectorOption>,
)

data class TimelineEntry(
    val x: String,
    val y: List<Long>,
)

data class TimelineUserSeries(
    val name: String,
    val data: List<TimelineEntry>,
)

data class UserPageView(
    val userId: Long,
    val headline: UserHeadline,
    val streak: StreakView?,
    val months: List<MonthActivityPoint>,
    val totalTime: Int,
    val gameId: Int,
    val gameName: String,
)

data class HomePageView(
    val activityChart: ActivityChartView,
    val timeOfDay: List<Pair<String, Double>>,
    val topUsersOfWeek: List<TopUserWeek>,
    val weekTrend: WeekTrendView,
    val weekComparison: List<WeekComparisonPoint>,
    val channelPopularity: List<ChannelPopularityEntry>,
    val recentEvents: RecentEventsPayload,
)

data class AdminMergedUserRow(
    val id: Long,
    val name: String,
    val obsolete: Boolean,
    val isSuperuser: Boolean,
    val tsUserCount: Int,
    val lastLogin: OffsetDateTime?,
)

data class AdminTsUserRow(
    val id: Int,
    val name: String?,
    val mergedUserId: Long?,
    val mergedUserName: String?,
    val isCurrentlyOnline: Boolean,
    val clientId: Int,
)

data class AdminNewsEventRow(
    val id: Long,
    val text: String,
    val websiteLink: String?,
    val date: OffsetDateTime,
)

data class MergeUsersResult(
    val targetId: Long,
    val sourceIds: List<Long>,
    val movedTsUsers: Int,
    val movedSteamIds: Int,
    val movedAwards: Int,
    val movedQueuedMessages: Int,
    val movedLoginLinks: Int,
    val movedPasskeys: Int,
    val obsoletedMergedUsers: Int,
)

enum class OnlineStatus(
    val code: Int,
) {
    OFFLINE(0),
    ONLINE(1),
    BUSY(2),
    AWAY(3),
    SNOOZE(4),
    ;

    companion object {
        fun fromCode(code: Int): OnlineStatus = entries.firstOrNull { it.code == code } ?: OFFLINE
    }
}

data class SteamAccountInfo(
    val steamId: String,
    val gameId: Int,
    val gameName: String,
    val avatarUrl: String,
    val onlineStatus: OnlineStatus,
)

data class TeamSpeakChannelSnapshot(
    val id: Int,
    val name: String,
    val order: Int,
)

data class TeamSpeakClientSnapshot(
    val clientId: Int,
    val channelId: Int,
    val clientDatabaseId: Int,
    val nickname: String,
    val clientType: String,
    val uniqueIdentifier: String,
)

data class OpenSessionView(
    val id: Int,
    val tsUserId: Int,
    val clientId: Int,
    val channelId: Int,
    val tsUserName: String,
)

data class TeamSpeakIdentity(
    val tsUserId: Int,
    val mergedUserId: Long,
    val tsUserName: String,
    val mergedUserName: String,
)

data class QueuedClientMessageView(
    val id: Long,
    val mergedUserId: Long,
    val text: String,
    val type: String,
)

sealed interface TeamSpeakEvent {
    data class ClientEnter(
        val client: TeamSpeakClientSnapshot,
    ) : TeamSpeakEvent

    data class ClientLeave(
        val clientId: Int,
        val channelId: Int,
        val reasonId: Int,
    ) : TeamSpeakEvent

    data class ClientMove(
        val clientId: Int,
        val channelToId: Int,
        val reasonId: Int,
    ) : TeamSpeakEvent
}
