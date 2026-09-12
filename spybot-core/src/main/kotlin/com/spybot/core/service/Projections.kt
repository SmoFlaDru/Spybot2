package com.spybot.core.service

import com.spybot.core.jooq.notNull
import com.spybot.core.model.MergedUserView
import com.spybot.jooq.tables.references.SPYBOT_MERGEDUSER
import org.jooq.DSLContext
import org.jooq.Records.mapping

// Projections shared by more than one query service. Column order is the DTO's constructor
// order: Records.mapping(::Dto) checks that at compile time, so a mismatch cannot reach runtime.
// Projections that several queries share. Column order is the DTO's constructor order:
// Records.mapping(::Dto) checks that at compile time, so a mismatch cannot reach runtime.
internal fun DSLContext.selectMergedUser() =
    select(
        SPYBOT_MERGEDUSER.ID.notNull(),
        SPYBOT_MERGEDUSER.NAME.notNull(),
        SPYBOT_MERGEDUSER.OBSOLETE.notNull(),
        SPYBOT_MERGEDUSER.IS_SUPERUSER.notNull(),
        SPYBOT_MERGEDUSER.LAST_LOGIN,
    )

internal val toMergedUser = mapping(::MergedUserView)
