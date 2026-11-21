package com.depromeet.team1.infra.storage.postgresql.llm

import com.depromeet.team1.core.llm.LlmUsage
import com.depromeet.team1.core.llm.LlmUsageRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository
class JpaLlmUsageRepository(private val llmUsageJpaRepository: LlmUsageJpaRepository) : LlmUsageRepository {
    @Transactional
    override fun save(usage: LlmUsage): LlmUsage = llmUsageJpaRepository.save(usage.toEntity()).toDomain()

    override fun findById(id: Long): LlmUsage? = llmUsageJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun sumTokensByMemberSince(memberId: Long, since: Instant): Long =
        llmUsageJpaRepository.sumTokensByMemberSince(memberId, since)

    override fun sumTokensSince(since: Instant): Long = llmUsageJpaRepository.sumTokensSince(since)
}
