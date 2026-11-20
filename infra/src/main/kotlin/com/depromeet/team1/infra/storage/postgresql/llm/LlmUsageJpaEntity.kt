package com.depromeet.team1.infra.storage.postgresql.llm

import com.depromeet.team1.core.llm.LlmPurpose
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
@Table(name = "llm_usage")
class LlmUsageJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long?,
    val memberId: Long,
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val purpose: LlmPurpose,
    @Column(length = 50)
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val processingJobId: Long?,
    val messageId: Long?,
    val createdAt: Instant,
)
