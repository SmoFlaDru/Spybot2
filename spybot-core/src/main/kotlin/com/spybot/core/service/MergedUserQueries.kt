package com.spybot.core.service

import com.spybot.core.model.MergedUserView
import com.spybot.jooq.tables.references.SPYBOT_LOGINLINK
import com.spybot.jooq.tables.references.SPYBOT_MERGEDUSER
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Service

/** Merged users (the site account a TeamSpeak identity belongs to): lookup, last-seen, login links. */
@Service
class MergedUserQueries(
    private val dsl: DSLContext,
) {
    fun findMergedUserById(id: Long): MergedUserView? =
        dsl
            .selectMergedUser()
            .from(SPYBOT_MERGEDUSER)
            .where(SPYBOT_MERGEDUSER.ID.eq(id))
            .fetchOne(toMergedUser)

    fun findMergedUserByLoginCode(code: String): MergedUserView? =
        dsl
            .selectMergedUser()
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

    fun mergedUserName(userId: Long): String? =
        dsl
            .select(SPYBOT_MERGEDUSER.NAME)
            .from(SPYBOT_MERGEDUSER)
            .where(SPYBOT_MERGEDUSER.ID.eq(userId))
            .fetchOne(SPYBOT_MERGEDUSER.NAME)

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
}
