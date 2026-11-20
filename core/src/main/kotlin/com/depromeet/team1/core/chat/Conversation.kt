package com.depromeet.team1.core.chat

import java.time.Instant

/** 꺼내 쓰기 대화. 모델은 회원당 여러 개를 허용하고, 프리런칭 운영은 회원당 1개다. */
data class Conversation(
    val id: Long?,
    val memberId: Long,
    /** 서버가 만들어 보관한다. 화면에는 노출하지 않는다. */
    val title: String?,
    val createdAt: Instant,
    val deletedAt: Instant?,
) {
    init {
        require(title == null || title.length <= TITLE_MAX) { "제목은 ${TITLE_MAX}자 이하다" }
    }

    companion object {
        const val TITLE_MAX = 100

        fun create(memberId: Long, now: Instant): Conversation = Conversation(
            id = null,
            memberId = memberId,
            title = null,
            createdAt = now,
            deletedAt = null,
        )
    }
}
