package com.spybot.web.service

import com.spybot.core.model.AdminMergedUserRow
import com.spybot.core.model.AdminNewsEventRow
import com.spybot.core.model.AdminTsUserRow
import com.spybot.core.model.MergeUsersResult
import com.spybot.core.service.SpybotQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminService(
    private val queryService: SpybotQueryService,
) {
    fun mergedUsers(search: String?): List<AdminMergedUserRow> = queryService.adminMergedUsers(search)

    fun tsUsers(search: String?): List<AdminTsUserRow> = queryService.adminTsUsers(search)

    fun newsEvents(search: String?): List<AdminNewsEventRow> = queryService.adminNewsEvents(search)

    fun newsEventById(id: Long): AdminNewsEventRow? = queryService.adminNewsEventById(id)

    fun createNewsEvent(
        text: String,
        websiteLink: String?,
    ): Long = queryService.adminCreateNewsEvent(text, websiteLink?.takeIf { it.isNotBlank() })

    fun updateNewsEvent(
        id: Long,
        text: String,
        websiteLink: String?,
    ): Boolean = queryService.adminUpdateNewsEvent(id, text, websiteLink?.takeIf { it.isNotBlank() })

    fun deleteNewsEvent(id: Long): Boolean = queryService.adminDeleteNewsEvent(id)

    data class AdminOverview(
        val mergedUsersCount: Int,
        val tsUsersCount: Int,
        val newsEventsCount: Int,
    )

    fun overview(): AdminOverview =
        AdminOverview(
            mergedUsersCount = queryService.adminMergedUsers(null).size,
            tsUsersCount = queryService.adminTsUsers(null).size,
            newsEventsCount = queryService.adminNewsEvents(null).size,
        )

    @Transactional
    fun mergeUsers(
        targetId: Long,
        sourceIds: List<Long>,
    ): MergeUsersResult {
        val deduplicatedSources = sourceIds.distinct().filter { it != targetId }
        require(deduplicatedSources.isNotEmpty()) { "At least one source user is required" }

        val loaded = queryService.adminFindMergedUsersByIds(deduplicatedSources + targetId)
        val loadedById = loaded.associateBy { it.id }
        val target = loadedById[targetId] ?: error("Target merged user not found")
        require(!target.obsolete) { "Target user is obsolete and cannot be used as merge target" }

        val missingSources = deduplicatedSources.filterNot { loadedById.containsKey(it) }
        require(missingSources.isEmpty()) { "Unknown source user id(s): $missingSources" }

        val obsoleteSources = deduplicatedSources.filter { loadedById[it]?.obsolete == true }
        require(obsoleteSources.isEmpty()) { "Source user(s) are already obsolete: $obsoleteSources" }

        val sourceUsers = deduplicatedSources.mapNotNull { loadedById[it] }
        val shouldSetTargetAdmin = target.isSuperuser || sourceUsers.any { it.isSuperuser }
        if (shouldSetTargetAdmin != target.isSuperuser) {
            queryService.adminSetMergedUserSuperuser(targetId, shouldSetTargetAdmin)
        }

        val movedTsUsers = queryService.adminReassignTsUsers(deduplicatedSources, targetId)
        val movedSteamIds = queryService.adminReassignSteamIds(deduplicatedSources, targetId)
        val movedAwards = queryService.adminReassignAwards(deduplicatedSources, targetId)
        val movedQueuedMessages = queryService.adminReassignQueuedMessages(deduplicatedSources, targetId)
        val movedLoginLinks = queryService.adminReassignLoginLinks(deduplicatedSources, targetId)
        val movedPasskeys = queryService.adminReassignPasskeys(deduplicatedSources, targetId)
        val obsoletedMergedUsers = queryService.adminSetMergedUsersObsolete(deduplicatedSources, true)

        return MergeUsersResult(
            targetId = targetId,
            sourceIds = deduplicatedSources,
            movedTsUsers = movedTsUsers,
            movedSteamIds = movedSteamIds,
            movedAwards = movedAwards,
            movedQueuedMessages = movedQueuedMessages,
            movedLoginLinks = movedLoginLinks,
            movedPasskeys = movedPasskeys,
            obsoletedMergedUsers = obsoletedMergedUsers,
        )
    }
}
