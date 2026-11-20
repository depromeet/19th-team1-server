package com.depromeet.team1.core.item

/** 저장물 삽입 결과. 같은 회원이 같은 링크를 이미 저장했으면 새 행을 만들지 않는다. */
sealed interface SavedItemInsertResult {
    val id: Long

    data class Inserted(override val id: Long) : SavedItemInsertResult

    /** 삭제되지 않은 같은 링크가 이미 있다. [id] 는 기존 저장물이다. 처리 작업을 만들지 않는다. */
    data class AlreadySaved(override val id: Long) : SavedItemInsertResult
}
