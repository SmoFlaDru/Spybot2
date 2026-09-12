package com.spybot.core.service

import com.spybot.core.jooq.notNull
import com.spybot.core.model.QueuedClientMessageView
import com.spybot.jooq.tables.references.SPYBOT_QUEUEDCLIENTMESSAGE
import org.jooq.DSLContext
import org.jooq.Records.mapping
import org.jooq.impl.DSL
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Messages queued for a user until the recorder next sees them on TeamSpeak. */
@Service
class QueuedMessageQueries(
    private val dsl: DSLContext,
) {
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
}
