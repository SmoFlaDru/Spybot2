package com.spybot.core.service

import com.spybot.core.model.TeamSpeakChannelSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChannelOrderingTest {
    private fun channel(
        id: Int,
        order: Int,
        parentId: Int = 0,
    ) = TeamSpeakChannelSnapshot(id = id, name = "channel-$id", order = order, parentId = parentId)

    @Test
    fun `orders flat siblings by following the previous-sibling linked list, not raw ids`() {
        // TS3's channel_order is the id of the previous sibling (0 = first), so the ids
        // themselves carry no positional meaning - only walking the list does.
        val a = channel(id = 10, order = 0)
        val b = channel(id = 3, order = 10)
        val c = channel(id = 44, order = 3)

        val result = resolveChannelDisplayOrder(listOf(c, a, b))

        assertEquals(listOf(10, 3, 44), result.map { it.id })
        assertEquals(listOf(0, 1, 2), result.map { it.order })
    }

    @Test
    fun `descends into a channel's children before moving to its next sibling`() {
        val root1 = channel(id = 1, order = 0, parentId = 0)
        val root2 = channel(id = 2, order = 1, parentId = 0)
        val child = channel(id = 20, order = 0, parentId = 1)

        val result = resolveChannelDisplayOrder(listOf(root2, child, root1))

        assertEquals(listOf(1, 20, 2), result.map { it.id })
    }

    @Test
    fun `appends channels with a broken or cyclic previous-sibling pointer instead of dropping them`() {
        // Neither channel points at 0, so the normal walk never finds a starting point for this
        // sibling group. They must still show up somewhere rather than vanish.
        val a = channel(id = 5, order = 9, parentId = 0)
        val b = channel(id = 6, order = 5, parentId = 0)

        val result = resolveChannelDisplayOrder(listOf(a, b))

        assertEquals(setOf(5, 6), result.map { it.id }.toSet())
        assertEquals(listOf(0, 1), result.map { it.order })
    }

    @Test
    fun `returns an empty list for no channels`() {
        assertEquals(emptyList<TeamSpeakChannelSnapshot>(), resolveChannelDisplayOrder(emptyList()))
    }
}
