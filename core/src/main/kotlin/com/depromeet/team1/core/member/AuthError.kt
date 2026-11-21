package com.depromeet.team1.core.member

import com.depromeet.team1.core.common.error.ErrorCategory
import com.depromeet.team1.core.common.error.ErrorCode

enum class AuthError(override val message: String, override val category: ErrorCategory) : ErrorCode {
    AUTH_INVALID_OAUTH_CODE("로그인 정보가 올바르지 않습니다. 다시 로그인해 주세요.", ErrorCategory.BAD_REQUEST),
    AUTH_OAUTH_UNAVAILABLE("로그인 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.", ErrorCategory.INTERNAL),
}
