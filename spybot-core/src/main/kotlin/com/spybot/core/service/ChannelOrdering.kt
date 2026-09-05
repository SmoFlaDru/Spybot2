package com.spybot.core.service

import com.spybot.core.model.TeamSpeakChannelSnapshot

/**
 * TS3's channel_order field is the id of the previous sibling channel at the same depth (0 for
 * the first channel under a parent) - a linked-list pointer, not a flat display rank. The real
 * TeamSpeak client renders channels by walking that list per parent, recursing into each
 * channel's own children before moving on to its next sibling (so a channel's descendants appear
 * directly beneath it). This reproduces that walk and returns the channels in the resulting
 * order, with `order` replaced by a dense rank (0, 1, 2, ...) reflecting that position - the raw
 * channel_order/pid values only matter as input to this walk, not as a display rank themselves.
 */
fun resolveChannelDisplayOrder(channels: List<TeamSpeakChannelSnapshot>): List<TeamSpeakChannelSnapshot> {
    val byParent = channels.groupBy { it.parentId }
    val visited = mutableSetOf<Int>()
    val ordered = mutableListOf<TeamSpeakChannelSnapshot>()

    fun walkSiblings(parentId: Int) {
        val siblings = byParent[parentId].orEmpty()
        val byPreviousSiblingId = siblings.associateBy { it.order }

        var current = byPreviousSiblingId[0]
        while (current != null && visited.add(current.id)) {
            ordered += current
            walkSiblings(current.id)
            current = byPreviousSiblingId[current.id]
        }

        // A channel whose previous-sibling pointer is broken or forms a cycle (e.g. a bad sync
        // snapshot) would otherwise vanish silently. Append any such leftovers in a stable order
        // so nothing gets dropped from the list, even if their exact position ends up off.
        siblings.filter { it.id !in visited }.sortedBy { it.id }.forEach {
            visited += it.id
            ordered += it
            walkSiblings(it.id)
        }
    }

    walkSiblings(0)
    return ordered.mapIndexed { index, channel -> channel.copy(order = index) }
}
