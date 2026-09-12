package com.spybot.core.service

import com.spybot.core.jooq.notNull
import com.spybot.core.model.LikedNameView
import com.spybot.core.model.Liker
import com.spybot.core.model.NameLikeStatus
import com.spybot.jooq.tables.references.SPYBOT_LIKEDNAME
import com.spybot.jooq.tables.references.SPYBOT_NAMELIKE
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Records.mapping
import org.jooq.impl.DSL
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Per-person likes on names proposed by the Steam name generator. The database holds one row per
 * (name, liker) with unique constraints, so a person can like a name once and take it back; the
 * displayed count is simply the number of rows.
 */
@Service
class LikedNameService(
    private val dsl: DSLContext,
) {
    fun like(
        displayName: String,
        realName: String,
        slang: String,
        liker: Liker,
    ): NameLikeStatus =
        dsl.transactionResult { config ->
            val tx = config.dsl()
            // DO UPDATE (not DO NOTHING) so RETURNING yields the id on the conflict path too.
            val nameId =
                tx
                    .insertInto(SPYBOT_LIKEDNAME)
                    .set(SPYBOT_LIKEDNAME.DISPLAY_NAME, displayName)
                    .set(SPYBOT_LIKEDNAME.REAL_NAME, realName)
                    .set(SPYBOT_LIKEDNAME.SLANG, slang)
                    .onConflict(SPYBOT_LIKEDNAME.DISPLAY_NAME)
                    .doUpdate()
                    .set(SPYBOT_LIKEDNAME.REAL_NAME, realName)
                    .returning(SPYBOT_LIKEDNAME.ID)
                    .fetchSingle()
                    .id!!
            tx
                .insertInto(SPYBOT_NAMELIKE)
                .set(SPYBOT_NAMELIKE.NAME_ID, nameId)
                .set(SPYBOT_NAMELIKE.MERGED_USER_ID, (liker as? Liker.User)?.mergedUserId)
                .set(SPYBOT_NAMELIKE.VISITOR_ID, (liker as? Liker.Visitor)?.visitorId)
                .onConflictDoNothing()
                .execute()
            status(tx, nameId, liker)
        }

    fun unlike(
        displayName: String,
        liker: Liker,
    ): NameLikeStatus =
        dsl.transactionResult { config ->
            val tx = config.dsl()
            val nameId =
                tx
                    .select(SPYBOT_LIKEDNAME.ID)
                    .from(SPYBOT_LIKEDNAME)
                    .where(SPYBOT_LIKEDNAME.DISPLAY_NAME.eq(displayName))
                    .fetchOne(SPYBOT_LIKEDNAME.ID)
                    ?: return@transactionResult NameLikeStatus(likes = 0, likedByMe = false)
            tx
                .deleteFrom(SPYBOT_NAMELIKE)
                .where(SPYBOT_NAMELIKE.NAME_ID.eq(nameId))
                .and(isLiker(liker))
                .execute()
            status(tx, nameId, liker)
        }

    fun status(
        displayName: String,
        liker: Liker,
    ): NameLikeStatus {
        val nameId =
            dsl
                .select(SPYBOT_LIKEDNAME.ID)
                .from(SPYBOT_LIKEDNAME)
                .where(SPYBOT_LIKEDNAME.DISPLAY_NAME.eq(displayName))
                .fetchOne(SPYBOT_LIKEDNAME.ID)
                ?: return NameLikeStatus(likes = 0, likedByMe = false)
        return status(dsl, nameId, liker)
    }

    /** Most-liked names first; ties go to the most recently liked. Names nobody likes any more are left out. */
    fun top(
        limit: Int,
        liker: Liker,
    ): List<LikedNameView> {
        val likes = DSL.count(SPYBOT_NAMELIKE.ID)
        // isLiker() compares a nullable column, so it is NULL rather than false on rows liked by
        // the other kind of liker - and bool_or over only NULLs is NULL. Coalesce in SQL, not here.
        val likedByMe = DSL.coalesce(DSL.boolOr(isLiker(liker)), DSL.inline(false))
        val lastLiked = DSL.max(SPYBOT_NAMELIKE.CREATED_AT)
        return dsl
            .select(
                SPYBOT_LIKEDNAME.DISPLAY_NAME.notNull(),
                SPYBOT_LIKEDNAME.REAL_NAME.notNull(),
                SPYBOT_LIKEDNAME.SLANG.notNull(),
                likes.notNull(),
                likedByMe.notNull(),
            ).from(SPYBOT_LIKEDNAME)
            .join(SPYBOT_NAMELIKE)
            .on(SPYBOT_NAMELIKE.NAME_ID.eq(SPYBOT_LIKEDNAME.ID))
            .groupBy(SPYBOT_LIKEDNAME.ID, SPYBOT_LIKEDNAME.DISPLAY_NAME, SPYBOT_LIKEDNAME.REAL_NAME, SPYBOT_LIKEDNAME.SLANG)
            .orderBy(likes.desc(), lastLiked.desc())
            .limit(limit)
            .fetch(mapping(::LikedNameView))
    }

    /**
     * Moves the likes of merged-away users to the user they were merged into, keeping at most one
     * like per name (the target's own, or otherwise the earliest). Returns how many rows moved.
     */
    @Transactional
    fun reassignLikes(
        sourceIds: Collection<Long>,
        targetId: Long,
    ): Int {
        if (sourceIds.isEmpty()) {
            return 0
        }
        val other = SPYBOT_NAMELIKE.`as`("other")
        dsl
            .deleteFrom(SPYBOT_NAMELIKE)
            .where(SPYBOT_NAMELIKE.MERGED_USER_ID.`in`(sourceIds))
            .andExists(
                dsl
                    .selectOne()
                    .from(other)
                    .where(other.NAME_ID.eq(SPYBOT_NAMELIKE.NAME_ID))
                    .and(
                        other.MERGED_USER_ID
                            .eq(targetId)
                            .or(other.MERGED_USER_ID.`in`(sourceIds).and(other.ID.lt(SPYBOT_NAMELIKE.ID))),
                    ),
            ).execute()
        return dsl
            .update(SPYBOT_NAMELIKE)
            .set(SPYBOT_NAMELIKE.MERGED_USER_ID, targetId)
            .where(SPYBOT_NAMELIKE.MERGED_USER_ID.`in`(sourceIds))
            .execute()
    }

    private fun status(
        ctx: DSLContext,
        nameId: Long,
        liker: Liker,
    ): NameLikeStatus {
        val likes = DSL.count(SPYBOT_NAMELIKE.ID)
        val likedByMe = DSL.boolOr(isLiker(liker))
        val row =
            ctx
                .select(likes, likedByMe)
                .from(SPYBOT_NAMELIKE)
                .where(SPYBOT_NAMELIKE.NAME_ID.eq(nameId))
                .fetchSingle()
        return NameLikeStatus(likes = row.get(likes) ?: 0, likedByMe = row.get(likedByMe) ?: false)
    }

    private fun isLiker(liker: Liker): Condition =
        when (liker) {
            is Liker.User -> SPYBOT_NAMELIKE.MERGED_USER_ID.eq(liker.mergedUserId)
            is Liker.Visitor -> SPYBOT_NAMELIKE.VISITOR_ID.eq(liker.visitorId)
        }
}
