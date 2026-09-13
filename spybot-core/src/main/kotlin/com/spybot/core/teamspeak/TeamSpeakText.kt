package com.spybot.core.teamspeak

/** Undoes the ServerQuery escaping (\\s, \\p, \\/ ...) the recorder stores channel and user names with. */
internal fun unescapeTeamSpeak(value: String): String {
    val result = StringBuilder(value.length)
    var i = 0
    while (i < value.length) {
        val char = value[i]
        val next = value.getOrNull(i + 1)
        val replacement =
            if (char == '\\' && next != null) {
                when (next) {
                    '\\' -> '\\'
                    '/' -> '/'
                    's' -> ' '
                    'p' -> '|'
                    ';' -> ';'
                    'a' -> '\u0007'
                    'b' -> '\b'
                    'f' -> '\u000C'
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    'v' -> '\u000B'
                    else -> null
                }
            } else {
                null
            }
        if (replacement != null) {
            result.append(replacement)
            i += 2
        } else {
            result.append(char)
            i += 1
        }
    }
    return result.toString()
}

/** The inverse of [unescapeTeamSpeak] for the characters that occur in channel names: what the recorder stores. */
internal fun escapeTeamSpeak(value: String): String =
    buildString(value.length) {
        for (char in value) {
            when (char) {
                '\\' -> append("\\\\")
                '/' -> append("\\/")
                ' ' -> append("\\s")
                '|' -> append("\\p")
                else -> append(char)
            }
        }
    }
