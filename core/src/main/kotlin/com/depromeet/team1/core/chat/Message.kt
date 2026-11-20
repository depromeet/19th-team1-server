package com.depromeet.team1.core.chat

import java.time.Instant

/** 대화의 질문 또는 답변. 순서는 id 순서다. */
data class Message(
    val id: Long?,
    val conversationId: Long,
    val role: MessageRole,
    val content: String,
    val status: MessageStatus,
    val createdAt: Instant,
) {
    init {
        if (role == MessageRole.USER) {
            require(status == MessageStatus.DONE) { "사용자 메시지는 항상 DONE 이다" }
            require(content.isNotBlank()) { "질문은 비어 있을 수 없다" }
        }
    }
}

enum class MessageRole { USER, ASSISTANT }

enum class MessageStatus { GENERATING, DONE, FAILED }
