package com.depromeet.team1.core.member

import java.time.Instant

interface RefreshTokenRepository {
    fun save(token: RefreshToken): RefreshToken

    fun findByTokenHash(tokenHash: String): RefreshToken?

    /** 폐기되지 않은 토큰만 폐기한다. 이미 폐기됐으면(동시에 쓰인 경우 포함) 아무것도 바꾸지 않고 false 를 돌려준다. */
    fun revoke(id: Long, now: Instant): Boolean

    /** 회원의 폐기되지 않은 토큰을 모두 폐기하고 폐기한 수를 돌려준다. */
    fun revokeAllActive(memberId: Long, now: Instant): Int
}
