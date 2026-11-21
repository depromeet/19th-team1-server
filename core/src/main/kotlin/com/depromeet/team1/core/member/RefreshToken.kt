package com.depromeet.team1.core.member

import java.time.Duration
import java.time.Instant

/** 저장된 리프레시 토큰. 원문은 갖지 않고 해시만 갖는다. */
data class RefreshToken(
    val id: Long?,
    val memberId: Long,
    val tokenHash: String,
    val expiresAt: Instant,
    val createdAt: Instant,
    val revokedAt: Instant?,
) {
    init {
        require(tokenHash.length == HASH_LENGTH) { "토큰 해시는 ${HASH_LENGTH}자다" }
        require(expiresAt.isAfter(createdAt)) { "만료 시각은 발급 시각 뒤다" }
    }

    companion object {
        const val HASH_LENGTH = 64
        val VALIDITY: Duration = Duration.ofDays(14)
    }
}
