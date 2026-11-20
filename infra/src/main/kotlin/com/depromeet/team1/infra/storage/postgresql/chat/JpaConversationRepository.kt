package com.depromeet.team1.infra.storage.postgresql.chat

import com.depromeet.team1.core.chat.Conversation
import com.depromeet.team1.core.chat.ConversationRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class JpaConversationRepository(private val conversationJpaRepository: ConversationJpaRepository) :
    ConversationRepository {
    @Transactional
    override fun save(conversation: Conversation): Conversation =
        conversationJpaRepository.save(conversation.toEntity()).toDomain()

    override fun findById(id: Long): Conversation? = conversationJpaRepository.findById(id).orElse(null)?.toDomain()
}
