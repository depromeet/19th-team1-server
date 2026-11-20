package com.depromeet.team1.core.chat

interface MessageRepository {
    fun save(message: Message): Message

    fun findById(id: Long): Message?

    /** 대화의 메시지를 id 순서(오래된 것부터)로 돌려준다. */
    fun findByConversationId(conversationId: Long): List<Message>

    fun saveSavedItems(savedItems: List<MessageSavedItem>): List<MessageSavedItem>

    /** 답변이 찾아 준 저장물을 [MessageSavedItem.sortOrder] 순서로 돌려준다. */
    fun findSavedItems(messageId: Long): List<MessageSavedItem>
}
