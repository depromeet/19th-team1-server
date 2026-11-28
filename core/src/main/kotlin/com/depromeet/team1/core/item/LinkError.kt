package com.depromeet.team1.core.item

import com.depromeet.team1.core.common.error.ErrorCategory
import com.depromeet.team1.core.common.error.ErrorCode

/** 링크 수집 중 외부에서 온 오류. 다시 할지는 분석이 코드로 정한다. */
enum class LinkError(override val message: String, override val category: ErrorCategory) : ErrorCode {
    /** 없는 글이다(404·410). 다시 해도 소용없다. */
    LINK_NOT_FOUND("링크의 글을 찾을 수 없습니다.", ErrorCategory.INTERNAL),

    /** 권한이 있어야 보거나(401·403) 그 밖의 이유로 받을 수 없게 거절했다(다른 4xx). 다시 해도 소용없다. */
    LINK_BLOCKED("링크의 글을 볼 수 없습니다.", ErrorCategory.INTERNAL),

    /** 사이트의 호출 한도에 걸렸다(429). 잠시 뒤 다시 하면 될 수 있다. */
    LINK_RATE_LIMITED("요청이 많아 링크를 잠시 받을 수 없습니다. 잠시 후 다시 시도해 주세요.", ErrorCategory.INTERNAL),

    /** 그 밖에 받지 못했다(5xx, 시간 초과, 연결 실패). 잠시 뒤 다시 하면 될 수 있다. */
    LINK_UNAVAILABLE("링크에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.", ErrorCategory.INTERNAL),
}
