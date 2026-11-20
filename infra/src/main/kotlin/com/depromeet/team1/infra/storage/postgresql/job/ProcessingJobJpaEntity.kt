package com.depromeet.team1.infra.storage.postgresql.job

import com.depromeet.team1.core.job.JobReason
import com.depromeet.team1.core.job.JobStatus
import com.depromeet.team1.core.job.JobType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** 생성과 상태 전이는 [ProcessingJobJpaRepository] 의 조건부 쿼리로만 한다. */
@Entity
@Table(name = "processing_job")
class ProcessingJobJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long?,
    val savedItemId: Long,
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val jobType: JobType,
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val reason: JobReason,
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val status: JobStatus,
    val attemptCount: Int,
    @Column(length = 30)
    val lastErrorCode: String?,
    @Column(length = 500)
    val lastErrorDetail: String?,
    val statusChangedAt: Instant,
    val createdAt: Instant,
    val completedAt: Instant?,
)
