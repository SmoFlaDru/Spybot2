package com.spybot.core.model

import com.spybot.core.teamspeak.escapeTeamSpeak

/**
 * The channels being in doesn't count as activity: the AFK channels and the entrance lobby
 * people get parked in. Every statistic that separates online from AFK time, the live view's
 * red/green markers, the widget's active/inactive split and the hall of fame records all use
 * this one list.
 */
object InactiveChannels {
    /** Channel names as displayed (after [com.spybot.core.teamspeak.unescapeTeamSpeak]). */
    val names: List<String> = listOf("AFK", "bei Bedarf anstupsen", "Eingangsbereich")

    /** The same names as the recorder stores them in tschannel.name (ServerQuery-escaped). */
    val storedNames: List<String> = names.map(::escapeTeamSpeak)

    /** A SQL list literal of [storedNames] for the hand-written queries: `'AFK', 'bei\sBedarf\sanstupsen', ...`. */
    val sqlList: String = storedNames.joinToString(", ") { "'$it'" }

    fun isInactive(displayName: String?): Boolean = displayName in names

    fun isInactiveStoredName(storedName: String?): Boolean = storedName in storedNames
}
