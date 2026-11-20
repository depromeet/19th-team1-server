package com.depromeet.team1.core.job

import java.time.Instant

/**
 * 저장물 처리 작업 한 건. SQS 메시지는 이 ID 만 나르고, 상태·시도 횟수·실패 기록은 여기에 둔다.
 *
 * 상태는 [ProcessingJobRepository] 의 조건부 UPDATE 로만 바꾼다. 늦은 갱신이나 중복 메시지가 상태를 덮지 않게 하려는 것이다.
 */
data class ProcessingJob(
    val id: Long?,
    val savedItemId: Long,
    val jobType: JobType,
    val reason: JobReason,
    val status: JobStatus,
    /** 워커가 실행을 시작한 횟수. 실패 판정의 기준이다. */
    val attemptCount: Int,
    val lastErrorCode: String?,
    val lastErrorDetail: String?,
    val statusChangedAt: Instant,
    val createdAt: Instant,
    val completedAt: Instant?,
) {
    init {
        require(attemptCount >= 0) { "시도 횟수는 0 이상이다" }
        require(lastErrorCode == null || lastErrorCode.length <= ERROR_CODE_MAX) {
            "오류 코드는 ${ERROR_CODE_MAX}자 이하다"
        }
        require(lastErrorDetail == null || lastErrorDetail.length <= ERROR_DETAIL_MAX) {
            "오류 내용은 ${ERROR_DETAIL_MAX}자 이하다"
        }
    }

    companion object {
        const val ERROR_CODE_MAX = 30
        const val ERROR_DETAIL_MAX = 500

        fun create(savedItemId: Long, jobType: JobType, reason: JobReason, now: Instant): ProcessingJob = ProcessingJob(
            id = null,
            savedItemId = savedItemId,
            jobType = jobType,
            reason = reason,
            status = JobStatus.PENDING,
            attemptCount = 0,
            lastErrorCode = null,
            lastErrorDetail = null,
            statusChangedAt = now,
            createdAt = now,
            completedAt = null,
        )
    }
}

enum class JobType {
    /** 링크 수집 + LLM 분석 */
    ANALYZE,

    /** 임베딩 생성 */
    EMBED,
}

enum class JobReason { INITIAL, CATEGORY_CHANGED, RETRY }

enum class JobStatus { PENDING, QUEUED, RUNNING, DONE, FAILED }
