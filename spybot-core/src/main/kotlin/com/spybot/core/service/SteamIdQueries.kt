package com.spybot.core.service

import com.spybot.core.jooq.notNull
import com.spybot.core.model.SteamIdView
import com.spybot.jooq.tables.references.SPYBOT_STEAMID
import org.jooq.Records.mapping
import org.jooq.DSLContext
import org.springframework.stereotype.Service

/** Steam accounts linked to a merged user. */
@Service
class SteamIdQueries(
    private val dsl: DSLContext,
) {
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
}
