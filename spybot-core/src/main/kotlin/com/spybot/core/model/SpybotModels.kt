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
    /** Passkey provider derived from the authenticator's AAGUID ("iCloud Keychain"); empty if unknown. */
    val platform: String,
    val addedOn: OffsetDateTime?,
    val lastUsed: OffsetDateTime?,
    /** WebAuthn backup state: the passkey is synced by a provider rather than bound to one device. */
    val synced: Boolean,
) {
    /** Tabler sprite icon for the provider, for the profile page. */
    val icon: String
        get() =
            when {
                platform.contains("iCloud", ignoreCase = true) -> "brand-apple"
                platform.contains("Google", ignoreCase = true) || platform.contains("Chrom", ignoreCase = true) -> "brand-google"
                platform.contains("Windows", ignoreCase = true) -> "brand-windows"
                else -> "key"
            }
}

/**
 * A stored passkey with everything Spring Security's WebAuthn support needs to verify an
 * assertion. Binary fields are base64url strings. [userHandle] is the WebAuthn user handle the
 * credential was registered under; it resolves to [userId] via spybot_webauthn_user_handle, which
 * may differ from the registering user after an account merge.
 */
data class WebauthnCredential(
    val userId: Long,
    val userHandle: String,
    val credentialId: String,
    val publicKeyCose: String,
    val signatureCount: Long,
    val uvInitialized: Boolean,
    val transports: List<String>,
    val backupEligible: Boolean,
    val backupState: Boolean,
    val aaguid: String,
    val attestationObject: String,
    val attestationClientDataJson: String,
    val name: String,
    val platform: String,
    val addedOn: OffsetDateTime,
    val lastUsed: OffsetDateTime?,
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

data class ActiveUsersStat(
    val usersThisWeek: Int,
    val usersToday: Int,
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

/** One user's streak of consecutive days online, for the records lists. */
data class StreakRecord(
    val userId: Long,
    val userName: String,
    val startDay: LocalDate,
    val endDay: LocalDate,
    val length: Int,
)

/** A record held by one user: [value] is seconds for sessions and hours for weeks. */
data class UserRecord(
    val userId: Long,
    val userName: String,
    val value: Double,
    val date: LocalDate,
)

/** The most users online at the same time and when that happened. */
data class PeakUsersRecord(
    val users: Int,
    val at: OffsetDateTime,
)

/** The day with the most summed-up online hours. */
data class BusiestDayRecord(
    val day: LocalDate,
    val hours: Double,
)

data class RecordsView(
    val longestStreaks: List<StreakRecord>,
    val currentStreaks: List<StreakRecord>,
    val longestSessions: List<UserRecord>,
    val bestWeeks: List<UserRecord>,
    val peakUsers: PeakUsersRecord?,
    val busiestDay: BusiestDayRecord?,
    val computedAt: OffsetDateTime,
)

/** A user's own bests, shown on their page next to the community records. */
data class PersonalBests(
    val longestSessionSeconds: Double?,
    val longestSessionDate: LocalDate?,
    val bestWeekHours: Double?,
    val bestWeekStart: LocalDate?,
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
    val bests: PersonalBests,
    val months: List<MonthActivityPoint>,
    val totalTime: Int,
    val gameId: Int,
    val gameName: String,
)

data class HomePageView(
    val activityChart: ActivityChartView,
    val timeOfDay: List<Pair<String, Double>>,
    val topUsersOfWeek: List<TopUserWeek>,
    val activeUsers: ActiveUsersStat,
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
    /** Last seen TeamSpeak client id; NULL for identities from before the recorder stored it. */
    val clientId: Int?,
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
    val movedNameLikes: Int,
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
    val parentId: Int,
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

/** Who is liking: a logged-in merged user, or an anonymous visitor identified by cookie. */
sealed interface Liker {
    data class User(
        val mergedUserId: Long,
    ) : Liker

    data class Visitor(
        val visitorId: String,
    ) : Liker
}

/** How many people like a name, and whether the current viewer is one of them. */
data class NameLikeStatus(
    val likes: Int,
    val likedByMe: Boolean,
)

data class LikedNameView(
    val displayName: String,
    val realName: String,
    val slang: String,
    val likes: Int,
    val likedByMe: Boolean,
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
