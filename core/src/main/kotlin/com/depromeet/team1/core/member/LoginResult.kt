package com.depromeet.team1.core.member

import java.time.Instant

/** 로그인 결과. [termsAgreementRequired] 가 참이면 웹이 약관 동의 화면을 띄운다. */
data class LoginResult(
    val memberId: Long,
    val newMember: Boolean,
    val termsAgreementRequired: Boolean,
    val tokens: IssuedTokens,
)

/** 발급한 토큰 쌍. 리프레시 토큰 원문은 이때 한 번만 내보내고 저장하지 않는다. */
data class IssuedTokens(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
)
