package com.depromeet.team1.core.common.error

/**
 * 업무 규칙 위반을 나타내는 예외. 응답에는 [errorCode] 의 문구만 나가고, [detail] 은 로그에만 남는다.
 */
open class BusinessException(
    val errorCode: ErrorCode,
    val detail: String? = null,
    cause: Throwable? = null,
) : RuntimeException(detail ?: errorCode.message, cause)
