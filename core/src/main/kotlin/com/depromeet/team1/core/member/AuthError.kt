package com.depromeet.team1.core.member

import com.depromeet.team1.core.common.error.ErrorCategory
import com.depromeet.team1.core.common.error.ErrorCode

enum class AuthError(override val message: String, override val category: ErrorCategory) : ErrorCode {
    AUTH_INVALID_OAUTH_CODE("로그인 정보가 올바르지 않습니다. 다시 로그인해 주세요.", ErrorCategory.BAD_REQUEST),
    AUTH_OAUTH_UNAVAILABLE("로그인 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.", ErrorCategory.INTERNAL),
    AUTH_UNAUTHORIZED("로그인이 필요합니다.", ErrorCategory.UNAUTHORIZED),
    AUTH_TOKEN_EXPIRED("로그인 시간이 지났습니다.", ErrorCategory.UNAUTHORIZED),
    AUTH_INVALID_REFRESH_TOKEN("로그인이 만료되었습니다. 다시 로그인해 주세요.", ErrorCategory.UNAUTHORIZED),
    AUTH_REQUIRED_TERMS_NOT_AGREED("필수 약관에 모두 동의해야 합니다.", ErrorCategory.BAD_REQUEST),
}
