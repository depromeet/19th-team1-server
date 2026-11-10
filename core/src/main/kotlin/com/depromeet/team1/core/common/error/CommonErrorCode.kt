package com.depromeet.team1.core.common.error

enum class CommonErrorCode(
    override val message: String,
    override val type: ErrorType,
) : ErrorCode {
    INVALID_INPUT("요청 값이 올바르지 않습니다.", ErrorType.INVALID_INPUT),
    UNAUTHORIZED("로그인이 필요합니다.", ErrorType.UNAUTHORIZED),
    FORBIDDEN("접근 권한이 없습니다.", ErrorType.FORBIDDEN),
    RESOURCE_NOT_FOUND("요청한 대상을 찾을 수 없습니다.", ErrorType.NOT_FOUND),
    METHOD_NOT_ALLOWED("지원하지 않는 요청 방식입니다.", ErrorType.INVALID_INPUT),
    INTERNAL_ERROR("일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", ErrorType.INTERNAL),
}
