package com.depromeet.team1.core.member

import java.time.Instant

/** 액세스 토큰 발급. 형식(JWT)과 서명·만료 시간은 구현이 정한다. */
interface AccessTokenIssuer {
    fun issue(memberId: Long, issuedAt: Instant): AccessToken
}

data class AccessToken(val value: String, val expiresAt: Instant)
