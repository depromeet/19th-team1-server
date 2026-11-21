package com.depromeet.team1.infra.storage.postgresql.llm

import com.depromeet.team1.core.llm.LlmPurpose
import com.depromeet.team1.core.llm.LlmUsage
import com.depromeet.team1.core.llm.LlmUsageRepository
import com.depromeet.team1.infra.storage.postgresql.StorageTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JpaLlmUsageRepositoryTest : StorageTest() {
    @Autowired
    private lateinit var llmUsageRepository: LlmUsageRepository

    private val since: Instant = Instant.parse("2026-11-20T15:00:00Z")

    @Test
    fun `저장한 사용량을 ID 로 조회한다`() {
        val saved = llmUsageRepository.save(
            LlmUsage(null, 1, LlmPurpose.ANALYZE, "model-a", 1200, 300, 5, null, NOW),
        )

        assertEquals(saved, llmUsageRepository.findById(assertNotNull(saved.id)))
    }

    @Test
    fun `기간 안 그 사용자의 입력과 출력 토큰을 더한다`() {
        save(memberId = 1, input = 1_000, output = 200, at = since)
        save(memberId = 1, input = 300, output = 0, at = NOW)

        assertEquals(1_500, llmUsageRepository.sumTokensByMemberSince(1, since))
    }

    @Test
    fun `사용자별 합에는 기간 전 사용량과 다른 사용자 사용량이 들어가지 않는다`() {
        save(memberId = 1, input = 100, output = 0, at = NOW)
        save(memberId = 1, input = 5_000, output = 0, at = since.minusSeconds(1))
        save(memberId = 2, input = 7_000, output = 0, at = NOW)

        assertEquals(100, llmUsageRepository.sumTokensByMemberSince(1, since))
    }

    @Test
    fun `사용량이 없으면 사용자별 합은 0 이다`() {
        assertEquals(0, llmUsageRepository.sumTokensByMemberSince(1, since))
    }

    @Test
    fun `전체 합은 기간 안 모든 사용자의 토큰을 더한다`() {
        save(memberId = 1, input = 100, output = 10, at = NOW)
        save(memberId = 2, input = 200, output = 20, at = since)
        save(memberId = 3, input = 9_000, output = 0, at = since.minusSeconds(1))

        assertEquals(330, llmUsageRepository.sumTokensSince(since))
    }

    @Test
    fun `사용량이 없으면 전체 합은 0 이다`() {
        assertEquals(0, llmUsageRepository.sumTokensSince(since))
    }

    @Test
    fun `토큰 합이 int 범위를 넘어도 그대로 더한다`() {
        save(memberId = 1, input = Int.MAX_VALUE, output = 0, at = NOW)
        save(memberId = 1, input = Int.MAX_VALUE, output = 0, at = NOW)

        assertEquals(2L * Int.MAX_VALUE, llmUsageRepository.sumTokensSince(since))
    }

    private fun save(memberId: Long, input: Int, output: Int, at: Instant) {
        llmUsageRepository.save(LlmUsage(null, memberId, LlmPurpose.ANALYZE, "model-a", input, output, null, null, at))
    }
}
