package com.depromeet.team1.core.member

import com.depromeet.team1.core.common.error.AppException

/** 등록한 인가 코드에만 사용자를 돌려주는 [OAuthClient]. 모르는 코드는 실제 구현처럼 잘못된 인가 코드로 실패한다. */
class FakeOAuthClient(override val provider: AuthProvider = AuthProvider.KAKAO) : OAuthClient {
    private val users = mutableMapOf<String, OAuthUser>()

    fun register(code: String, user: OAuthUser) {
        users[code] = user
    }

    override fun fetchUser(authorizationCode: String): OAuthUser =
        users[authorizationCode] ?: throw AppException(AuthError.AUTH_INVALID_OAUTH_CODE, "code=$authorizationCode")
}
