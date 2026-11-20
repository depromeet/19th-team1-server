package com.depromeet.team1.infra.storage.postgresql.chat

import org.springframework.data.jpa.repository.JpaRepository

interface ConversationJpaRepository : JpaRepository<ConversationJpaEntity, Long>

interface MessageJpaRepository : JpaRepository<MessageJpaEntity, Long> {
    fun findByConversationIdOrderById(conversationId: Long): List<MessageJpaEntity>
}

interface MessageSavedItemJpaRepository : JpaRepository<MessageSavedItemJpaEntity, MessageSavedItemId> {
    fun findByMessageIdOrderBySortOrder(messageId: Long): List<MessageSavedItemJpaEntity>
}
