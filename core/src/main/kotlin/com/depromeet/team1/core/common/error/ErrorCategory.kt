package com.depromeet.team1.core.common.error

/**
 * 에러의 분류. 도메인은 HTTP 를 모르고 이 분류만 정한다. HTTP 상태 코드는 api 가 이 분류를 보고 정한다.
 * 지금 쓰는 분류만 둔다. 외부 연동 실패 등은 그 기능이 생길 때 추가한다.
 */
enum class ErrorCategory {
    BAD_REQUEST,

    /** 인증이 없거나 쓸 수 없다(토큰 없음·위조·만료, 쓸 수 없는 리프레시 토큰). */
    UNAUTHORIZED,
    NOT_FOUND,
    CONFLICT,
    INTERNAL,
}
