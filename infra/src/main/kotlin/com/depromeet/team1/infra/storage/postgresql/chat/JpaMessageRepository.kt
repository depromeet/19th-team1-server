package com.depromeet.team1.infra.storage.postgresql.chat

import com.depromeet.team1.core.chat.Message
import com.depromeet.team1.core.chat.MessageRepository
import com.depromeet.team1.core.chat.MessageSavedItem
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class JpaMessageRepository(
    private val messageJpaRepository: MessageJpaRepository,
    private val messageSavedItemJpaRepository: MessageSavedItemJpaRepository,
) : MessageRepository {
    @Transactional
    override fun save(message: Message): Message = messageJpaRepository.save(message.toEntity()).toDomain()

    override fun findById(id: Long): Message? = messageJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByConversationId(conversationId: Long): List<Message> =
        messageJpaRepository.findByConversationIdOrderById(conversationId).map { it.toDomain() }

    @Transactional
    override fun saveSavedItems(savedItems: List<MessageSavedItem>): List<MessageSavedItem> =
        messageSavedItemJpaRepository.saveAll(savedItems.map { it.toEntity() }).map { it.toDomain() }

    override fun findSavedItems(messageId: Long): List<MessageSavedItem> =
        messageSavedItemJpaRepository.findByMessageIdOrderBySortOrder(messageId).map { it.toDomain() }
}
