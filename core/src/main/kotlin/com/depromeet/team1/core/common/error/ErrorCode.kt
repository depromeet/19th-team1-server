package com.depromeet.team1.core.common.error

/**
 * 에러 코드 계약. 기능마다 enum 으로 구현해 해당 기능 패키지에 둔다 (예: core/item/ItemErrorCode).
 *
 * - name: 응답의 code 값. 기능 간 겹치지 않게 기능 이름으로 시작한다 (예: ITEM_NOT_FOUND). enum 이면 자동으로 채워진다.
 * - message: 응답의 message 값. 사용자에게 그대로 보여 줄 수 있는 문장으로 쓴다.
 */
interface ErrorCode {
    val name: String
    val message: String
    val type: ErrorType
}
