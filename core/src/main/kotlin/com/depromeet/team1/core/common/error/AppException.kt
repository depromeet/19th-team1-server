package com.depromeet.team1.core.common.error

/**
 * 서비스에서 던지는 예외. 응답에는 [errorCode] 의 문구만 나가고, [detail] 은 로그에만 남는다.
 *
 * 예: `throw AppException(ItemError.ITEM_NOT_FOUND, "itemId=$itemId")`
 */
class AppException(val errorCode: ErrorCode, val detail: String? = null, cause: Throwable? = null) :
    RuntimeException(detail ?: errorCode.message, cause)
