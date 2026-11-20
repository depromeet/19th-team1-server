package com.depromeet.team1.core.llm

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertFailsWith

class LlmUsageTest {
    @Test
    fun `토큰 수는 음수일 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            LlmUsage(null, 1, LlmPurpose.CHAT, "model", -1, 0, null, null, Instant.parse("2026-11-21T03:00:00Z"))
        }
    }
}
