package com.depromeet.team1.infra.storage.postgresql.job

import com.depromeet.team1.core.job.JobStatus
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * 처리 작업의 생성과 상태 전이. 전이 번호는 설계 문서의 "SQS 처리 작업 규칙" 표를 따른다.
 * 모든 UPDATE 에 지금 상태 조건을 붙여, 늦은 갱신이나 중복 메시지가 상태를 덮지 않게 한다.
 */
interface ProcessingJobJpaRepository : JpaRepository<ProcessingJobJpaEntity, Long> {
    /**
     * 전이 1. 진행 중인 ANALYZE 작업이 있으면 부분 유일 인덱스(uk_processing_job_active_analyze)에 걸려 삽입하지 않고 null.
     * EMBED 는 그 인덱스 대상이 아니라 항상 삽입된다.
     */
    @Query(
        value = """
            insert into processing_job (saved_item_id, job_type, reason, status, attempt_count, status_changed_at, created_at)
            values (:savedItemId, :jobType, :reason, 'PENDING', 0, :now, :now)
            on conflict (saved_item_id) where job_type = 'ANALYZE' and status in ('PENDING', 'QUEUED', 'RUNNING')
            do nothing
            returning id
        """,
        nativeQuery = true,
    )
    fun insertIfNoActiveAnalyze(
        @Param("savedItemId") savedItemId: Long,
        @Param("jobType") jobType: String,
        @Param("reason") reason: String,
        @Param("now") now: Instant,
    ): Long?

    /** 전이 2 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update ProcessingJobJpaEntity j set j.status = 'QUEUED', j.statusChangedAt = :now
        where j.id = :id and j.status = 'PENDING'
        """,
    )
    fun markQueued(@Param("id") id: Long, @Param("now") now: Instant): Int

    /** 전이 3. 올린 시도 횟수를 돌려준다. 가져가지 못하면 null. */
    @Query(
        value = """
            update processing_job
            set status = 'RUNNING', attempt_count = attempt_count + 1, status_changed_at = :now
            where id = :id
              and (status in ('PENDING', 'QUEUED') or (status = 'RUNNING' and status_changed_at < :staleBefore))
            returning attempt_count
        """,
        nativeQuery = true,
    )
    fun start(@Param("id") id: Long, @Param("staleBefore") staleBefore: Instant, @Param("now") now: Instant): Int?

    /** 전이 4 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update ProcessingJobJpaEntity j set j.status = 'DONE', j.statusChangedAt = :now, j.completedAt = :now
        where j.id = :id and j.status = 'RUNNING' and j.attemptCount = :attempt
        """,
    )
    fun complete(@Param("id") id: Long, @Param("attempt") attempt: Int, @Param("now") now: Instant): Int

    /** 전이 5(QUEUED), 6(FAILED). 차례 번호가 맞는 RUNNING 작업만. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update ProcessingJobJpaEntity j set
            j.status = :status, j.lastErrorCode = :errorCode, j.lastErrorDetail = :errorDetail,
            j.statusChangedAt = :now, j.completedAt = :completedAt
        where j.id = :id and j.status = 'RUNNING' and j.attemptCount = :attempt
        """,
    )
    fun finishAttemptWithError(
        @Param("id") id: Long,
        @Param("attempt") attempt: Int,
        @Param("status") status: JobStatus,
        @Param("errorCode") errorCode: String,
        @Param("errorDetail") errorDetail: String?,
        @Param("completedAt") completedAt: Instant?,
        @Param("now") now: Instant,
    ): Int

    /** 전이 7 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update ProcessingJobJpaEntity j set
            j.status = 'FAILED', j.lastErrorCode = :errorCode, j.lastErrorDetail = :errorDetail,
            j.statusChangedAt = :now, j.completedAt = :now
        where j.id = :id and j.status not in ('DONE', 'FAILED')
        """,
    )
    fun failUnfinished(
        @Param("id") id: Long,
        @Param("errorCode") errorCode: String,
        @Param("errorDetail") errorDetail: String?,
        @Param("now") now: Instant,
    ): Int

    /** 전이 8 */
    @Query(
        """
        select j from ProcessingJobJpaEntity j
        where (j.status in ('PENDING', 'QUEUED') and j.statusChangedAt < :queuedBefore)
           or (j.status = 'RUNNING' and j.statusChangedAt < :runningBefore)
        order by j.statusChangedAt
        """,
    )
    fun findStale(
        @Param("queuedBefore") queuedBefore: Instant,
        @Param("runningBefore") runningBefore: Instant,
        limit: Limit,
    ): List<ProcessingJobJpaEntity>
}
