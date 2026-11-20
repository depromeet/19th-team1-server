package com.depromeet.team1.infra.storage.postgresql.llm

import com.depromeet.team1.core.llm.LlmUsage

internal fun LlmUsage.toEntity() = LlmUsageJpaEntity(
    id = id,
    memberId = memberId,
    purpose = purpose,
    model = model,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    processingJobId = processingJobId,
    messageId = messageId,
    createdAt = createdAt,
)

internal fun LlmUsageJpaEntity.toDomain() = LlmUsage(
    id = id,
    memberId = memberId,
    purpose = purpose,
    model = model,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    processingJobId = processingJobId,
    messageId = messageId,
    createdAt = createdAt,
)
