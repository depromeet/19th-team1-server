package com.depromeet.team1.core.member

import java.time.Instant

/** 회원이 약관 한 종류의 한 버전에 동의한 기록. 고치거나 지우지 않는다. */
data class MemberTermsAgreement(
    val id: Long?,
    val memberId: Long,
    val termsType: TermsType,
    val termsVersion: String,
    val agreedAt: Instant,
) {
    init {
        require(termsVersion.isNotBlank() && termsVersion.length <= VERSION_MAX) { "약관 버전은 1~${VERSION_MAX}자다" }
    }

    companion object {
        const val VERSION_MAX = 20
    }
}

/**
 * 약관 종류와 지금 받는 버전. 종류·필수 여부·문구는 scope.md 결정 대기 11 을 따른다.
 * 정해지기 전까지 서비스 이용약관과 개인정보 수집·이용 동의를 필수로 둔다.
 */
enum class TermsType(val required: Boolean, val currentVersion: String) {
    SERVICE(required = true, currentVersion = "1"),
    PRIVACY(required = true, currentVersion = "1"),
}
