package com.depromeet.team1.core.item

import java.time.Instant

/** 저장물의 주제어 하나. [sortOrder] 는 LLM 이 준 순서다. */
data class SavedItemKeyword(val savedItemId: Long, val keyword: String, val sortOrder: Int, val createdAt: Instant) {
    init {
        require(keyword.isNotBlank() && keyword.length <= KEYWORD_MAX) { "주제어는 1~${KEYWORD_MAX}자다" }
        require(sortOrder >= 0) { "순서는 0 이상이다" }
    }

    companion object {
        const val KEYWORD_MAX = 50
    }
}
