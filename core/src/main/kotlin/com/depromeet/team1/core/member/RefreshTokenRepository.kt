package com.depromeet.team1.core.member

import java.time.Instant

interface RefreshTokenRepository {
    fun save(token: RefreshToken): RefreshToken

    fun findByTokenHash(tokenHash: String): RefreshToken?

    /** 로테이션으로 폐기한다. 폐기되지 않은 토큰만 폐기하고, 이미 폐기됐으면(동시에 쓰인 경우 포함) 아무것도 바꾸지 않고 false 를 돌려준다. */
    fun revoke(id: Long, now: Instant): Boolean

    /** 토큰 행을 지운다(로그아웃). 없으면 아무것도 하지 않는다. */
    fun delete(id: Long)

    /** 회원의 리프레시 토큰 행을 폐기 여부와 무관하게 모두 지우고 지운 수를 돌려준다(탈취 감지). */
    fun deleteAllByMemberId(memberId: Long): Int
}
