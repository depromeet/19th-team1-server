package com.depromeet.team1.core.llm

import com.depromeet.team1.core.common.error.ErrorCategory
import com.depromeet.team1.core.common.error.ErrorCode

enum class LlmError(override val message: String, override val category: ErrorCategory) : ErrorCode {
    /** 팀이 정한 사용량 상한(사용자별·전체)을 넘었다. 다시 해도 소용없는 실패다. */
    LLM_LIMIT_EXCEEDED("오늘 사용할 수 있는 분석량을 모두 썼습니다. 내일 다시 시도해 주세요.", ErrorCategory.INTERNAL),

    /** 벤더의 호출 한도에 걸렸다(429). 잠시 뒤 다시 하면 될 수 있다. */
    LLM_RATE_LIMITED("요청이 많아 잠시 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.", ErrorCategory.INTERNAL),

    /** 그 밖의 호출 실패(5xx, 타임아웃, 응답 형식 오류). */
    LLM_UNAVAILABLE("분석 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.", ErrorCategory.INTERNAL),
}
