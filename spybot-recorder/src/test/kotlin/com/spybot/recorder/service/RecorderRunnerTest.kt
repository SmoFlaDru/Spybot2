package com.spybot.recorder.service

import com.spybot.core.config.SpybotProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecorderRunnerTest {
    @Test
    fun `runner starts and stops gateway`() {
        val gateway = FakeGateway()
        val runner = RecorderRunner(gateway, SpybotProperties())

        runner.run(org.springframework.boot.DefaultApplicationArguments(*emptyArray<String>()))
        runner.shutdown()

        assertEquals(1, gateway.started)
        assertEquals(1, gateway.stopped)
    }

    private class FakeGateway : TeamSpeakGateway {
        var started = 0
        var stopped = 0

        override fun start() {
            started += 1
        }

        override fun stop() {
            stopped += 1
        }
    }
}
