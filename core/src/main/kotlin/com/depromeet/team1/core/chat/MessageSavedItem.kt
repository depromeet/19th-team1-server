package com.depromeet.team1.core.chat

/** 답변이 찾아 준 저장물 하나. [sortOrder] 는 관련도순이다. */
data class MessageSavedItem(val messageId: Long, val savedItemId: Long, val sortOrder: Int) {
    init {
        require(sortOrder >= 0) { "순서는 0 이상이다" }
    }
}
