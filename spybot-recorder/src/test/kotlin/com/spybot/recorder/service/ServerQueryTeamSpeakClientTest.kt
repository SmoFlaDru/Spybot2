package com.spybot.recorder.service

import com.spybot.core.config.SpybotProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.nio.charset.StandardCharsets

class ServerQueryTeamSpeakClientTest {
    @Test
    fun `waitForEvent throws instead of returning null forever when the connection reaches EOF`() {
        // Regression test for the production incident this caused: once the remote side closed
        // the socket, readLine() returned null immediately (no blocking) on every call, and
        // waitForEvent() treated that exactly like a normal read timeout - returning null instead
        // of signalling connection loss. RecorderLoopGateway's event loop then spun at ~100% CPU
        // forever instead of hitting the reconnect/backoff path.
        val client = ServerQueryTeamSpeakClient(SpybotProperties())
        val readerField = ServerQueryTeamSpeakClient::class.java.getDeclaredField("reader")
        readerField.isAccessible = true
        readerField.set(client, ServerQueryTeamSpeakClient.LineReader(ChunkedInputStream(ByteArray(0), chunkSize = 4096)))

        assertThrows(RuntimeException::class.java) { client.waitForEvent() }
    }

    @Test
    fun `reads plain lines separated by newlines`() {
        val reader =
            ServerQueryTeamSpeakClient.LineReader(
                ChunkedInputStream("first\nsecond\nthird\n".toByteArray(StandardCharsets.UTF_8), chunkSize = 4096),
            )

        assertEquals("first", reader.readLine())
        assertEquals("second", reader.readLine())
        assertEquals("third", reader.readLine())
    }

    @Test
    fun `strips the CR that follows LF per the TS3 ServerQuery line terminator`() {
        // TS3 ServerQuery terminates lines with LF-then-CR ("\n\r"), the reverse of conventional
        // CRLF. A naive trailing-CR strip (checking the byte before LF) misses this entirely and
        // leaks a stray leading CR onto the start of every following line - which is exactly the
        // bug this test guards against.
        val banner = "TS3\n\rWelcome to the TeamSpeak 3 ServerQuery interface.\n\r"
        val reader = ServerQueryTeamSpeakClient.LineReader(ChunkedInputStream(banner.toByteArray(StandardCharsets.UTF_8), chunkSize = 4096))

        assertEquals("TS3", reader.readLine())
        assertEquals("Welcome to the TeamSpeak 3 ServerQuery interface.", reader.readLine())
    }

    @Test
    fun `recognizes a response line as an error line even after a preceding LF-CR terminated line`() {
        // Regression test for the exact failure this bug caused in production: because the
        // leaked CR made the error line read as "\rerror id=0 msg=ok", `startsWith("error ")`
        // never matched and the client blocked forever waiting for a response that had already
        // arrived.
        val stream = "notifyx a=b\n\rerror id=0 msg=ok\n\r"
        val reader = ServerQueryTeamSpeakClient.LineReader(ChunkedInputStream(stream.toByteArray(StandardCharsets.UTF_8), chunkSize = 4096))

        reader.readLine()
        val errorLine = reader.readLine()

        assertEquals("error id=0 msg=ok", errorLine)
        assert(errorLine!!.startsWith("error ")) { "expected line to start with 'error ' but was: $errorLine" }
    }

    @Test
    fun `handles the CR terminator split across separate underlying reads`() {
        // The CR can arrive in a later read() call than the LF that precedes it; the reader must
        // still swallow it rather than treating it as the start of the next line's content.
        val reader =
            ServerQueryTeamSpeakClient.LineReader(
                ChunkedInputStream("first\n\rsecond\n\r".toByteArray(StandardCharsets.UTF_8), chunkSize = 1),
            )

        assertEquals("first", reader.readLine())
        assertEquals("second", reader.readLine())
    }

    @Test
    fun `returns null at end of stream`() {
        val reader =
            ServerQueryTeamSpeakClient.LineReader(
                ChunkedInputStream("only\n\r".toByteArray(StandardCharsets.UTF_8), chunkSize = 4096),
            )

        assertEquals("only", reader.readLine())
        assertNull(reader.readLine())
    }

    @Test
    fun `decodes a multi-byte UTF-8 character split across separate reads`() {
        // "Gästeecke" contains an ä (2 UTF-8 bytes); force the stream to hand back bytes one at a
        // time so the split can land in the middle of that multi-byte sequence.
        val reader =
            ServerQueryTeamSpeakClient.LineReader(
                ChunkedInputStream("Gästeecke\n\r".toByteArray(StandardCharsets.UTF_8), chunkSize = 1),
            )

        assertEquals("Gästeecke", reader.readLine())
    }

    @Test
    fun `handles a line spanning multiple underlying reads`() {
        val reader =
            ServerQueryTeamSpeakClient.LineReader(
                ChunkedInputStream("notifycliententerview clid=5\n\r".toByteArray(StandardCharsets.UTF_8), chunkSize = 3),
            )

        assertEquals("notifycliententerview clid=5", reader.readLine())
    }

    private class ChunkedInputStream(
        private val data: ByteArray,
        private val chunkSize: Int,
    ) : InputStream() {
        private var position = 0

        override fun read(): Int = throw UnsupportedOperationException("not used by LineReader")

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            if (position >= data.size) {
                return -1
            }
            val toCopy = minOf(chunkSize, len, data.size - position)
            System.arraycopy(data, position, b, off, toCopy)
            position += toCopy
            return toCopy
        }
    }
}
