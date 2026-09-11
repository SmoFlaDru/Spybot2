package com.spybot.core.service

import com.spybot.core.model.LikedNameView
import com.spybot.jooq.tables.references.SPYBOT_LIKEDNAME
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Service

/** Likes for names proposed by the Steam name generator. */
@Service
class LikedNameService(
    private val dsl: DSLContext,
) {
    /** Records one like: inserts the name on first sight, otherwise bumps its counter. */
    fun like(
        displayName: String,
        realName: String,
        slang: String,
    ): LikedNameView {
        val record =
            dsl
                .insertInto(SPYBOT_LIKEDNAME)
                .set(SPYBOT_LIKEDNAME.DISPLAY_NAME, displayName)
                .set(SPYBOT_LIKEDNAME.REAL_NAME, realName)
                .set(SPYBOT_LIKEDNAME.SLANG, slang)
                .set(SPYBOT_LIKEDNAME.LIKES, 1)
                .onConflict(SPYBOT_LIKEDNAME.DISPLAY_NAME)
                .doUpdate()
                .set(SPYBOT_LIKEDNAME.LIKES, SPYBOT_LIKEDNAME.LIKES.plus(1))
                .set(SPYBOT_LIKEDNAME.LAST_LIKED_AT, DSL.currentOffsetDateTime())
                .returning()
                .fetchSingle()
        return LikedNameView(
            displayName = record.displayName ?: displayName,
            realName = record.realName ?: realName,
            slang = record.slang ?: slang,
            likes = record.likes ?: 0,
        )
    }

    /** Most-liked names first; ties go to the most recently liked. */
    fun top(limit: Int): List<LikedNameView> =
        dsl
            .select(SPYBOT_LIKEDNAME.DISPLAY_NAME, SPYBOT_LIKEDNAME.REAL_NAME, SPYBOT_LIKEDNAME.SLANG, SPYBOT_LIKEDNAME.LIKES)
            .from(SPYBOT_LIKEDNAME)
            .orderBy(SPYBOT_LIKEDNAME.LIKES.desc(), SPYBOT_LIKEDNAME.LAST_LIKED_AT.desc())
            .limit(limit)
            .fetch {
                LikedNameView(
                    displayName = it.get(SPYBOT_LIKEDNAME.DISPLAY_NAME)!!,
                    realName = it.get(SPYBOT_LIKEDNAME.REAL_NAME)!!,
                    slang = it.get(SPYBOT_LIKEDNAME.SLANG)!!,
                    likes = it.get(SPYBOT_LIKEDNAME.LIKES) ?: 0,
                )
            }
}
