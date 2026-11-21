package com.depromeet.team1.core.llm

import java.time.Instant

interface LlmUsageRepository {
    fun save(usage: LlmUsage): LlmUsage

    fun findById(id: Long): LlmUsage?

    /** [since] 이후(포함) 그 사용자의 입력·출력 토큰 합. 없으면 0. 사용자별 상한 검사용이다. */
    fun sumTokensByMemberSince(memberId: Long, since: Instant): Long

    /** [since] 이후(포함) 모든 사용자의 입력·출력 토큰 합. 없으면 0. 전체 상한 검사용이다. */
    fun sumTokensSince(since: Instant): Long
}
