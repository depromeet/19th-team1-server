package com.depromeet.team1.infra.storage.postgresql.llm

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface LlmUsageJpaRepository : JpaRepository<LlmUsageJpaEntity, Long> {
    // 인덱스 (member_id, created_at)를 탄다.
    @Query(
        "select coalesce(sum(cast(u.inputTokens as long) + u.outputTokens), 0) from LlmUsageJpaEntity u " +
            "where u.memberId = :memberId and u.createdAt >= :since",
    )
    fun sumTokensByMemberSince(memberId: Long, since: Instant): Long

    // 인덱스 (created_at)를 탄다.
    @Query(
        "select coalesce(sum(cast(u.inputTokens as long) + u.outputTokens), 0) from LlmUsageJpaEntity u " +
            "where u.createdAt >= :since",
    )
    fun sumTokensSince(since: Instant): Long
}
