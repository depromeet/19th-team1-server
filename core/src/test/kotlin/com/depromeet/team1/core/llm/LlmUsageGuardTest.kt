package com.depromeet.team1.core.llm

import com.depromeet.team1.core.common.error.AppException
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LlmUsageGuardTest {
    private val repository = FakeLlmUsageRepository()
    private val settings = LlmUsageSettings(memberDailyTokenLimit = 1_000, globalDailyTokenLimit = 3_000)

    // 한국 시간 2026-11-21 12:00. 오늘의 시작은 한국 시간 자정 = 2026-11-20T15:00Z.
    private val guard = LlmUsageGuard(repository, settings, Clock.fixed(NOW, ZoneOffset.UTC))

    @Test
    fun `사용자별 상한 미만이면 통과한다`() {
        record(memberId = 1, tokens = 999)

        guard.check(1)
    }

    @Test
    fun `사용자별 상한에 이르면 상한 초과로 거절한다`() {
        record(memberId = 1, tokens = 1_000)

        val e = assertFailsWith<AppException> { guard.check(1) }

        assertEquals(LlmError.LLM_LIMIT_EXCEEDED, e.errorCode)
        assertEquals("scope=MEMBER memberId=1", e.detail)
    }

    @Test
    fun `다른 사용자의 사용량은 사용자별 상한에 섞이지 않는다`() {
        record(memberId = 2, tokens = 1_000)

        guard.check(1)
    }

    @Test
    fun `전체 상한에 이르면 처음 쓰는 사용자도 상한 초과로 거절한다`() {
        record(memberId = 2, tokens = 999)
        record(memberId = 3, tokens = 999)
        record(memberId = 4, tokens = 1_002)

        val e = assertFailsWith<AppException> { guard.check(1) }

        assertEquals(LlmError.LLM_LIMIT_EXCEEDED, e.errorCode)
        assertEquals("scope=GLOBAL", e.detail)
    }

    @Test
    fun `전체 상한 미만이면 통과한다`() {
        record(memberId = 2, tokens = 999)
        record(memberId = 3, tokens = 999)
        record(memberId = 4, tokens = 1_001)

        guard.check(1)
    }

    @Test
    fun `한국 시간 자정 전의 사용량은 오늘 상한에 넣지 않는다`() {
        record(memberId = 1, tokens = 1_000, at = Instant.parse("2026-11-20T14:59:59Z"))

        guard.check(1)
    }

    @Test
    fun `한국 시간 자정부터의 사용량은 오늘 상한에 넣는다`() {
        record(memberId = 1, tokens = 1_000, at = Instant.parse("2026-11-20T15:00:00Z"))

        assertFailsWith<AppException> { guard.check(1) }
    }

    private fun record(memberId: Long, tokens: Int, at: Instant = NOW) {
        repository.save(LlmUsage(null, memberId, LlmPurpose.ANALYZE, "model", tokens, 0, null, null, at))
    }

    companion object {
        val NOW: Instant = Instant.parse("2026-11-21T03:00:00Z")
    }
}
