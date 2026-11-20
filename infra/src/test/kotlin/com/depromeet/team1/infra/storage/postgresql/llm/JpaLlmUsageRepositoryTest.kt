package com.depromeet.team1.infra.storage.postgresql.llm

import com.depromeet.team1.core.llm.LlmPurpose
import com.depromeet.team1.core.llm.LlmUsage
import com.depromeet.team1.core.llm.LlmUsageRepository
import com.depromeet.team1.infra.storage.postgresql.StorageTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JpaLlmUsageRepositoryTest : StorageTest() {
    @Autowired
    private lateinit var llmUsageRepository: LlmUsageRepository

    @Test
    fun `저장한 사용량을 ID 로 조회한다`() {
        val saved = llmUsageRepository.save(
            LlmUsage(null, 1, LlmPurpose.ANALYZE, "model-a", 1200, 300, 5, null, NOW),
        )

        assertEquals(saved, llmUsageRepository.findById(assertNotNull(saved.id)))
    }
}
