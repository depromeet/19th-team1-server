package com.depromeet.team1.core.common.error

/**
 * 에러의 분류. 도메인은 HTTP 를 모르고 이 분류만 정한다. HTTP 상태 코드는 api 가 이 분류를 보고 정한다.
 *
 * @property serverFault 서버 쪽 문제인지 여부. true 면 ERROR, false 면 WARN 으로 기록한다.
 */
enum class ErrorType(
    val serverFault: Boolean,
) {
    INVALID_INPUT(false),
    UNAUTHORIZED(false),
    FORBIDDEN(false),
    NOT_FOUND(false),
    CONFLICT(false),
    EXTERNAL_FAILURE(true),
    INTERNAL(true),
}
