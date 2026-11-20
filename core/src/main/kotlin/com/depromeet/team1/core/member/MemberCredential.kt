package com.depromeet.team1.core.member

import java.time.Instant

/** 회원의 로그인 수단. [revokedAt] 이 있으면 로그인에 쓰지 않고, 같은 제공자 계정으로 새로 가입할 수 있다. */
data class MemberCredential(
    val id: Long?,
    val memberId: Long,
    val provider: AuthProvider,
    val providerUserId: String,
    val createdAt: Instant,
    val revokedAt: Instant?,
) {
    init {
        require(providerUserId.isNotBlank() && providerUserId.length <= PROVIDER_USER_ID_MAX) {
            "제공자 사용자 ID 는 1~${PROVIDER_USER_ID_MAX}자다"
        }
    }

    companion object {
        const val PROVIDER_USER_ID_MAX = 100

        fun create(memberId: Long, provider: AuthProvider, providerUserId: String, now: Instant): MemberCredential =
            MemberCredential(
                id = null,
                memberId = memberId,
                provider = provider,
                providerUserId = providerUserId,
                createdAt = now,
                revokedAt = null,
            )
    }
}

enum class AuthProvider { KAKAO }
