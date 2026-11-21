package com.depromeet.team1.core.member

/**
 * 소셜 로그인 제공자 연동. 제공자마다 구현을 하나씩 둔다.
 * 실패는 [AuthError.AUTH_INVALID_OAUTH_CODE](인가 코드 거절)와 [AuthError.AUTH_OAUTH_UNAVAILABLE](그 밖의 연동 실패)로 던진다.
 */
interface OAuthClient {
    val provider: AuthProvider

    /** 인가 코드로 제공자 토큰을 받아 사용자 정보를 조회한다. */
    fun fetchUser(authorizationCode: String): OAuthUser
}

/** 제공자가 알려 준 사용자. 닉네임과 이메일은 사용자가 제공에 동의하지 않으면 없다. */
data class OAuthUser(val provider: AuthProvider, val providerUserId: String, val nickname: String?, val email: String?)
