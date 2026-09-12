package com.spybot.web.service

import com.spybot.core.model.AdminMergedUserRow
import com.spybot.core.model.AdminNewsEventRow
import com.spybot.core.model.AdminTsUserRow
import com.spybot.core.model.MergeUsersResult
import com.spybot.core.service.AdminQueries
import com.spybot.core.service.LikedNameService
import com.spybot.core.service.NewsEventQueries
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminService(
    private val adminQueries: AdminQueries,
    private val newsEventQueries: NewsEventQueries,
    private val likedNameService: LikedNameService,
) {
    fun mergedUsers(search: String?): List<AdminMergedUserRow> = adminQueries.adminMergedUsers(search)

    fun tsUsers(search: String?): List<AdminTsUserRow> = adminQueries.adminTsUsers(search)

    fun newsEvents(search: String?): List<AdminNewsEventRow> = newsEventQueries.adminNewsEvents(search)

    fun newsEventById(id: Long): AdminNewsEventRow? = newsEventQueries.adminNewsEventById(id)

    fun createNewsEvent(
        text: String,
        websiteLink: String?,
    ): Long = newsEventQueries.adminCreateNewsEvent(text, websiteLink?.takeIf { it.isNotBlank() })

    fun updateNewsEvent(
        id: Long,
        text: String,
        websiteLink: String?,
    ): Boolean = newsEventQueries.adminUpdateNewsEvent(id, text, websiteLink?.takeIf { it.isNotBlank() })

    fun deleteNewsEvent(id: Long): Boolean = newsEventQueries.adminDeleteNewsEvent(id)

    data class AdminOverview(
        val mergedUsersCount: Int,
        val tsUsersCount: Int,
        val newsEventsCount: Int,
    )

    fun overview(): AdminOverview =
        AdminOverview(
            mergedUsersCount = adminQueries.adminMergedUsers(null).size,
            tsUsersCount = adminQueries.adminTsUsers(null).size,
            newsEventsCount = newsEventQueries.adminNewsEvents(null).size,
        )

    @Transactional
    fun mergeUsers(
        targetId: Long,
        sourceIds: List<Long>,
    ): MergeUsersResult {
        val deduplicatedSources = sourceIds.distinct().filter { it != targetId }
        require(deduplicatedSources.isNotEmpty()) { "At least one source user is required" }

        val loaded = adminQueries.adminFindMergedUsersByIds(deduplicatedSources + targetId)
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
            adminQueries.adminSetMergedUserSuperuser(targetId, shouldSetTargetAdmin)
        }

        val movedTsUsers = adminQueries.adminReassignTsUsers(deduplicatedSources, targetId)
        val movedSteamIds = adminQueries.adminReassignSteamIds(deduplicatedSources, targetId)
        val movedAwards = adminQueries.adminReassignAwards(deduplicatedSources, targetId)
        val movedQueuedMessages = adminQueries.adminReassignQueuedMessages(deduplicatedSources, targetId)
        val movedLoginLinks = adminQueries.adminReassignLoginLinks(deduplicatedSources, targetId)
        val movedPasskeys = adminQueries.adminReassignPasskeys(deduplicatedSources, targetId)
        // The WebAuthn user handles inside those passkeys can't change, so the handles follow
        // the passkeys to the target: a login with an old handle then resolves to the merged user.
        adminQueries.adminReassignWebauthnUserHandles(deduplicatedSources, targetId)
        val movedNameLikes = likedNameService.reassignLikes(deduplicatedSources, targetId)
        val obsoletedMergedUsers = adminQueries.adminSetMergedUsersObsolete(deduplicatedSources, true)

        return MergeUsersResult(
            targetId = targetId,
            sourceIds = deduplicatedSources,
            movedTsUsers = movedTsUsers,
            movedSteamIds = movedSteamIds,
            movedAwards = movedAwards,
            movedQueuedMessages = movedQueuedMessages,
            movedLoginLinks = movedLoginLinks,
            movedPasskeys = movedPasskeys,
            movedNameLikes = movedNameLikes,
            obsoletedMergedUsers = obsoletedMergedUsers,
        )
    }
}
