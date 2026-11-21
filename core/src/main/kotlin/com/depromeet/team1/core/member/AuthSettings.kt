package com.depromeet.team1.core.member

import java.time.Duration

/** 인증 설정값. 값은 api 의 설정(auth.*)에서 받는다. */
data class AuthSettings(
    /** 로테이션으로 폐기된 리프레시 토큰이 이 시간 안에 다시 오면 동시 재발급으로 보고 새 토큰 쌍을 준다. 지나서 오면 탈취로 본다. */
    val refreshTokenReuseGrace: Duration,
) {
    init {
        require(!refreshTokenReuseGrace.isNegative) { "리프레시 토큰 재사용 유예 시간은 0 이상이다" }
    }
}
