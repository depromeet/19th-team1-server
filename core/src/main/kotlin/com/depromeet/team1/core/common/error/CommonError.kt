package com.depromeet.team1.core.common.error

/** 특정 기능에 속하지 않는 공통 에러 코드. */
enum class CommonError(override val message: String, override val category: ErrorCategory) : ErrorCode {
    INVALID_INPUT("요청 값이 올바르지 않습니다.", ErrorCategory.BAD_REQUEST),
    RESOURCE_NOT_FOUND("요청한 대상을 찾을 수 없습니다.", ErrorCategory.NOT_FOUND),
    INTERNAL_ERROR("일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", ErrorCategory.INTERNAL),
}
