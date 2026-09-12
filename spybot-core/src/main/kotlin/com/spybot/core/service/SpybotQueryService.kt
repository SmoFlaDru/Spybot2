package com.spybot.core.service

import com.spybot.core.jooq.notNull
import com.spybot.core.model.ActiveUsersStat
import com.spybot.core.model.ActivityChartView
import com.spybot.core.model.AdminMergedUserRow
import com.spybot.core.model.AdminNewsEventRow
import com.spybot.core.model.AdminTsUserRow
import com.spybot.core.model.ChannelPopularityEntry
import com.spybot.core.model.ChannelView
import com.spybot.core.model.DailyActivityPoint
import com.spybot.core.model.HallOfFameEntry
import com.spybot.core.model.LiveApiChannel
import com.spybot.core.model.LiveApiResponse
import com.spybot.core.model.LiveApiUser
import com.spybot.core.model.LiveClientView
import com.spybot.core.model.MergedUserView
import com.spybot.core.model.MonthActivityPoint
import com.spybot.core.model.OpenSessionView
import com.spybot.core.model.PasskeyView
import com.spybot.core.model.QueuedClientMessageView
import com.spybot.core.model.RecentEventView
import com.spybot.core.model.RecentEventsPayload
import com.spybot.core.model.SelectorOption
import com.spybot.core.model.SteamIdView
import com.spybot.core.model.StreakView
import com.spybot.core.model.TeamSpeakChannelSnapshot
import com.spybot.core.model.TeamSpeakIdentity
import com.spybot.core.model.TimeRangeView
import com.spybot.core.model.TimelineEntry
import com.spybot.core.model.TimelineUserSeries
import com.spybot.core.model.TopUserWeek
import com.spybot.core.model.UserHeadline
import com.spybot.core.model.UserPageView
import com.spybot.core.model.WebauthnCredential
import com.spybot.core.model.WeekComparisonPoint
import com.spybot.core.model.WeekTrendView
import com.spybot.core.model.WidgetLegacyResponse
import com.spybot.jooq.tables.records.SpybotUserpasskeyRecord
import com.spybot.jooq.tables.references.HOURLYACTIVITY
import com.spybot.jooq.tables.references.SPYBOT_AWARD
import com.spybot.jooq.tables.references.SPYBOT_LOGINLINK
import com.spybot.jooq.tables.references.SPYBOT_MERGEDUSER
import com.spybot.jooq.tables.references.SPYBOT_NEWSEVENT
import com.spybot.jooq.tables.references.SPYBOT_QUEUEDCLIENTMESSAGE
import com.spybot.jooq.tables.references.SPYBOT_STEAMID
import com.spybot.jooq.tables.references.SPYBOT_USERPASSKEY
import com.spybot.jooq.tables.references.SPYBOT_WEBAUTHN_USER_HANDLE
import com.spybot.jooq.tables.references.TSCHANNEL
import com.spybot.jooq.tables.references.TSID
import com.spybot.jooq.tables.references.TSUSER
import com.spybot.jooq.tables.references.TSUSERACTIVITY
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.Records.mapping
import org.jooq.impl.DSL
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

@Service
class SpybotQueryService(
    private val dsl: DSLContext,
) {
    fun findMergedUserById(id: Long): MergedUserView? =
        selectMergedUser()
            .from(SPYBOT_MERGEDUSER)
            .where(SPYBOT_MERGEDUSER.ID.eq(id))
            .fetchOne(toMergedUser)

    fun findMergedUserByLoginCode(code: String): MergedUserView? =
        selectMergedUser()
            .from(SPYBOT_LOGINLINK)
            .join(SPYBOT_MERGEDUSER)
            .on(SPYBOT_MERGEDUSER.ID.eq(SPYBOT_LOGINLINK.USER_ID))
            .where(SPYBOT_LOGINLINK.CODE.eq(code))
            .fetchOne(toMergedUser)

    fun touchLastSeen(userId: Long) {
        dsl
            .update(SPYBOT_MERGEDUSER)
            .set(SPYBOT_MERGEDUSER.LAST_LOGIN, DSL.currentOffsetDateTime())
            .where(SPYBOT_MERGEDUSER.ID.eq(userId))
            .execute()
    }

    fun adminMergedUsers(search: String?): List<AdminMergedUserRow> {
        val tsCountField = DSL.count(TSUSER.ID).`as`("ts_user_count")
        val conditions = mutableListOf<Condition>()
        search?.trim()?.takeIf { it.isNotBlank() }?.let { q ->
            val byId = q.toLongOrNull()
            conditions +=
                if (byId != null) {
                    SPYBOT_MERGEDUSER.ID.eq(byId).or(SPYBOT_MERGEDUSER.NAME.containsIgnoreCase(q))
                } else {
                    SPYBOT_MERGEDUSER.NAME.containsIgnoreCase(q)
                }
        }

        return dsl
            .select(
                SPYBOT_MERGEDUSER.ID.notNull(),
                SPYBOT_MERGEDUSER.NAME.notNull(),
                SPYBOT_MERGEDUSER.OBSOLETE.notNull(),
                SPYBOT_MERGEDUSER.IS_SUPERUSER.notNull(),
                tsCountField.notNull(),
                SPYBOT_MERGEDUSER.LAST_LOGIN,
            ).from(SPYBOT_MERGEDUSER)
            .leftJoin(TSUSER)
            .on(TSUSER.MERGED_USER_ID.eq(SPYBOT_MERGEDUSER.ID))
            .where(if (conditions.isEmpty()) DSL.trueCondition() else conditions.reduce(Condition::and))
            .groupBy(
                SPYBOT_MERGEDUSER.ID,
                SPYBOT_MERGEDUSER.NAME,
                SPYBOT_MERGEDUSER.OBSOLETE,
                SPYBOT_MERGEDUSER.IS_SUPERUSER,
                SPYBOT_MERGEDUSER.LAST_LOGIN,
            ).orderBy(tsCountField.desc(), SPYBOT_MERGEDUSER.ID.asc())
            .fetch(mapping(::AdminMergedUserRow))
    }

    fun adminTsUsers(search: String?): List<AdminTsUserRow> {
        val conditions = mutableListOf<Condition>()
        search?.trim()?.takeIf { it.isNotBlank() }?.let { q ->
            val byId = q.toIntOrNull()
            val byMergedUserId = q.toLongOrNull()
            val idMatch = if (byId != null) TSUSER.ID.eq(byId) else DSL.falseCondition()
            val mergedIdMatch = if (byMergedUserId != null) TSUSER.MERGED_USER_ID.eq(byMergedUserId) else DSL.falseCondition()
            conditions +=
                idMatch
                    .or(mergedIdMatch)
                    .or(TSUSER.NAME.containsIgnoreCase(q))
                    .or(SPYBOT_MERGEDUSER.NAME.containsIgnoreCase(q))
        }

        return dsl
            .select(
                TSUSER.ID.notNull(),
                TSUSER.NAME,
                TSUSER.MERGED_USER_ID,
                SPYBOT_MERGEDUSER.NAME,
                TSUSER.ISCURRENTLYONLINE.notNull(),
                TSUSER.CLIENTID.notNull(),
            ).from(TSUSER)
            .leftJoin(SPYBOT_MERGEDUSER)
            .on(SPYBOT_MERGEDUSER.ID.eq(TSUSER.MERGED_USER_ID))
            .where(if (conditions.isEmpty()) DSL.trueCondition() else conditions.reduce(Condition::and))
            .orderBy(TSUSER.ID.desc())
            .fetch(mapping(::AdminTsUserRow))
    }

    fun adminNewsEvents(search: String?): List<AdminNewsEventRow> {
        val conditions = mutableListOf<Condition>()
        search?.trim()?.takeIf { it.isNotBlank() }?.let { q ->
            val byId = q.toLongOrNull()
            conditions +=
                if (byId != null) {
                    SPYBOT_NEWSEVENT.ID.eq(byId).or(SPYBOT_NEWSEVENT.TEXT.containsIgnoreCase(q))
                } else {
                    SPYBOT_NEWSEVENT.TEXT.containsIgnoreCase(q)
                }
        }

        return selectNewsEvent()
            .from(SPYBOT_NEWSEVENT)
            .where(if (conditions.isEmpty()) DSL.trueCondition() else conditions.reduce(Condition::and))
            .orderBy(SPYBOT_NEWSEVENT.DATE.desc(), SPYBOT_NEWSEVENT.ID.desc())
            .fetch(toNewsEvent)
    }

    fun adminNewsEventById(id: Long): AdminNewsEventRow? =
        selectNewsEvent()
            .from(SPYBOT_NEWSEVENT)
            .where(SPYBOT_NEWSEVENT.ID.eq(id))
            .fetchOne(toNewsEvent)

    fun adminCreateNewsEvent(
        text: String,
        websiteLink: String?,
    ): Long =
        dsl
            .insertInto(SPYBOT_NEWSEVENT)
            .set(SPYBOT_NEWSEVENT.TEXT, text)
            .set(SPYBOT_NEWSEVENT.WEBSITE_LINK, websiteLink)
            .set(SPYBOT_NEWSEVENT.DATE, DSL.currentOffsetDateTime())
            .returning(SPYBOT_NEWSEVENT.ID)
            .fetchSingle(SPYBOT_NEWSEVENT.ID) ?: 0L

    fun adminUpdateNewsEvent(
        id: Long,
        text: String,
        websiteLink: String?,
    ): Boolean =
        dsl
            .update(SPYBOT_NEWSEVENT)
            .set(SPYBOT_NEWSEVENT.TEXT, text)
            .set(SPYBOT_NEWSEVENT.WEBSITE_LINK, websiteLink)
            .where(SPYBOT_NEWSEVENT.ID.eq(id))
            .execute() > 0

    fun adminDeleteNewsEvent(id: Long): Boolean =
        dsl
            .deleteFrom(SPYBOT_NEWSEVENT)
            .where(SPYBOT_NEWSEVENT.ID.eq(id))
            .execute() > 0

    fun adminFindMergedUsersByIds(ids: Collection<Long>): List<MergedUserView> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        return selectMergedUser()
            .from(SPYBOT_MERGEDUSER)
            .where(SPYBOT_MERGEDUSER.ID.`in`(ids))
            .fetch(toMergedUser)
    }

    fun adminSetMergedUserSuperuser(
        id: Long,
        isSuperuser: Boolean,
    ): Int =
        dsl
            .update(SPYBOT_MERGEDUSER)
            .set(SPYBOT_MERGEDUSER.IS_SUPERUSER, isSuperuser)
            .where(SPYBOT_MERGEDUSER.ID.eq(id))
            .execute()

    fun adminSetMergedUsersObsolete(
        ids: Collection<Long>,
        obsolete: Boolean,
    ): Int {
        if (ids.isEmpty()) {
            return 0
        }
        return dsl
            .update(SPYBOT_MERGEDUSER)
            .set(SPYBOT_MERGEDUSER.OBSOLETE, obsolete)
            .where(SPYBOT_MERGEDUSER.ID.`in`(ids))
            .execute()
    }

    fun adminReassignTsUsers(
        sourceIds: Collection<Long>,
        targetId: Long,
    ): Int {
        if (sourceIds.isEmpty()) {
            return 0
        }
        return dsl
            .update(TSUSER)
            .set(TSUSER.MERGED_USER_ID, targetId)
            .where(TSUSER.MERGED_USER_ID.`in`(sourceIds))
            .execute()
    }

    fun adminReassignSteamIds(
        sourceIds: Collection<Long>,
        targetId: Long,
    ): Int {
        if (sourceIds.isEmpty()) {
            return 0
        }
        return dsl
            .update(SPYBOT_STEAMID)
            .set(SPYBOT_STEAMID.MERGED_USER_ID, targetId)
            .where(SPYBOT_STEAMID.MERGED_USER_ID.`in`(sourceIds))
            .execute()
    }

    fun adminReassignAwards(
        sourceIds: Collection<Long>,
        targetId: Long,
    ): Int {
        if (sourceIds.isEmpty()) {
            return 0
        }
        return dsl
            .update(SPYBOT_AWARD)
            .set(SPYBOT_AWARD.MERGED_USER_ID, targetId)
            .where(SPYBOT_AWARD.MERGED_USER_ID.`in`(sourceIds))
            .execute()
    }

    fun adminReassignQueuedMessages(
        sourceIds: Collection<Long>,
        targetId: Long,
    ): Int {
        if (sourceIds.isEmpty()) {
            return 0
        }
        return dsl
            .update(SPYBOT_QUEUEDCLIENTMESSAGE)
            .set(SPYBOT_QUEUEDCLIENTMESSAGE.MERGED_USER_ID, targetId)
            .where(SPYBOT_QUEUEDCLIENTMESSAGE.MERGED_USER_ID.`in`(sourceIds))
            .execute()
    }

    fun adminReassignLoginLinks(
        sourceIds: Collection<Long>,
        targetId: Long,
    ): Int {
        if (sourceIds.isEmpty()) {
            return 0
        }
        return dsl
            .update(SPYBOT_LOGINLINK)
            .set(SPYBOT_LOGINLINK.USER_ID, targetId)
            .where(SPYBOT_LOGINLINK.USER_ID.`in`(sourceIds))
            .execute()
    }

    fun adminReassignPasskeys(
        sourceIds: Collection<Long>,
        targetId: Long,
    ): Int {
        if (sourceIds.isEmpty()) {
            return 0
        }
        return dsl
            .update(SPYBOT_USERPASSKEY)
            .set(SPYBOT_USERPASSKEY.USER_ID, targetId)
            .where(SPYBOT_USERPASSKEY.USER_ID.`in`(sourceIds))
            .execute()
    }

    fun passkeysForUser(userId: Long): List<PasskeyView> =
        dsl
            .select(
                SPYBOT_USERPASSKEY.ID.notNull(),
                SPYBOT_USERPASSKEY.NAME.notNull(),
                SPYBOT_USERPASSKEY.PLATFORM.notNull(),
                SPYBOT_USERPASSKEY.ADDED_ON,
                SPYBOT_USERPASSKEY.LAST_USED,
                SPYBOT_USERPASSKEY.BACKUP_STATE.notNull(),
            ).from(SPYBOT_USERPASSKEY)
            .where(SPYBOT_USERPASSKEY.USER_ID.eq(userId))
            .orderBy(SPYBOT_USERPASSKEY.ADDED_ON.desc())
            .fetch(mapping(::PasskeyView))

    fun renamePasskey(
        userId: Long,
        passkeyId: Long,
        name: String,
    ): Boolean =
        dsl
            .update(SPYBOT_USERPASSKEY)
            .set(SPYBOT_USERPASSKEY.NAME, name)
            .where(SPYBOT_USERPASSKEY.ID.eq(passkeyId))
            .and(SPYBOT_USERPASSKEY.USER_ID.eq(userId))
            .execute() > 0

    fun deletePasskey(
        userId: Long,
        passkeyId: Long,
    ): Boolean =
        dsl
            .deleteFrom(SPYBOT_USERPASSKEY)
            .where(SPYBOT_USERPASSKEY.ID.eq(passkeyId))
            .and(SPYBOT_USERPASSKEY.USER_ID.eq(userId))
            .execute() > 0

    // ---- WebAuthn credentials and user handles (see WebauthnCredential) ----

    fun findWebauthnCredential(credentialId: String): WebauthnCredential? =
        dsl
            .selectFrom(SPYBOT_USERPASSKEY)
            .where(SPYBOT_USERPASSKEY.CREDENTIAL_ID.eq(credentialId))
            .fetchOne()
            ?.toWebauthnCredential()

    /** Every credential of the user a handle resolves to, across all of that user's handles. */
    fun webauthnCredentialsForHandle(handle: String): List<WebauthnCredential> {
        val userId = findWebauthnUserIdByHandle(handle) ?: return emptyList()
        return webauthnCredentialsForUser(userId)
    }

    /** Inserts a new credential or updates the mutable parts of an existing one (counter, flags, last use). */
    fun saveWebauthnCredential(credential: WebauthnCredential) {
        dsl
            .insertInto(SPYBOT_USERPASSKEY)
            .set(SPYBOT_USERPASSKEY.USER_ID, credential.userId)
            .set(SPYBOT_USERPASSKEY.USER_HANDLE, credential.userHandle)
            .set(SPYBOT_USERPASSKEY.CREDENTIAL_ID, credential.credentialId)
            .set(SPYBOT_USERPASSKEY.PUBLIC_KEY_COSE, credential.publicKeyCose)
            .set(SPYBOT_USERPASSKEY.SIGNATURE_COUNT, credential.signatureCount)
            .set(SPYBOT_USERPASSKEY.UV_INITIALIZED, credential.uvInitialized)
            .set(SPYBOT_USERPASSKEY.TRANSPORTS, credential.transports.joinToString(","))
            .set(SPYBOT_USERPASSKEY.BACKUP_ELIGIBLE, credential.backupEligible)
            .set(SPYBOT_USERPASSKEY.BACKUP_STATE, credential.backupState)
            .set(SPYBOT_USERPASSKEY.AAGUID, credential.aaguid)
            .set(SPYBOT_USERPASSKEY.ATTESTATION_OBJECT, credential.attestationObject)
            .set(SPYBOT_USERPASSKEY.ATTESTATION_CLIENT_DATA_JSON, credential.attestationClientDataJson)
            .set(SPYBOT_USERPASSKEY.NAME, credential.name)
            .set(SPYBOT_USERPASSKEY.PLATFORM, credential.platform)
            .set(SPYBOT_USERPASSKEY.ENABLED, true)
            .set(SPYBOT_USERPASSKEY.ADDED_ON, credential.addedOn)
            .set(SPYBOT_USERPASSKEY.LAST_USED, credential.lastUsed)
            .onConflict(SPYBOT_USERPASSKEY.CREDENTIAL_ID)
            .doUpdate()
            .set(SPYBOT_USERPASSKEY.SIGNATURE_COUNT, credential.signatureCount)
            .set(SPYBOT_USERPASSKEY.UV_INITIALIZED, credential.uvInitialized)
            .set(SPYBOT_USERPASSKEY.BACKUP_STATE, credential.backupState)
            .set(SPYBOT_USERPASSKEY.LAST_USED, credential.lastUsed)
            .execute()
    }

    fun deleteWebauthnCredential(credentialId: String): Boolean =
        dsl
            .deleteFrom(SPYBOT_USERPASSKEY)
            .where(SPYBOT_USERPASSKEY.CREDENTIAL_ID.eq(credentialId))
            .execute() > 0

    fun findWebauthnUserIdByHandle(handle: String): Long? =
        dsl
            .select(SPYBOT_WEBAUTHN_USER_HANDLE.MERGED_USER_ID)
            .from(SPYBOT_WEBAUTHN_USER_HANDLE)
            .where(SPYBOT_WEBAUTHN_USER_HANDLE.HANDLE.eq(handle))
            .fetchOne(SPYBOT_WEBAUTHN_USER_HANDLE.MERGED_USER_ID)

    /** Every handle a user owns, including ones inherited through merges. */
    fun webauthnUserHandlesForUser(userId: Long): List<String> =
        dsl
            .select(SPYBOT_WEBAUTHN_USER_HANDLE.HANDLE)
            .from(SPYBOT_WEBAUTHN_USER_HANDLE)
            .where(SPYBOT_WEBAUTHN_USER_HANDLE.MERGED_USER_ID.eq(userId))
            .orderBy(SPYBOT_WEBAUTHN_USER_HANDLE.CREATED.asc())
            .fetch(SPYBOT_WEBAUTHN_USER_HANDLE.HANDLE)
            .filterNotNull()

    fun webauthnCredentialsForUser(userId: Long): List<WebauthnCredential> =
        dsl
            .selectFrom(SPYBOT_USERPASSKEY)
            .where(SPYBOT_USERPASSKEY.USER_ID.eq(userId))
            .fetch()
            .map { it.toWebauthnCredential() }

    /**
     * The handle new passkeys are registered under: the user's oldest one, created on demand.
     * Older handles a merged-in account brought along stay valid for the passkeys that carry them.
     */
    fun findOrCreateWebauthnUserHandle(
        userId: Long,
        newHandle: () -> String,
    ): String {
        val existing =
            dsl
                .select(SPYBOT_WEBAUTHN_USER_HANDLE.HANDLE)
                .from(SPYBOT_WEBAUTHN_USER_HANDLE)
                .where(SPYBOT_WEBAUTHN_USER_HANDLE.MERGED_USER_ID.eq(userId))
                .orderBy(SPYBOT_WEBAUTHN_USER_HANDLE.CREATED.asc())
                .limit(1)
                .fetchOne(SPYBOT_WEBAUTHN_USER_HANDLE.HANDLE)
        if (existing != null) return existing
        val handle = newHandle()
        saveWebauthnUserHandle(handle, userId)
        return handle
    }

    fun saveWebauthnUserHandle(
        handle: String,
        userId: Long,
    ) {
        dsl
            .insertInto(SPYBOT_WEBAUTHN_USER_HANDLE)
            .set(SPYBOT_WEBAUTHN_USER_HANDLE.HANDLE, handle)
            .set(SPYBOT_WEBAUTHN_USER_HANDLE.MERGED_USER_ID, userId)
            .set(SPYBOT_WEBAUTHN_USER_HANDLE.CREATED, DSL.currentOffsetDateTime())
            .onConflict(SPYBOT_WEBAUTHN_USER_HANDLE.HANDLE)
            .doUpdate()
            .set(SPYBOT_WEBAUTHN_USER_HANDLE.MERGED_USER_ID, userId)
            .execute()
    }

    fun deleteWebauthnUserHandle(handle: String) {
        dsl
            .deleteFrom(SPYBOT_WEBAUTHN_USER_HANDLE)
            .where(SPYBOT_WEBAUTHN_USER_HANDLE.HANDLE.eq(handle))
            .execute()
    }

    /** Part of merging users: handles move with the passkeys so those passkeys keep logging in. */
    fun adminReassignWebauthnUserHandles(
        sourceIds: Collection<Long>,
        targetId: Long,
    ): Int {
        if (sourceIds.isEmpty()) {
            return 0
        }
        return dsl
            .update(SPYBOT_WEBAUTHN_USER_HANDLE)
            .set(SPYBOT_WEBAUTHN_USER_HANDLE.MERGED_USER_ID, targetId)
            .where(SPYBOT_WEBAUTHN_USER_HANDLE.MERGED_USER_ID.`in`(sourceIds))
            .execute()
    }

    private fun SpybotUserpasskeyRecord.toWebauthnCredential(): WebauthnCredential =
        WebauthnCredential(
            userId = userId!!,
            userHandle = userHandle!!,
            credentialId = credentialId!!,
            publicKeyCose = publicKeyCose!!,
            signatureCount = signatureCount ?: 0L,
            uvInitialized = uvInitialized ?: false,
            transports = transports.orEmpty().split(',').filter { it.isNotBlank() },
            backupEligible = backupEligible ?: false,
            backupState = backupState ?: false,
            aaguid = aaguid.orEmpty(),
            attestationObject = attestationObject!!,
            attestationClientDataJson = attestationClientDataJson!!,
            name = name.orEmpty(),
            platform = platform.orEmpty(),
            addedOn = addedOn ?: OffsetDateTime.now(ZoneOffset.UTC),
            lastUsed = lastUsed,
        )

    fun steamIdsForUser(userId: Long): List<SteamIdView> =
        dsl
            .select(SPYBOT_STEAMID.ID.notNull(), SPYBOT_STEAMID.STEAM_ID.notNull(), SPYBOT_STEAMID.ACCOUNT_NAME)
            .from(SPYBOT_STEAMID)
            .where(SPYBOT_STEAMID.MERGED_USER_ID.eq(userId))
            .orderBy(SPYBOT_STEAMID.ID.desc())
            .fetch(mapping(::SteamIdView))

    fun addSteamId(
        userId: Long,
        steamId: Long,
        accountName: String?,
    ): Long =
        dsl
            .insertInto(SPYBOT_STEAMID)
            .set(SPYBOT_STEAMID.STEAM_ID, steamId)
            .set(SPYBOT_STEAMID.ACCOUNT_NAME, accountName)
            .set(SPYBOT_STEAMID.MERGED_USER_ID, userId)
            .returning(SPYBOT_STEAMID.ID)
            .fetchSingle(SPYBOT_STEAMID.ID) ?: 0L

    fun deleteSteamId(
        userId: Long,
        steamIdId: Long,
    ): Boolean =
        dsl
            .deleteFrom(SPYBOT_STEAMID)
            .where(SPYBOT_STEAMID.ID.eq(steamIdId))
            .and(SPYBOT_STEAMID.MERGED_USER_ID.eq(userId))
            .execute() > 0

    @Transactional
    fun upsertChannels(channels: List<TeamSpeakChannelSnapshot>) {
        if (channels.isEmpty()) {
            return
        }
        // tschannel."order" has a UNIQUE constraint. If the TeamSpeak server reordered
        // channels since the last sync, a straight per-row upsert can collide: row A wants
        // the "order" value that row B currently still holds, and B hasn't been updated yet.
        // Shift every touched row to a temporary, mutually-unique negative order first (ids
        // are unique and always positive, so -id can never collide with another temp value
        // or with any real order value) so the second pass can set the real values freely.
        channels.forEach { channel ->
            dsl
                .update(TSCHANNEL)
                .set(TSCHANNEL.ORDER, -channel.id)
                .where(TSCHANNEL.ID.eq(channel.id))
                .execute()
        }
        channels.forEach { channel ->
            dsl.execute(
                """
                insert into tschannel (id, name, "order", pid)
                values (?, ?, ?, ?)
                on conflict (id) do update
                set name = excluded.name,
                    "order" = excluded."order",
                    pid = excluded.pid
                """.trimIndent(),
                channel.id,
                channel.name,
                channel.order,
                channel.parentId,
            )
        }
    }

    fun updateChannelName(
        channelId: Int,
        escapedChannelName: String,
    ) {
        dsl
            .update(TSCHANNEL)
            .set(TSCHANNEL.NAME, escapedChannelName)
            .where(TSCHANNEL.ID.eq(channelId))
            .execute()
    }

    fun findIdentityByUniqueIdentifier(uniqueIdentifier: String): TeamSpeakIdentity? =
        dsl
            .fetchOne(
                """
                select tu.id as ts_user_id,
                       mu.id as merged_user_id,
                       tu.name as ts_user_name,
                       mu.name as merged_user_name
                from tsid tid
                join tsuser tu on tu.id = tid.tsuserid
                join spybot_mergeduser mu on mu.id = tu.merged_user_id
                where tid.tsid = ?
                """.trimIndent(),
                uniqueIdentifier,
            )?.toTeamSpeakIdentity()

    @Transactional
    fun createTeamSpeakIdentity(
        nickname: String,
        clientId: Int,
        uniqueIdentifier: String,
    ): TeamSpeakIdentity {
        val mergedUserId =
            dsl
                .insertInto(SPYBOT_MERGEDUSER)
                .set(SPYBOT_MERGEDUSER.PASSWORD, "")
                .set(SPYBOT_MERGEDUSER.NAME, nickname)
                .set(SPYBOT_MERGEDUSER.OBSOLETE, false)
                .set(SPYBOT_MERGEDUSER.IS_SUPERUSER, false)
                .returning(SPYBOT_MERGEDUSER.ID)
                .fetchSingle(SPYBOT_MERGEDUSER.ID) ?: 0L
        val tsUserId =
            dsl
                .insertInto(TSUSER)
                .set(TSUSER.NAME, nickname)
                .set(TSUSER.CLIENTID, clientId)
                .set(TSUSER.ISCURRENTLYONLINE, false)
                .set(TSUSER.MERGED_USER_ID, mergedUserId)
                .returning(TSUSER.ID)
                .fetchSingle(TSUSER.ID) ?: 0
        dsl
            .insertInto(TSID)
            .set(TSID.TSID_, uniqueIdentifier)
            .set(TSID.TSUSERID, tsUserId)
            .execute()
        return TeamSpeakIdentity(
            tsUserId = tsUserId,
            mergedUserId = mergedUserId,
            tsUserName = nickname,
            mergedUserName = nickname,
        )
    }

    @Transactional
    fun renameIdentity(
        identity: TeamSpeakIdentity,
        nickname: String,
    ): TeamSpeakIdentity {
        if (identity.tsUserName == nickname) {
            return identity
        }
        dsl
            .update(TSUSER)
            .set(TSUSER.NAME, nickname)
            .where(TSUSER.ID.eq(identity.tsUserId))
            .execute()
        val mergedUserName =
            if (identity.mergedUserName == identity.tsUserName) {
                dsl
                    .update(SPYBOT_MERGEDUSER)
                    .set(SPYBOT_MERGEDUSER.NAME, nickname)
                    .where(SPYBOT_MERGEDUSER.ID.eq(identity.mergedUserId))
                    .execute()
                nickname
            } else {
                identity.mergedUserName
            }
        return identity.copy(tsUserName = nickname, mergedUserName = mergedUserName)
    }

    @Transactional
    fun markClientSessionStarted(
        tsUserId: Int,
        channelId: Int,
        clientId: Int,
        joined: Boolean,
    ) {
        dsl
            .update(TSUSER)
            .set(TSUSER.CLIENTID, clientId)
            .set(TSUSER.ISCURRENTLYONLINE, true)
            .where(TSUSER.ID.eq(tsUserId))
            .execute()
        dsl
            .insertInto(TSUSERACTIVITY)
            .set(TSUSERACTIVITY.TSUSERID, tsUserId)
            .set(TSUSERACTIVITY.STARTTIME, DSL.currentOffsetDateTime())
            .set(TSUSERACTIVITY.JOINED, joined)
            .set(TSUSERACTIVITY.CID, channelId)
            .execute()
    }

    /**
     * A channel move ends the current session and starts the next one. If the second half
     * failed, the user would silently drop out of the live view until another event touched
     * them - so both happen in one transaction.
     */
    @Transactional
    fun moveClientSession(
        tsUserId: Int,
        channelId: Int,
        clientId: Int,
        reasonId: Int,
    ) {
        closeOpenSessionsForUser(tsUserId, reasonId)
        markClientSessionStarted(tsUserId, channelId, clientId, joined = false)
    }

    @Transactional
    fun closeOpenSessionsForUser(
        tsUserId: Int,
        reasonId: Int,
    ) {
        dsl
            .update(TSUSER)
            .set(TSUSER.CLIENTID, 0)
            .set(TSUSER.ISCURRENTLYONLINE, false)
            .where(TSUSER.ID.eq(tsUserId))
            .execute()
        dsl.execute(
            """
            update tsuseractivity
            set endtime = now(),
                discid = ?
            where tsuserid = ?
              and endtime is null
            """.trimIndent(),
            reasonId,
            tsUserId,
        )
    }

    fun findIdentityByClientId(clientId: Int): TeamSpeakIdentity? =
        dsl
            .fetchOne(
                """
                select tu.id as ts_user_id,
                       mu.id as merged_user_id,
                       tu.name as ts_user_name,
                       mu.name as merged_user_name
                from tsuser tu
                join spybot_mergeduser mu on mu.id = tu.merged_user_id
                where tu.clientid = ?
                """.trimIndent(),
                clientId,
            )?.toTeamSpeakIdentity()

    fun openSessions(): List<OpenSessionView> =
        dsl
            .fetch(
                """
                select a.id as activity_id,
                       a.tsuserid as ts_user_id,
                       u.clientid as client_id,
                       a.cid as channel_id,
                       u.name as ts_user_name
                from tsuseractivity a
                join tsuser u on u.id = a.tsuserid
                where a.endtime is null
                order by a.starttime desc
                """.trimIndent(),
            ).map {
                OpenSessionView(
                    id = it.int("activity_id"),
                    tsUserId = it.int("ts_user_id"),
                    clientId = it.int("client_id"),
                    channelId = it.int("channel_id"),
                    tsUserName = it.string("ts_user_name"),
                )
            }

    /**
     * Closes one specific stale session row by its own id, unlike [closeOpenSessionsForUser]
     * which closes every open row for the user. A user can briefly have two open rows at once
     * (e.g. a reconnect's ClientEnter is processed before the old connection's ClientLeave), and
     * closing by tsUserId in that situation would also close the sibling row that's still
     * genuinely live - permanently dropping them from the live view until another TeamSpeak event
     * happens to touch them again.
     */
    @Transactional
    fun closeOpenSession(
        activityId: Int,
        tsUserId: Int,
        reasonId: Int,
    ) {
        dsl.execute(
            """
            update tsuseractivity
            set endtime = now(),
                discid = ?
            where id = ?
              and endtime is null
            """.trimIndent(),
            reasonId,
            activityId,
        )
        val stillOpen =
            dsl.fetchExists(
                dsl
                    .selectOne()
                    .from(TSUSERACTIVITY)
                    .where(TSUSERACTIVITY.TSUSERID.eq(tsUserId))
                    .and(TSUSERACTIVITY.ENDTIME.isNull),
            )
        if (!stillOpen) {
            dsl
                .update(TSUSER)
                .set(TSUSER.CLIENTID, 0)
                .set(TSUSER.ISCURRENTLYONLINE, false)
                .where(TSUSER.ID.eq(tsUserId))
                .execute()
        }
    }

    fun queuedMessagesForMergedUser(mergedUserId: Long): List<QueuedClientMessageView> =
        dsl
            .select(
                SPYBOT_QUEUEDCLIENTMESSAGE.ID.notNull(),
                SPYBOT_QUEUEDCLIENTMESSAGE.MERGED_USER_ID.notNull(),
                SPYBOT_QUEUEDCLIENTMESSAGE.TEXT.notNull(),
                SPYBOT_QUEUEDCLIENTMESSAGE.TYPE.notNull(),
            ).from(SPYBOT_QUEUEDCLIENTMESSAGE)
            .where(SPYBOT_QUEUEDCLIENTMESSAGE.MERGED_USER_ID.eq(mergedUserId))
            .orderBy(SPYBOT_QUEUEDCLIENTMESSAGE.DATE.desc(), SPYBOT_QUEUEDCLIENTMESSAGE.ID.desc())
            .fetch(mapping(::QueuedClientMessageView))

    fun deleteQueuedMessage(messageId: Long) {
        dsl
            .deleteFrom(SPYBOT_QUEUEDCLIENTMESSAGE)
            .where(SPYBOT_QUEUEDCLIENTMESSAGE.ID.eq(messageId))
            .execute()
    }

    @Transactional
    fun replaceQueuedMessage(
        mergedUserId: Long,
        type: String,
        text: String,
    ) {
        dsl
            .deleteFrom(SPYBOT_QUEUEDCLIENTMESSAGE)
            .where(SPYBOT_QUEUEDCLIENTMESSAGE.MERGED_USER_ID.eq(mergedUserId))
            .and(SPYBOT_QUEUEDCLIENTMESSAGE.TYPE.eq(type))
            .execute()
        dsl
            .insertInto(SPYBOT_QUEUEDCLIENTMESSAGE)
            .set(SPYBOT_QUEUEDCLIENTMESSAGE.TSUSER_ID, null as Int?)
            .set(SPYBOT_QUEUEDCLIENTMESSAGE.MERGED_USER_ID, mergedUserId)
            .set(SPYBOT_QUEUEDCLIENTMESSAGE.TEXT, text)
            .set(SPYBOT_QUEUEDCLIENTMESSAGE.TYPE, type)
            .set(SPYBOT_QUEUEDCLIENTMESSAGE.DATE, DSL.currentLocalDate())
            .execute()
    }

    fun createLoginLink(
        userId: Long,
        code: String,
    ) {
        dsl
            .insertInto(SPYBOT_LOGINLINK)
            .set(SPYBOT_LOGINLINK.CODE, code)
            .set(SPYBOT_LOGINLINK.USER_ID, userId)
            .execute()
    }

    fun mergedUserName(userId: Long): String? =
        dsl
            .select(SPYBOT_MERGEDUSER.NAME)
            .from(SPYBOT_MERGEDUSER)
            .where(SPYBOT_MERGEDUSER.ID.eq(userId))
            .fetchOne(SPYBOT_MERGEDUSER.NAME)

    fun countAwardsForUser(userId: Long): Int = dsl.fetchCount(SPYBOT_AWARD, SPYBOT_AWARD.MERGED_USER_ID.eq(userId))

    fun countAwardsForUserByPoints(
        userId: Long,
        points: Int,
    ): Int =
        dsl.fetchCount(
            SPYBOT_AWARD,
            SPYBOT_AWARD.MERGED_USER_ID.eq(userId).and(SPYBOT_AWARD.POINTS.eq(points)),
        )

    fun createAward(
        mergedUserId: Long,
        points: Int,
    ) {
        dsl
            .insertInto(SPYBOT_AWARD)
            .set(SPYBOT_AWARD.TYPE, "USER_OF_WEEK")
            .set(SPYBOT_AWARD.POINTS, points)
            .set(SPYBOT_AWARD.TSUSER_ID, null as Int?)
            .set(SPYBOT_AWARD.MERGED_USER_ID, mergedUserId)
            .set(SPYBOT_AWARD.DATE, DSL.currentOffsetDateTime())
            .execute()
    }

    fun createNewsEvent(
        text: String,
        websiteLink: String?,
    ) {
        dsl
            .insertInto(SPYBOT_NEWSEVENT)
            .set(SPYBOT_NEWSEVENT.TEXT, text)
            .set(SPYBOT_NEWSEVENT.WEBSITE_LINK, websiteLink)
            .set(SPYBOT_NEWSEVENT.DATE, DSL.currentOffsetDateTime())
            .execute()
    }

    fun liveApi(): LiveApiResponse {
        val channels =
            dsl
                .select(TSCHANNEL.ID.notNull(), TSCHANNEL.NAME)
                .from(TSCHANNEL)
                .orderBy(TSCHANNEL.ORDER.asc())
                .fetch(mapping(::LiveApiChannel))
        val clients =
            dsl
                .select(TSUSER.NAME, TSUSERACTIVITY.CID.notNull())
                .from(TSUSERACTIVITY)
                .join(TSUSER)
                .on(TSUSER.ID.eq(TSUSERACTIVITY.TSUSERID))
                .where(TSUSERACTIVITY.ENDTIME.isNull)
                .fetch(mapping(::LiveApiUser))

        return LiveApiResponse(clients = clients, channels = channels)
    }

    fun widgetLegacy(): WidgetLegacyResponse {
        val active = mutableListOf<String?>()
        val inactive = mutableListOf<String?>()
        dsl
            .fetch(
                """
                select u.name as user_name, c.name as channel_name
                from tsuseractivity a
                join tsuser u on u.id = a.tsuserid
                join tschannel c on c.id = a.cid
                where a.endtime is null
                """.trimIndent(),
            ).forEach { record ->
                when (record.string("channel_name")) {
                    "bei Bedarf anstupsen", "AFK" -> inactive += record.get("user_name", String::class.java)
                    else -> active += record.get("user_name", String::class.java)
                }
            }

        return WidgetLegacyResponse(activeClients = active, inactiveClients = inactive)
    }

    fun liveClients(): Pair<List<ChannelView>, List<LiveClientView>> {
        val channels =
            dsl
                .select(TSCHANNEL.ID.notNull(), TSCHANNEL.NAME)
                .from(TSCHANNEL)
                .orderBy(TSCHANNEL.ORDER.asc())
                .fetch(mapping { id, name -> ChannelView(id, name?.let(::unescapeTeamSpeak)) })

        val clients =
            dsl
                .fetch(
                    """
                    select
                        a.cid as channel_id,
                        u.name,
                        u.merged_user_id,
                        coalesce(
                            (
                                select json_agg(s.steam_id::text)
                                from spybot_steamid s
                                where s.merged_user_id = u.merged_user_id
                            )::text,
                            '[]'
                        ) as steam_ids
                    from tsuseractivity a
                    join tsuser u on u.id = a.tsuserid
                    where a.endtime is null
                    """.trimIndent(),
                ).map {
                    LiveClientView(
                        channelId = it.int("channel_id"),
                        name = it.get("name", String::class.java),
                        mergedUserId = it.get("merged_user_id", Long::class.java),
                        steamIds = parseJsonArray(it.string("steam_ids")),
                    )
                }

        return channels to clients
    }

    fun activityChart(timeSpan: Int): ActivityChartView {
        val allowed = listOf(7, 14, 30, 90)
        val selected = allowed.firstOrNull { it == timeSpan } ?: allowed.first()
        val options = allowed.map { SelectorOption("Last $it days", it, it == selected) }
        val points =
            dsl
                .fetch(
                    """
                    WITH active_data AS (
                        SELECT
                            TO_CHAR(starttime, 'YYYY-MM-DD') AS date,
                            SUM(EXTRACT(EPOCH FROM AGE(endtime, starttime))) / 3600 AS time_hours
                        FROM tsuseractivity
                        INNER JOIN tschannel channel ON tsuseractivity.cid = channel.id
                        WHERE
                            starttime > CURRENT_DATE - (? || ' days')::interval
                            AND endtime IS NOT NULL
                            AND channel.name NOT IN ('bei\sBedarf\sanstupsen', 'AFK')
                        GROUP BY date
                        ORDER BY date
                    ),
                    afk_data AS (
                        SELECT
                            TO_CHAR(starttime, 'YYYY-MM-DD') AS date,
                            SUM(EXTRACT(EPOCH FROM AGE(endtime, starttime))) / 3600 AS time_hours
                        FROM tsuseractivity
                        INNER JOIN tschannel channel ON tsuseractivity.cid = channel.id
                        WHERE
                            starttime > CURRENT_DATE - (? || ' days')::interval
                            AND endtime IS NOT NULL
                            AND channel.name IN ('bei\sBedarf\sanstupsen', 'AFK')
                        GROUP BY date
                        ORDER BY date
                    )
                    SELECT active_data.date,
                           CAST(active_data.time_hours AS DOUBLE PRECISION) AS active_hours,
                           COALESCE(CAST(afk_data.time_hours AS DOUBLE PRECISION), 0) AS afk_hours
                    FROM active_data
                    LEFT OUTER JOIN afk_data ON active_data.date = afk_data.date
                    """.trimIndent(),
                    selected,
                    selected,
                ).map {
                    DailyActivityPoint(
                        date = it.string("date"),
                        activeHours = it.double("active_hours"),
                        afkHours = it.double("afk_hours"),
                    )
                }

        return ActivityChartView(points = points, options = options, activeOptionText = "Last $selected days")
    }

    fun timeOfDayHistogram(): List<Pair<String, Double>> =
        run {
            val hourField = DSL.field("to_char({0}, 'HH24')", String::class.java, HOURLYACTIVITY.DATETIME).`as`("hour")
            dsl
                .select(hourField, DSL.avg(HOURLYACTIVITY.ACTIVITY_HOURS).`as`("amplitude"))
                .from(HOURLYACTIVITY)
                .groupBy(hourField)
                .orderBy(hourField)
                .fetch { (it.get(hourField) ?: "") to ((it.get("amplitude") as Number?)?.toDouble() ?: 0.0) }
        }

    fun topUsersOfWeek(): List<TopUserWeek> =
        dsl
            .fetch(
                """
                WITH start_of_week AS (
                    SELECT DATE_TRUNC('week', CURRENT_DATE)::DATE AS date
                )
                SELECT
                    SUM(EXTRACT(EPOCH FROM AGE(COALESCE(endtime, NOW()), starttime))) / 3600 AS time,
                    mu.name AS user_name,
                    mu.id AS user_id
                FROM start_of_week, tsuseractivity
                INNER JOIN tsuser tu ON tsuserid = tu.id
                INNER JOIN spybot_mergeduser mu ON tu.merged_user_id = mu.id
                WHERE starttime > start_of_week.date
                GROUP BY mu.id
                ORDER BY time DESC
                LIMIT 3
                """.trimIndent(),
            ).map {
                TopUserWeek(
                    time = it.double("time"),
                    userName = it.string("user_name"),
                    userId = it.long("user_id"),
                )
            }

    fun activeUsersStat(): ActiveUsersStat {
        val record =
            dsl.fetchOne(
                """
                WITH start_of_week AS (
                    SELECT DATE_TRUNC('week', CURRENT_DATE)::DATE AS date
                )
                SELECT
                    COUNT(DISTINCT mu.id) AS users_this_week,
                    COUNT(DISTINCT mu.id) FILTER (WHERE starttime > CURRENT_DATE) AS users_today
                FROM start_of_week, tsuseractivity
                INNER JOIN tsuser tu ON tsuserid = tu.id
                INNER JOIN spybot_mergeduser mu ON tu.merged_user_id = mu.id
                WHERE starttime > start_of_week.date
                """.trimIndent(),
            )
        return ActiveUsersStat(
            usersThisWeek = record?.get("users_this_week", Int::class.java) ?: 0,
            usersToday = record?.get("users_today", Int::class.java) ?: 0,
        )
    }

    fun weekTrend(): WeekTrendView {
        val record =
            dsl.fetchOne(
                """
                WITH
                    current_week AS (
                        SELECT
                            DATE_TRUNC('week', CURRENT_DATE) AS start_week,
                            DATE_TRUNC('hour', NOW()) - INTERVAL '1 hour' AS end_week
                    ),
                    compare_week AS (
                        SELECT
                            current_week.end_week - INTERVAL '1 WEEK' AS end_week,
                            current_week.start_week - INTERVAL '1 WEEK' AS start_week
                        FROM current_week
                    ),
                    current_week_data AS (
                        SELECT COALESCE(SUM(activity_hours), 0) AS sum
                        FROM hourlyactivity, current_week
                        WHERE hourlyactivity.datetime >= current_week.start_week
                            AND hourlyactivity.datetime <= current_week.end_week
                    ),
                    compare_week_data AS (
                        SELECT COALESCE(SUM(activity_hours), 0) AS sum
                        FROM hourlyactivity, compare_week
                        WHERE hourlyactivity.datetime >= compare_week.start_week
                            AND hourlyactivity.datetime <= compare_week.end_week
                    )
                SELECT
                    current_week_data.sum AS current_week_sum,
                    compare_week_data.sum AS compare_week_sum,
                    CASE
                        WHEN compare_week_data.sum != 0
                            THEN current_week_data.sum / compare_week_data.sum
                        ELSE 0
                    END AS fraction,
                    CASE
                        WHEN current_week_data.sum = 0 AND compare_week_data.sum = 0 THEN 0
                        WHEN compare_week_data.sum = 0 THEN 'Infinity'
                        ELSE 100 * ((current_week_data.sum / compare_week_data.sum) - 1)
                    END AS delta_percent
                FROM current_week_data, compare_week_data
                """.trimIndent(),
            ) ?: return WeekTrendView(0.0, 0.0, 0.0, "0")

        return WeekTrendView(
            currentWeekSum = record.double("current_week_sum"),
            compareWeekSum = record.double("compare_week_sum"),
            fraction = record.double("fraction"),
            deltaPercent = record.get("delta_percent")?.toString() ?: "0",
        )
    }

    fun weekComparison(): List<WeekComparisonPoint> =
        dsl
            .fetch(
                """
                WITH current_week AS (
                        SELECT
                            DATE_TRUNC('week', CURRENT_DATE) AS start,
                            DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '1 week' AS end
                    ),
                compare_week AS (
                    SELECT
                        current_week.end - INTERVAL '1 WEEK' AS end,
                        current_week.start - INTERVAL '1 WEEK' AS start
                    FROM current_week
                ),
                current_week_data AS (
                    SELECT datetime, activity_hours
                    FROM hourlyactivity, current_week
                    WHERE hourlyactivity.datetime >= current_week.start
                        AND hourlyactivity.datetime <= current_week.end
                ),
                compare_week_data AS (
                    SELECT datetime, activity_hours
                    FROM hourlyactivity, compare_week
                    WHERE hourlyactivity.datetime >= compare_week.start
                        AND hourlyactivity.datetime <= compare_week.end
                ),
                cumulate_current_week_data AS (
                    SELECT datetime, activity_hours, SUM(activity_hours) OVER(ORDER BY datetime) AS cumulative_sum
                    FROM current_week_data
                ),
                cumulate_compare_week_data AS (
                    SELECT datetime + INTERVAL '7 DAY' AS datetime, activity_hours, SUM(activity_hours) OVER(ORDER BY datetime) AS cumulative_sum
                    FROM compare_week_data
                )
                SELECT comp.datetime, cur.cumulative_sum AS hours_current, comp.cumulative_sum AS hours_compare
                FROM cumulate_compare_week_data AS comp
                LEFT JOIN cumulate_current_week_data cur ON comp.datetime = cur.datetime
                """.trimIndent(),
            ).map {
                WeekComparisonPoint(
                    datetime = it.offsetDateTime("datetime") ?: OffsetDateTime.now(ZoneOffset.UTC),
                    hoursCurrent = it.get("hours_current")?.let { value -> (value as Number).toDouble() },
                    hoursCompare = it.get("hours_compare")?.let { value -> (value as Number).toDouble() },
                )
            }

    fun channelPopularity(): List<ChannelPopularityEntry> =
        dsl
            .fetch(
                """
                WITH unfiltered AS (
                    SELECT ROUND(SUM(EXTRACT(EPOCH FROM AGE(endtime, starttime)) / 3600)) AS hours,
                        tschannel.name
                    FROM tsuseractivity
                    INNER JOIN tschannel ON tsuseractivity.cid = tschannel.id
                    WHERE starttime > NOW() - INTERVAL '1 YEAR'
                        AND tschannel.name NOT LIKE '%spacer%'
                    GROUP BY tschannel.id
                ), absolute AS (
                    SELECT * FROM unfiltered
                    WHERE hours > 5
                ), total_hours AS (
                    SELECT SUM(hours) AS hours FROM absolute
                )
                SELECT
                    absolute.name,
                    100 * absolute.hours / total_hours.hours AS percentage
                FROM absolute, total_hours
                ORDER BY percentage DESC
                """.trimIndent(),
            ).map {
                ChannelPopularityEntry(
                    name = unescapeTeamSpeak(it.string("name")),
                    percentage = it.double("percentage"),
                )
            }

    fun recentEvents(start: Int): RecentEventsPayload {
        val rows =
            dsl
                .select(
                    SPYBOT_NEWSEVENT.ID,
                    SPYBOT_NEWSEVENT.TEXT,
                    SPYBOT_NEWSEVENT.WEBSITE_LINK,
                    SPYBOT_NEWSEVENT.DATE,
                ).from(SPYBOT_NEWSEVENT)
                .orderBy(SPYBOT_NEWSEVENT.DATE.desc())
                .offset(start)
                .limit(11)
                .fetch()
        val hasMore = rows.size == 11
        val events =
            rows.take(10).map {
                val date = it.offsetDateTime("date") ?: OffsetDateTime.now(ZoneOffset.UTC)
                RecentEventView(
                    id = it.long("id"),
                    text = it.string("text"),
                    websiteLink = it.get("website_link", String::class.java),
                    date = date,
                    isRecent = date.isAfter(OffsetDateTime.now(ZoneOffset.UTC).minusWeeks(1)),
                )
            }
        return RecentEventsPayload(events = events, hasMore = hasMore, start = start + events.size)
    }

    fun hallOfFame(): List<HallOfFameEntry> =
        dsl
            .fetch(
                """
                WITH total_time AS (
                    SELECT
                        spybot_mergeduser.id AS user_id,
                        spybot_mergeduser.name AS user_name,
                        SUM(EXTRACT(EPOCH FROM AGE(COALESCE(tsuseractivity.endtime, NOW()), tsuseractivity.starttime))) AS time
                    FROM tsuseractivity, tsuser, spybot_mergeduser
                    WHERE tsuseractivity.tsuserid = tsuser.id
                    AND spybot_mergeduser.id = tsuser.merged_user_id
                    GROUP BY tsuser.merged_user_id, spybot_mergeduser.name, spybot_mergeduser.id
                    ORDER BY time DESC
                    LIMIT 25
                ),
                awards AS (
                    SELECT
                        merged_user_id,
                        COUNT(*) FILTER (WHERE points = 3) AS gold,
                        COUNT(*) FILTER (WHERE points = 2) AS silver,
                        COUNT(*) FILTER (WHERE points = 1) AS bronze
                    FROM spybot_award
                    GROUP BY merged_user_id
                )
                SELECT
                    total_time.user_id,
                    total_time.user_name AS "user",
                    total_time.time,
                    COALESCE(awards.gold, 0) AS gold,
                    COALESCE(awards.silver, 0) AS silver,
                    COALESCE(awards.bronze, 0) AS bronze
                FROM total_time
                LEFT JOIN awards ON awards.merged_user_id = total_time.user_id
                ORDER BY total_time.time DESC
                """.trimIndent(),
            ).map { row ->
                HallOfFameEntry(
                    userId = row.long("user_id"),
                    user = unescapeTeamSpeak(row.string("user")),
                    time = row.double("time"),
                    numGoldAwards = row.int("gold"),
                    numSilverAwards = row.int("silver"),
                    numBronzeAwards = row.int("bronze"),
                )
            }

    fun timeline(rangeHours: Int): Pair<TimeRangeView, List<TimelineUserSeries>> {
        val allowed = listOf(6, 12, 24)
        val selected = allowed.firstOrNull { it == rangeHours } ?: allowed.first()
        val options = allowed.map { SelectorOption("$it hours", it, it == selected) }
        val cutoff = Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).minusHours(selected.toLong()).toInstant())
        val rows =
            dsl.fetch(
                """
                select
                    a.starttime,
                    a.endtime,
                    c.name as channel_name,
                    c."order" as channel_order,
                    u.name as user_name
                from tsuseractivity a
                join tschannel c on c.id = a.cid
                join tsuser u on u.id = a.tsuserid
                where a.endtime > ? or a.endtime is null
                order by c."order"
                """.trimIndent(),
                cutoff,
            )

        val users = linkedMapOf<String, MutableList<TimelineEntry>>()
        rows.forEach { row ->
            val start = row.offsetDateTime("starttime") ?: return@forEach
            val end = row.offsetDateTime("endtime") ?: OffsetDateTime.now(ZoneOffset.UTC)
            if (end.toEpochMillis() - start.toEpochMillis() <= 10_000) {
                return@forEach
            }

            val userName = row.string("user_name")
            val channelName = unescapeTeamSpeak(row.string("channel_name"))
            val entry =
                TimelineEntry(
                    x = channelName,
                    y = listOf(start.toEpochMillis(), end.toEpochMillis()),
                )
            users.computeIfAbsent(userName) { mutableListOf() }.add(entry)
        }

        return TimeRangeView(selected, options) to
            users.map { (name, data) ->
                TimelineUserSeries(name = name, data = data)
            }
    }

    fun userPage(userId: Long): UserPageView? {
        val user =
            dsl.fetchOne(
                """
                WITH user_time AS (
                    SELECT
                        tsuseractivity.starttime AS starttime,
                        tsuseractivity.endtime AS endtime,
                        tsuseractivity.cid AS channel,
                        tsuserid AS user_id,
                        tsuser.merged_user_id AS mergeduserid
                    FROM tsuseractivity
                    JOIN tsuser ON tsuseractivity.tsuserid = tsuser.id
                    WHERE tsuser.merged_user_id = ?
                ),
                total_time AS (
                    SELECT
                        SUM(CASE WHEN channel IN (7, 13) THEN EXTRACT(EPOCH FROM AGE(COALESCE(endtime, NOW()), starttime)) ELSE 0 END) / 3600 AS afk_time,
                        SUM(CASE WHEN channel NOT IN (7, 13) THEN EXTRACT(EPOCH FROM AGE(COALESCE(endtime, NOW()), starttime)) ELSE 0 END) / 3600 AS online_time,
                        MAX(endtime) AS last_seen,
                        MIN(starttime) AS first_seen
                    FROM user_time
                    GROUP BY mergeduserid
                ),
                awards AS (
                    SELECT
                        string_agg(DISTINCT tu.name, ',') AS names,
                        sm.name AS merged_username,
                        bool_or(tu.iscurrentlyonline) AS online,
                        SUM(CASE WHEN points = 1 THEN 1 ELSE 0 END) AS bronze,
                        SUM(CASE WHEN points = 2 THEN 1 ELSE 0 END) AS silver,
                        SUM(CASE WHEN points = 3 THEN 1 ELSE 0 END) AS gold
                    FROM spybot_award
                    RIGHT JOIN tsuser tu ON spybot_award.tsuser_id = tu.id
                    JOIN spybot_mergeduser sm ON tu.merged_user_id = sm.id
                    WHERE tu.merged_user_id = ?
                    GROUP BY tu.merged_user_id, sm.name
                )
                SELECT *
                FROM total_time, awards
                """.trimIndent(),
                userId,
                userId,
            ) ?: return null

        val streak =
            dsl
                .fetchOne(
                    """
                    WITH dates AS (
                        SELECT DISTINCT CAST(tsuseractivity.starttime AS DATE) AS day
                        FROM tsuseractivity
                        INNER JOIN tsuser ON tsuserid = tsuser.id
                        WHERE merged_user_id = ?
                    ),
                    cte AS (
                        SELECT
                            day,
                            COALESCE(DATE(day) > DATE(LAG(day, 1) OVER (ORDER BY day)) + INTERVAL '1 DAY', true) AS startsstreak
                        FROM dates
                    ),
                    result AS (
                        SELECT
                            dates.day AS start_day,
                            SUM(startsstreak::int) AS streakgroup,
                            ROW_NUMBER() OVER (PARTITION BY SUM(startsstreak::int) ORDER BY dates.day) AS runningstreaklength,
                            COUNT(*) OVER (PARTITION BY SUM(startsstreak::int)) AS totalstreaklength
                        FROM dates
                        JOIN cte ON dates.day >= cte.day AND cte.startsstreak = true
                        GROUP BY dates.day
                        ORDER BY dates.day
                    )
                    SELECT start_day,
                           start_day + make_interval(days => totalstreaklength::int - 1) AS end_day,
                           totalstreaklength AS length
                    FROM result
                    WHERE runningstreaklength = 1
                    ORDER BY totalstreaklength DESC, start_day DESC
                    LIMIT 1
                    """.trimIndent(),
                    userId,
                )?.let {
                    StreakView(
                        startDay = it.localDate("start_day") ?: LocalDate.now(),
                        endDay = it.localDate("end_day") ?: LocalDate.now(),
                        length = it.int("length"),
                    )
                }

        val months =
            dsl
                .fetch(
                    """
                    WITH data AS (
                        SELECT
                            DATE_PART('year', starttime) AS year,
                            DATE_PART('month', starttime) AS month,
                            SUM(EXTRACT(EPOCH FROM AGE(endtime, starttime))) / 3600 AS time_hours
                        FROM tsuseractivity
                        INNER JOIN tschannel channel ON tsuseractivity.cid = channel.id
                        INNER JOIN tsuser ON tsuseractivity.tsuserid = tsuser.id
                        WHERE starttime > MAKE_DATE(2016, 1, 1)
                            AND endtime IS NOT NULL
                            AND channel.name NOT IN ('bei\sBedarf\sanstupsen', 'AFK')
                            AND tsuser.merged_user_id = ?
                        GROUP BY year, month
                        ORDER BY year, month
                    ),
                    months AS (
                        WITH RECURSIVE nrows(date) AS (
                            SELECT MAKE_DATE(2016, 1, 1)::timestamptz
                            UNION ALL
                            SELECT date + INTERVAL '1 MONTH' FROM nrows WHERE date <= CURRENT_DATE - INTERVAL '1 MONTH'
                        )
                        SELECT date FROM nrows
                    )
                    SELECT DATE_PART('month', months.date) AS month,
                           DATE_PART('year', months.date) AS year,
                           COALESCE(data.time_hours, 0) AS activity
                    FROM months
                    LEFT JOIN data
                        ON DATE_PART('year', months.date) = data.year
                       AND DATE_PART('month', months.date) = data.month
                    ORDER BY year, month
                    """.trimIndent(),
                    userId,
                ).map {
                    MonthActivityPoint(
                        month = it.int("month"),
                        year = it.int("year"),
                        activity = it.double("activity"),
                    )
                }

        val headline =
            UserHeadline(
                names = user.string("names").split(',').filter { it.isNotBlank() },
                mergedUsername = user.string("merged_username"),
                online = user.boolean("online"),
                bronze = user.int("bronze"),
                silver = user.int("silver"),
                gold = user.int("gold"),
                afkTime = user.double("afk_time"),
                onlineTime = user.double("online_time"),
                lastSeen = user.offsetDateTime("last_seen"),
                firstSeen = user.offsetDateTime("first_seen"),
            )

        return UserPageView(
            userId = userId,
            headline = headline,
            streak = streak,
            months = months,
            totalTime = (headline.afkTime + headline.onlineTime).roundToInt(),
            gameId = 0,
            gameName = "",
        )
    }

    fun recordHourlyActivity() {
        dsl.execute(
            """
            INSERT INTO hourlyactivity(datetime, activity_hours)
            WITH startofhour AS (
                SELECT DATE_TRUNC('hour', NOW()) AS stamp
            ),
            activityhours AS (
                SELECT CAST(COALESCE(SUM(
                    EXTRACT(EPOCH FROM AGE(
                        COALESCE(endtime, NOW()),
                        CASE WHEN startofhour.stamp > starttime THEN startofhour.stamp ELSE starttime END
                    ))
                ), 0) AS FLOAT) / 3600 AS activity_hours
                FROM tsuseractivity, startofhour
                WHERE endtime IS NULL OR endtime > startofhour.stamp
            )
            SELECT startofhour.stamp, activityhours.activity_hours
            FROM startofhour, activityhours
            """.trimIndent(),
        )
    }

    fun weeklyAwardCandidates(): List<TopUserWeek> = topUsersOfWeek()

    // Projections that several queries share. Column order is the DTO's constructor order:
    // Records.mapping(::Dto) checks that at compile time, so a mismatch cannot reach runtime.
    private fun selectMergedUser() =
        dsl.select(
            SPYBOT_MERGEDUSER.ID.notNull(),
            SPYBOT_MERGEDUSER.NAME.notNull(),
            SPYBOT_MERGEDUSER.OBSOLETE.notNull(),
            SPYBOT_MERGEDUSER.IS_SUPERUSER.notNull(),
            SPYBOT_MERGEDUSER.LAST_LOGIN,
        )

    private val toMergedUser = mapping(::MergedUserView)

    private fun selectNewsEvent() =
        dsl.select(
            SPYBOT_NEWSEVENT.ID.notNull(),
            SPYBOT_NEWSEVENT.TEXT.notNull(),
            SPYBOT_NEWSEVENT.WEBSITE_LINK,
            SPYBOT_NEWSEVENT.DATE.notNull(),
        )

    private val toNewsEvent = mapping(::AdminNewsEventRow)

    private fun Record.toMergedUser(): MergedUserView =
        MergedUserView(
            id = long("id"),
            name = string("name"),
            obsolete = boolean("obsolete"),
            isSuperuser = boolean("is_superuser"),
            lastLogin = offsetDateTime("last_login"),
        )

    private fun Record.toTeamSpeakIdentity(): TeamSpeakIdentity =
        TeamSpeakIdentity(
            tsUserId = int("ts_user_id"),
            mergedUserId = long("merged_user_id"),
            tsUserName = string("ts_user_name"),
            mergedUserName = string("merged_user_name"),
        )

    private fun Record.string(field: String): String = get(field, String::class.java) ?: ""

    private fun unescapeTeamSpeak(value: String): String {
        val result = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val char = value[i]
            val next = value.getOrNull(i + 1)
            val replacement =
                if (char == '\\' && next != null) {
                    when (next) {
                        '\\' -> '\\'
                        '/' -> '/'
                        's' -> ' '
                        'p' -> '|'
                        ';' -> ';'
                        'a' -> '\u0007'
                        'b' -> '\b'
                        'f' -> '\u000C'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'v' -> '\u000B'
                        else -> null
                    }
                } else {
                    null
                }
            if (replacement != null) {
                result.append(replacement)
                i += 2
            } else {
                result.append(char)
                i += 1
            }
        }
        return result.toString()
    }

    private fun Record.long(field: String): Long = (get(field) as Number?)?.toLong() ?: 0L

    private fun Record.int(field: String): Int = (get(field) as Number?)?.toInt() ?: 0

    private fun Record.double(field: String): Double = (get(field) as Number?)?.toDouble() ?: 0.0

    private fun Record.boolean(field: String): Boolean = get(field, Boolean::class.java) ?: false

    private fun Record.offsetDateTime(field: String): OffsetDateTime? {
        val value = get(field)
        return when (value) {
            null -> null
            is OffsetDateTime -> value
            is Timestamp -> value.toInstant().atOffset(ZoneOffset.UTC)
            is java.time.LocalDateTime -> value.atOffset(ZoneOffset.UTC)
            else -> null
        }
    }

    private fun Record.localDate(field: String): LocalDate? =
        when (val value = get(field)) {
            null -> null
            is LocalDate -> value
            is java.sql.Date -> value.toLocalDate()
            is Timestamp -> value.toInstant().atOffset(ZoneOffset.UTC).toLocalDate()
            else -> null
        }

    private fun parseJsonArray(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.length < 2) {
            return emptyList()
        }
        return trimmed
            .removePrefix("[")
            .removeSuffix("]")
            .split(',')
            .map { it.trim().removePrefix("\"").removeSuffix("\"") }
            .filter { it.isNotBlank() }
    }

    private fun OffsetDateTime.toEpochMillis(): Long = toInstant().toEpochMilli()
}
