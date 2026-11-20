package com.depromeet.team1.infra.storage.postgresql.chat

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "conversation")
class ConversationJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long?,
    val memberId: Long,
    @Column(length = 100)
    val title: String?,
    val createdAt: Instant,
    val deletedAt: Instant?,
)
