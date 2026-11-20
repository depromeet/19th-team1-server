package com.depromeet.team1.infra.storage.postgresql.chat

import com.depromeet.team1.core.chat.MessageRole
import com.depromeet.team1.core.chat.MessageStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "message")
class MessageJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long?,
    val conversationId: Long,
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val role: MessageRole,
    @Column(columnDefinition = "text")
    val content: String,
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val status: MessageStatus,
    val createdAt: Instant,
)
