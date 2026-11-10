package com.depromeet.team1.api.web.common

import com.depromeet.team1.core.common.error.ErrorCode

/** 실패 응답 본문: `{ "code": "ITEM_NOT_FOUND", "message": "..." }` */
data class ErrorResponse(val code: String, val message: String) {
    companion object {
        fun of(errorCode: ErrorCode) = ErrorResponse(code = errorCode.name, message = errorCode.message)
    }
}
