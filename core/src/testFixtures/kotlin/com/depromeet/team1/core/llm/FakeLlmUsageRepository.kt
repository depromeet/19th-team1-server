package com.depromeet.team1.core.llm

import java.time.Instant

/** 메모리에 사용량을 두는 [LlmUsageRepository]. 실제 구현처럼 저장할 때 번호를 매긴다. */
class FakeLlmUsageRepository : LlmUsageRepository {
    private val usages = mutableListOf<LlmUsage>()
    private var nextId = 1L

    override fun save(usage: LlmUsage): LlmUsage = usage.copy(id = nextId++).also { usages += it }

    override fun findById(id: Long): LlmUsage? = usages.find { it.id == id }

    override fun sumTokensByMemberSince(memberId: Long, since: Instant): Long =
        usages.filter { it.memberId == memberId && !it.createdAt.isBefore(since) }.sumOf { it.totalTokens() }

    override fun sumTokensSince(since: Instant): Long =
        usages.filter { !it.createdAt.isBefore(since) }.sumOf { it.totalTokens() }

    /** 테스트 확인용: 저장한 사용량 전체. */
    fun all(): List<LlmUsage> = usages.toList()

    private fun LlmUsage.totalTokens(): Long = inputTokens.toLong() + outputTokens
}
