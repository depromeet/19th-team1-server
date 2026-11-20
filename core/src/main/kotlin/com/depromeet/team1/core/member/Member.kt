package com.depromeet.team1.core.member

import java.time.Instant

/** 회원. 탈퇴해도 프리런칭 기간에는 행을 지우지 않는다. */
data class Member(
    val id: Long?,
    val nickname: String,
    val email: String?,
    val acquisitionChannel: String?,
    val status: MemberStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val withdrawnAt: Instant?,
) {
    init {
        require(nickname.isNotBlank() && nickname.length <= NICKNAME_MAX) { "닉네임은 1~${NICKNAME_MAX}자다" }
        require(email == null || email.length <= EMAIL_MAX) { "이메일은 ${EMAIL_MAX}자 이하다" }
        require(acquisitionChannel == null || acquisitionChannel.length <= CHANNEL_MAX) {
            "가입 경로는 ${CHANNEL_MAX}자 이하다"
        }
        require((status == MemberStatus.WITHDRAWN) == (withdrawnAt != null)) { "탈퇴 회원만 탈퇴 시각이 있다" }
    }

    companion object {
        const val NICKNAME_MAX = 30
        const val EMAIL_MAX = 320
        const val CHANNEL_MAX = 30

        fun create(nickname: String, email: String?, now: Instant): Member = Member(
            id = null,
            nickname = nickname,
            email = email,
            acquisitionChannel = null,
            status = MemberStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
            withdrawnAt = null,
        )
    }
}

enum class MemberStatus { ACTIVE, WITHDRAWN }
