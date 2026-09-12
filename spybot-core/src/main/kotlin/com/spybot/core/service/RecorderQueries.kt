package com.spybot.core.service

import com.spybot.core.jooq.int
import com.spybot.core.jooq.string
import com.spybot.core.model.OpenSessionView
import com.spybot.core.model.TeamSpeakChannelSnapshot
import com.spybot.core.model.TeamSpeakIdentity
import com.spybot.jooq.tables.references.SPYBOT_MERGEDUSER
import com.spybot.jooq.tables.references.TSCHANNEL
import com.spybot.jooq.tables.references.TSID
import com.spybot.jooq.tables.references.TSUSER
import com.spybot.jooq.tables.references.TSUSERACTIVITY
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.transaction.annotation.Transactional
import com.spybot.core.jooq.long
import org.jooq.DSLContext
import org.springframework.stereotype.Service

/** What the TeamSpeak recorder writes: channels, TeamSpeak identities, and the activity sessions the live view and statistics are built from. */
@Service
class RecorderQueries(
    private val dsl: DSLContext,
) {
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

    private fun Record.toTeamSpeakIdentity(): TeamSpeakIdentity =
        TeamSpeakIdentity(
            tsUserId = int("ts_user_id"),
            mergedUserId = long("merged_user_id"),
            tsUserName = string("ts_user_name"),
            mergedUserName = string("merged_user_name"),
        )
}
