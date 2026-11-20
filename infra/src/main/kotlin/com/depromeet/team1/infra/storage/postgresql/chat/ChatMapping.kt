package com.depromeet.team1.infra.storage.postgresql.chat

import com.depromeet.team1.core.chat.Conversation
import com.depromeet.team1.core.chat.Message
import com.depromeet.team1.core.chat.MessageSavedItem

internal fun Conversation.toEntity() = ConversationJpaEntity(
    id = id,
    memberId = memberId,
    title = title,
    createdAt = createdAt,
    deletedAt = deletedAt,
)

internal fun ConversationJpaEntity.toDomain() = Conversation(
    id = id,
    memberId = memberId,
    title = title,
    createdAt = createdAt,
    deletedAt = deletedAt,
)

internal fun Message.toEntity() = MessageJpaEntity(
    id = id,
    conversationId = conversationId,
    role = role,
    content = content,
    status = status,
    createdAt = createdAt,
)

internal fun MessageJpaEntity.toDomain() = Message(
    id = id,
    conversationId = conversationId,
    role = role,
    content = content,
    status = status,
    createdAt = createdAt,
)

internal fun MessageSavedItem.toEntity() = MessageSavedItemJpaEntity(
    messageId = messageId,
    savedItemId = savedItemId,
    sortOrder = sortOrder.toShort(),
)

internal fun MessageSavedItemJpaEntity.toDomain() = MessageSavedItem(
    messageId = messageId,
    savedItemId = savedItemId,
    sortOrder = sortOrder.toInt(),
)
