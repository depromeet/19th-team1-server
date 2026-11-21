package com.depromeet.team1.core.member

import java.time.Duration
import java.time.Instant

/** 회원 번호와 발급 시각을 그대로 담은 문자열을 액세스 토큰으로 준다. 만료는 30분 뒤다. */
class FakeAccessTokenIssuer : AccessTokenIssuer {
    override fun issue(memberId: Long, issuedAt: Instant): AccessToken =
        AccessToken("access:$memberId:${issuedAt.epochSecond}", issuedAt.plus(VALIDITY))

    companion object {
        val VALIDITY: Duration = Duration.ofMinutes(30)
    }
}
