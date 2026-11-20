package com.depromeet.team1.infra.storage.postgresql.job

import com.depromeet.team1.core.job.ProcessingJob

internal fun ProcessingJobJpaEntity.toDomain() = ProcessingJob(
    id = id,
    savedItemId = savedItemId,
    jobType = jobType,
    reason = reason,
    status = status,
    attemptCount = attemptCount,
    lastErrorCode = lastErrorCode,
    lastErrorDetail = lastErrorDetail,
    statusChangedAt = statusChangedAt,
    createdAt = createdAt,
    completedAt = completedAt,
)
