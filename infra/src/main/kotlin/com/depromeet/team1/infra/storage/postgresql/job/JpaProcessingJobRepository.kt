package com.depromeet.team1.infra.storage.postgresql.job

import com.depromeet.team1.core.job.JobStatus
import com.depromeet.team1.core.job.ProcessingJob
import com.depromeet.team1.core.job.ProcessingJobRepository
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository
class JpaProcessingJobRepository(private val jobJpaRepository: ProcessingJobJpaRepository) : ProcessingJobRepository {
    /** 새 작업(PENDING, 시도 0)만 받는다. */
    @Transactional
    override fun create(job: ProcessingJob): ProcessingJob? {
        require(job.id == null && job.status == JobStatus.PENDING && job.attemptCount == 0) { "새 작업만 만든다" }
        val id = jobJpaRepository.insertIfNoActiveAnalyze(
            savedItemId = job.savedItemId,
            jobType = job.jobType.name,
            reason = job.reason.name,
            now = job.createdAt,
        ) ?: return null
        return job.copy(id = id)
    }

    override fun findById(id: Long): ProcessingJob? = jobJpaRepository.findById(id).orElse(null)?.toDomain()

    @Transactional
    override fun markQueued(id: Long, now: Instant): Int = jobJpaRepository.markQueued(id, now)

    @Transactional
    override fun start(id: Long, staleBefore: Instant, now: Instant): Int? =
        jobJpaRepository.start(id, staleBefore, now)

    @Transactional
    override fun complete(id: Long, attempt: Int, now: Instant): Int = jobJpaRepository.complete(id, attempt, now)

    @Transactional
    override fun requeue(id: Long, attempt: Int, errorCode: String, errorDetail: String?, now: Instant): Int =
        jobJpaRepository.finishAttemptWithError(id, attempt, JobStatus.QUEUED, errorCode, errorDetail, null, now)

    @Transactional
    override fun fail(id: Long, attempt: Int, errorCode: String, errorDetail: String?, now: Instant): Int =
        jobJpaRepository.finishAttemptWithError(id, attempt, JobStatus.FAILED, errorCode, errorDetail, now, now)

    @Transactional
    override fun failUnfinished(id: Long, errorCode: String, errorDetail: String?, now: Instant): Int =
        jobJpaRepository.failUnfinished(id, errorCode, errorDetail, now)

    override fun findStale(queuedBefore: Instant, runningBefore: Instant, limit: Int): List<ProcessingJob> =
        jobJpaRepository.findStale(queuedBefore, runningBefore, Limit.of(limit)).map { it.toDomain() }
}
