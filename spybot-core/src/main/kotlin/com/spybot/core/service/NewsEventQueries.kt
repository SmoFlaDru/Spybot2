package com.spybot.core.service

import com.spybot.core.jooq.notNull
import com.spybot.core.model.AdminNewsEventRow
import com.spybot.jooq.tables.references.SPYBOT_NEWSEVENT
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Records.mapping
import org.jooq.impl.DSL
import org.springframework.stereotype.Service

/** News events shown on the home page: admin CRUD plus the create used by the weekly awards job. */
@Service
class NewsEventQueries(
    private val dsl: DSLContext,
) {
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

    private fun selectNewsEvent() =
        dsl.select(
            SPYBOT_NEWSEVENT.ID.notNull(),
            SPYBOT_NEWSEVENT.TEXT.notNull(),
            SPYBOT_NEWSEVENT.WEBSITE_LINK,
            SPYBOT_NEWSEVENT.DATE.notNull(),
        )

    private val toNewsEvent = mapping(::AdminNewsEventRow)
}
