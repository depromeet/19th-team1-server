package com.depromeet.team1.core.job

import java.time.Instant

/**
 * [ProcessingJobRepository] 의 가짜 구현. 실제 구현(JpaProcessingJobRepository)의 조건부 UPDATE 와 같은 조건으로 상태를 바꾼다.
 * 워커 테스트에서 여러 스레드가 함께 쓰므로 메서드를 동기화한다.
 */
class FakeProcessingJobRepository : ProcessingJobRepository {
    private val jobs = linkedMapOf<Long, ProcessingJob>()
    private var nextId = 1L

    @Synchronized
    override fun create(job: ProcessingJob): ProcessingJob? {
        require(job.id == null && job.status == JobStatus.PENDING && job.attemptCount == 0) { "새 작업만 만든다" }
        val hasActiveAnalyze = job.jobType == JobType.ANALYZE &&
            jobs.values.any {
                it.savedItemId == job.savedItemId && it.jobType == JobType.ANALYZE && it.status in ACTIVE
            }
        if (hasActiveAnalyze) return null
        val saved = job.copy(id = nextId++)
        jobs[saved.id!!] = saved
        return saved
    }

    @Synchronized
    override fun findById(id: Long): ProcessingJob? = jobs[id]

    @Synchronized
    override fun markQueued(id: Long, now: Instant): Int = update(id, { it.status == JobStatus.PENDING }) {
        it.copy(status = JobStatus.QUEUED, statusChangedAt = now)
    }

    @Synchronized
    override fun start(id: Long, staleBefore: Instant, now: Instant): Int? {
        val updated = update(id, {
            it.status == JobStatus.PENDING ||
                it.status == JobStatus.QUEUED ||
                (it.status == JobStatus.RUNNING && it.statusChangedAt.isBefore(staleBefore))
        }) { it.copy(status = JobStatus.RUNNING, attemptCount = it.attemptCount + 1, statusChangedAt = now) }
        return if (updated == 0) null else jobs.getValue(id).attemptCount
    }

    @Synchronized
    override fun complete(id: Long, attempt: Int, now: Instant): Int = update(id, { it.isRunningAttempt(attempt) }) {
        it.copy(status = JobStatus.DONE, statusChangedAt = now, completedAt = now)
    }

    @Synchronized
    override fun requeue(id: Long, attempt: Int, errorCode: String, errorDetail: String?, now: Instant): Int =
        update(id, { it.isRunningAttempt(attempt) }) {
            it.copy(
                status = JobStatus.QUEUED,
                lastErrorCode = errorCode,
                lastErrorDetail = errorDetail,
                statusChangedAt = now,
                completedAt = null,
            )
        }

    @Synchronized
    override fun fail(id: Long, attempt: Int, errorCode: String, errorDetail: String?, now: Instant): Int =
        update(id, { it.isRunningAttempt(attempt) }) {
            it.copy(
                status = JobStatus.FAILED,
                lastErrorCode = errorCode,
                lastErrorDetail = errorDetail,
                statusChangedAt = now,
                completedAt = now,
            )
        }

    @Synchronized
    override fun failUnfinished(id: Long, errorCode: String, errorDetail: String?, now: Instant): Int =
        update(id, { it.status in ACTIVE }) {
            it.copy(
                status = JobStatus.FAILED,
                lastErrorCode = errorCode,
                lastErrorDetail = errorDetail,
                statusChangedAt = now,
                completedAt = now,
            )
        }

    @Synchronized
    override fun findStale(queuedBefore: Instant, runningBefore: Instant, limit: Int): List<ProcessingJob> = jobs.values
        .filter {
            (it.status in WAITING && it.statusChangedAt.isBefore(queuedBefore)) ||
                (it.status == JobStatus.RUNNING && it.statusChangedAt.isBefore(runningBefore))
        }
        .sortedBy { it.statusChangedAt }
        .take(limit)

    private fun ProcessingJob.isRunningAttempt(attempt: Int) = status == JobStatus.RUNNING && attemptCount == attempt

    private fun update(id: Long, condition: (ProcessingJob) -> Boolean, change: (ProcessingJob) -> ProcessingJob): Int {
        val job = jobs[id] ?: return 0
        if (!condition(job)) return 0
        jobs[id] = change(job)
        return 1
    }

    companion object {
        private val WAITING = setOf(JobStatus.PENDING, JobStatus.QUEUED)
        private val ACTIVE = WAITING + JobStatus.RUNNING
    }
}
