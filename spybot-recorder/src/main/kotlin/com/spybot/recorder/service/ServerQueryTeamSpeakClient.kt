package com.spybot.recorder.service

import com.spybot.core.config.SpybotProperties
import com.spybot.core.model.TeamSpeakChannelSnapshot
import com.spybot.core.model.TeamSpeakClientSnapshot
import com.spybot.core.model.TeamSpeakEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque

@Component
class ServerQueryTeamSpeakClient(
    private val properties: SpybotProperties,
) : TeamSpeakQueryClient {
    private val log = LoggerFactory.getLogger(javaClass)
    private var socket: Socket? = null
    private var reader: LineReader? = null
    private var output: OutputStream? = null
    private val queuedEvents = ArrayDeque<String>()

    override fun connect() {
        close()
        socket =
            Socket(properties.teamspeak.host, properties.teamspeak.port).apply {
                soTimeout = EVENT_TIMEOUT_MS
            }
        reader = LineReader(socket!!.getInputStream())
        output = socket!!.getOutputStream()
        discardGreeting()
        execute(
            "login",
            mapOf(
                "client_login_name" to properties.teamspeak.user.orEmpty(),
                "client_login_password" to properties.teamspeak.password.orEmpty(),
            ),
        )
        execute("use", mapOf("sid" to "1"))
    }

    override fun registerEvents() {
        execute("servernotifyregister", mapOf("event" to "channel", "id" to "0"))
        execute("servernotifyregister", mapOf("event" to "server"))
    }

    override fun setNickname(name: String) {
        var suffix = 0
        while (true) {
            val attempted = if (suffix == 0) name else "${name}_$suffix"
            try {
                execute("clientupdate", mapOf("client_nickname" to attempted))
                return
            } catch (error: TeamSpeakQueryException) {
                if (error.id == 513) {
                    suffix += 1
                    Thread.sleep(500)
                    continue
                }
                throw error
            }
        }
    }

    override fun getClients(): List<TeamSpeakClientSnapshot> =
        execute("clientlist", options = listOf("uid")).records.mapNotNull { record ->
            val uniqueIdentifier = record["client_unique_identifier"] ?: return@mapNotNull null
            TeamSpeakClientSnapshot(
                clientId = record["clid"]?.toIntOrNull() ?: return@mapNotNull null,
                channelId = record["cid"]?.toIntOrNull() ?: return@mapNotNull null,
                clientDatabaseId = record["client_database_id"]?.toIntOrNull() ?: 0,
                nickname = record["client_nickname"].orEmpty(),
                clientType = record["client_type"].orEmpty(),
                uniqueIdentifier = uniqueIdentifier,
            )
        }

    override fun getChannels(): List<TeamSpeakChannelSnapshot> =
        execute("channellist").records.mapNotNull { record ->
            TeamSpeakChannelSnapshot(
                id = record["cid"]?.toIntOrNull() ?: return@mapNotNull null,
                name = record["channel_name"].orEmpty(),
                order = record["channel_order"]?.toIntOrNull() ?: 0,
            )
        }

    override fun getChannelName(channelId: Int): String? =
        execute("channelinfo", mapOf("cid" to channelId.toString())).records.firstOrNull()?.get("channel_name")

    override fun waitForEvent(): TeamSpeakEvent? {
        val line =
            if (queuedEvents.isNotEmpty()) {
                queuedEvents.removeFirst()
            } else {
                try {
                    reader?.readLine() ?: throw TeamSpeakQueryException(-1, "connection closed")
                } catch (_: SocketTimeoutException) {
                    return null
                }
            }

        if (!line.startsWith("notify")) {
            return null
        }
        val records = parseRecords(line.substringAfter(' '))
        val record = records.firstOrNull().orEmpty()
        return when (line.substringBefore(' ')) {
            "notifycliententerview" -> {
                TeamSpeakEvent.ClientEnter(
                    TeamSpeakClientSnapshot(
                        clientId = record["clid"]?.toIntOrNull() ?: return null,
                        channelId = record["ctid"]?.toIntOrNull() ?: return null,
                        clientDatabaseId = record["client_database_id"]?.toIntOrNull() ?: 0,
                        nickname = record["client_nickname"].orEmpty(),
                        clientType = record["client_type"].orEmpty(),
                        uniqueIdentifier = record["client_unique_identifier"].orEmpty(),
                    ),
                )
            }

            "notifyclientleftview" -> {
                TeamSpeakEvent.ClientLeave(
                    clientId = record["clid"]?.toIntOrNull() ?: return null,
                    channelId = record["cfid"]?.toIntOrNull() ?: 0,
                    reasonId = record["reasonid"]?.toIntOrNull() ?: -1,
                )
            }

            "notifyclientmoved" -> {
                TeamSpeakEvent.ClientMove(
                    clientId = record["clid"]?.toIntOrNull() ?: return null,
                    channelToId = record["ctid"]?.toIntOrNull() ?: return null,
                    reasonId = record["reasonid"]?.toIntOrNull() ?: -1,
                )
            }

            else -> {
                null
            }
        }
    }

    override fun pokeClient(
        clientId: Int,
        message: String,
    ) {
        execute("clientpoke", mapOf("clid" to clientId.toString(), "msg" to message.take(100)))
    }

    override fun sendTextMessage(
        clientId: Int,
        message: String,
    ) {
        execute("sendtextmessage", mapOf("targetmode" to "1", "target" to clientId.toString(), "msg" to message.take(1024)))
    }

    override fun close() {
        runCatching { socket?.close() }
        reader = null
        output = null
        socket = null
        queuedEvents.clear()
    }

    private fun discardGreeting() {
        val existingTimeout = socket?.soTimeout ?: EVENT_TIMEOUT_MS
        socket?.soTimeout = 200
        try {
            while (true) {
                val line = reader?.readLine() ?: break
                if (line.isBlank()) {
                    break
                }
            }
        } catch (_: SocketTimeoutException) {
            // end of greeting
        } finally {
            socket?.soTimeout = existingTimeout
        }
    }

    private fun execute(
        command: String,
        params: Map<String, String> = emptyMap(),
        options: List<String> = emptyList(),
    ): TeamSpeakResponse {
        val commandLine =
            buildString {
                append(command)
                params.forEach { (key, value) ->
                    append(' ')
                    append(key)
                    append('=')
                    append(encodeValue(value))
                }
                options.forEach {
                    append(' ')
                    append(it)
                }
            }
        val target = output ?: error("TeamSpeak connection is not established")
        target.write((commandLine + "\n").toByteArray(StandardCharsets.UTF_8))
        target.flush()

        var bodyLine: String? = null
        while (true) {
            val line = reader?.readLine() ?: throw TeamSpeakQueryException(-1, "connection closed")
            if (line.startsWith("notify")) {
                queuedEvents += line
                continue
            }
            if (line.startsWith("error ")) {
                val errorData = parseRecord(line.removePrefix("error "))
                val errorId = errorData["id"]?.toIntOrNull() ?: -1
                val message = errorData["msg"].orEmpty()
                if (errorId != 0) {
                    throw TeamSpeakQueryException(errorId, message)
                }
                return TeamSpeakResponse(parseRecords(bodyLine))
            }
            bodyLine = line
        }
    }

    private fun parseRecords(raw: String?): List<Map<String, String>> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        return raw.split('|').map(::parseRecord).filter { it.isNotEmpty() }
    }

    private fun parseRecord(raw: String): Map<String, String> =
        raw
            .split(' ')
            .filter { it.isNotBlank() }
            .associate { token ->
                val parts = token.split('=', limit = 2)
                val key = parts.first()
                val value = if (parts.size == 2) decodeValue(parts[1]) else ""
                key to value
            }

    private fun encodeValue(value: String): String =
        buildString(value.length) {
            value.forEach { char ->
                append(
                    when (char) {
                        '\\' -> "\\\\"
                        '/' -> "\\/"
                        ' ' -> "\\s"
                        '|' -> "\\p"
                        '\u0007' -> "\\a"
                        '\b' -> "\\b"
                        '\t' -> "\\t"
                        '\n' -> "\\n"
                        '\u000B' -> "\\v"
                        '\u000C' -> "\\f"
                        '\r' -> "\\r"
                        else -> char
                    },
                )
            }
        }

    private fun decodeValue(value: String): String {
        val output = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val current = value[index]
            if (current == '\\' && index + 1 < value.length) {
                output.append(
                    when (value[index + 1]) {
                        '\\' -> '\\'
                        '/' -> '/'
                        's' -> ' '
                        'p' -> '|'
                        'a' -> '\u0007'
                        'b' -> '\b'
                        't' -> '\t'
                        'n' -> '\n'
                        'v' -> '\u000B'
                        'f' -> '\u000C'
                        'r' -> '\r'
                        else -> value[index + 1]
                    },
                )
                index += 2
            } else {
                output.append(current)
                index += 1
            }
        }
        return output.toString()
    }

    private data class TeamSpeakResponse(
        val records: List<Map<String, String>>,
    )

    private class TeamSpeakQueryException(
        val id: Int,
        message: String,
    ) : RuntimeException(message)

    /**
     * Reads raw bytes directly off the socket and only decodes UTF-8 once a complete line has
     * been assembled. Deliberately avoids BufferedReader/InputStreamReader: their internal
     * CharsetDecoder retry loop can spin at ~100% CPU without making progress (observed on this
     * JVM/platform combination) instead of cleanly blocking or throwing SocketTimeoutException.
     * A single InputStream.read() call per iteration has none of that retry machinery.
     *
     * The TS3 ServerQuery protocol terminates lines with "\n\r" (LF then CR) - the reverse of
     * conventional CRLF - so a line ends at the first LF, and a CR immediately following it
     * belongs to the terminator, not the next line, and must be swallowed.
     */
    internal class LineReader(
        private val input: InputStream,
    ) {
        private val buffer = ByteArray(8192)
        private var bufferLength = 0
        private var bufferPos = 0
        private val line = java.io.ByteArrayOutputStream(256)
        private var skipLeadingCarriageReturn = false

        fun readLine(): String? {
            while (true) {
                while (bufferPos < bufferLength) {
                    val byte = buffer[bufferPos]
                    bufferPos++
                    if (skipLeadingCarriageReturn) {
                        skipLeadingCarriageReturn = false
                        if (byte == CARRIAGE_RETURN) {
                            continue
                        }
                    }
                    if (byte == NEWLINE) {
                        skipLeadingCarriageReturn = true
                        return finishLine()
                    }
                    line.write(byte.toInt())
                }
                bufferLength = input.read(buffer)
                bufferPos = 0
                if (bufferLength < 0) {
                    return null
                }
            }
        }

        private fun finishLine(): String {
            val bytes = line.toByteArray()
            line.reset()
            return String(bytes, StandardCharsets.UTF_8)
        }

        companion object {
            private const val NEWLINE: Byte = '\n'.code.toByte()
            private const val CARRIAGE_RETURN: Byte = '\r'.code.toByte()
        }
    }

    companion object {
        private const val EVENT_TIMEOUT_MS = 1000
    }
}
