package com.spybot.core.service

import com.spybot.core.jooq.notNull
import com.spybot.core.model.AdminMergedUserRow
import com.spybot.core.model.AdminTsUserRow
import com.spybot.core.model.MergedUserView
import com.spybot.jooq.tables.references.SPYBOT_AWARD
import com.spybot.jooq.tables.references.SPYBOT_LOGINLINK
import com.spybot.jooq.tables.references.SPYBOT_MERGEDUSER
import com.spybot.jooq.tables.references.SPYBOT_QUEUEDCLIENTMESSAGE
import com.spybot.jooq.tables.references.SPYBOT_STEAMID
import com.spybot.jooq.tables.references.SPYBOT_USERPASSKEY
import com.spybot.jooq.tables.references.SPYBOT_WEBAUTHN_USER_HANDLE
import com.spybot.jooq.tables.references.TSUSER
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Records.mapping
import org.jooq.impl.DSL
import org.springframework.stereotype.Service

/** Admin-only views and mutations over merged users and TeamSpeak users, including the merge reassignments. */
@Service
class AdminQueries(
    private val dsl: DSLContext,
) {
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

    fun adminFindMergedUsersByIds(ids: Collection<Long>): List<MergedUserView> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        return dsl.selectMergedUser()
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
}
