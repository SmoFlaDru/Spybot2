package com.spybot.core.service

import com.spybot.core.config.SpybotProperties
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Service
class AwardService(
    private val queryService: SpybotQueryService,
    private val properties: SpybotProperties,
) {
    fun runEndOfWeekAwards(): Int {
        val candidates = queryService.weeklyAwardCandidates().take(3)
        candidates.forEachIndexed { index, candidate ->
            val points = 3 - index
            queryService.createAward(candidate.userId, points)
            queryService.createNewsEvent(
                text = newsEventText(candidate.userId, index, points),
                websiteLink = "/u/${candidate.userId}",
            )
            queryService.replaceQueuedMessage(
                mergedUserId = candidate.userId,
                type = AWARD_USER_OF_WEEK,
                text = privateMessage(index, points),
            )
        }
        return candidates.size
    }

    private fun privateMessage(
        index: Int,
        points: Int,
    ): String {
        val specifier =
            when (index) {
                1 -> " second"
                2 -> " third"
                else -> ""
            }
        val metal =
            when (points) {
                3 -> "gold"
                2 -> "silver"
                else -> "bronze"
            }
        val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        return "You got a $metal award for being the$specifier most active user of the week ${weekStart.format(
            DATE_FORMAT,
        )}! See more: [url]${properties.publicBaseUrl}[/url]"
    }

    private fun newsEventText(
        userId: Long,
        index: Int,
        points: Int,
    ): String {
        val userName = escapeHtml(queryService.mergedUserName(userId).orEmpty())
        val totalAwards = queryService.countAwardsForUser(userId)
        val sameScoreAwards = queryService.countAwardsForUserByPoints(userId, points)
        val specifier =
            when (index) {
                1 -> " second"
                2 -> " third"
                else -> ""
            }
        val metal =
            when (points) {
                3 -> "gold"
                2 -> "silver"
                else -> "bronze"
            }

        val detail =
            when {
                totalAwards == 1 -> "This is the first time <strong>$userName</strong> won any award."

                sameScoreAwards == 1 -> "This is the first time <strong>$userName</strong> won a $metal award."

                sameScoreAwards < 4 -> "This is only the ${ordinalWord(
                    sameScoreAwards,
                )} time <strong>$userName</strong> won a $metal award."

                else -> "This is the ${ordinalWord(
                    sameScoreAwards,
                )} time <strong>$userName</strong> won a $metal award, ${ordinalWord(totalAwards)} award overall."
            }

        val now = LocalDate.now()
        val weekNumber = now.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return "<strong>$userName</strong> earned the $metal award for being the$specifier most active user of week&nbsp;$weekNumber in ${now.year}. Congratulations! $detail"
    }

    private fun ordinalWord(number: Int): String =
        when (number) {
            1 -> "first"
            2 -> "second"
            3 -> "third"
            4 -> "fourth"
            5 -> "fifth"
            6 -> "sixth"
            7 -> "seventh"
            8 -> "eighth"
            9 -> "ninth"
            10 -> "tenth"
            else -> "${number}${ordinalSuffix(number)}"
        }

    private fun ordinalSuffix(number: Int): String =
        if (number % 100 in 11..13) {
            "th"
        } else {
            when (number % 10) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
        }

    private fun escapeHtml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    companion object {
        private val DATE_FORMAT =
            java.time.format.DateTimeFormatter
                .ofPattern("dd.MM.yyyy", Locale.ENGLISH)
        private const val AWARD_USER_OF_WEEK = "AWARD_USER_OF_WEEK"
    }
}
