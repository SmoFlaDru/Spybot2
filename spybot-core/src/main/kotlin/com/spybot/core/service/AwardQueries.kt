package com.spybot.core.service

import com.spybot.jooq.tables.references.SPYBOT_AWARD
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Service

/** Weekly award medals. */
@Service
class AwardQueries(
    private val dsl: DSLContext,
) {
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
}
