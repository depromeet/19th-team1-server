package com.depromeet.team1.core.chat

interface ConversationRepository {
    fun save(conversation: Conversation): Conversation

    fun findById(id: Long): Conversation?
}
