package com.depromeet.team1.infra.storage.postgresql.chat

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable

/** PK 가 (message_id, saved_item_id) 인 복합 키 테이블이다. */
@Entity
@Table(name = "message_saved_item")
@IdClass(MessageSavedItemId::class)
class MessageSavedItemJpaEntity(
    @Id
    val messageId: Long,
    @Id
    val savedItemId: Long,
    val sortOrder: Short,
)

data class MessageSavedItemId(val messageId: Long = 0, val savedItemId: Long = 0) : Serializable
