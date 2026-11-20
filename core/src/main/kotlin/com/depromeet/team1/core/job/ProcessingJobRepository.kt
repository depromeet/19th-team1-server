package com.depromeet.team1.core.job

import java.time.Instant

/**
 * 처리 작업 저장소. 상태를 바꾸는 메서드는 WHERE 조건을 붙인 UPDATE 이고 갱신한 행 수를 돌려준다.
 * 0 이면 그 동작을 하지 않는다(이미 다른 쪽이 처리했거나 늦은 갱신이다).
 */
interface ProcessingJobRepository {
    /**
     * 전이 1(없음 → PENDING). 같은 저장물에 진행 중(PENDING·QUEUED·RUNNING)인 ANALYZE 작업이 있으면
     * 만들지 않고 null 을 돌려준다. 동시에 요청해도 DB 유일 인덱스가 하나만 통과시킨다. EMBED 작업은 항상 만든다.
     */
    fun create(job: ProcessingJob): ProcessingJob?

    fun findById(id: Long): ProcessingJob?

    /** 전이 2(PENDING → QUEUED). 발행자가 메시지를 보낸 뒤 호출한다. */
    fun markQueued(id: Long, now: Instant): Int

    /**
     * 전이 3(PENDING·QUEUED → RUNNING). [staleBefore] 보다 오래 RUNNING 인 작업(가시성 제한 시간이 지난 것)도 다시 가져간다.
     * 시도 횟수를 1 올리고, 올린 값(이 워커의 차례 번호)을 돌려준다. 가져가지 못하면 null.
     */
    fun start(id: Long, staleBefore: Instant, now: Instant): Int?

    /** 전이 4(RUNNING → DONE). [attempt] 가 차례 번호와 같을 때만. */
    fun complete(id: Long, attempt: Int, now: Instant): Int

    /** 전이 5(RUNNING → QUEUED). 재시도할 수 있는 실패. 메시지를 지우지 않아 SQS 가 다시 보낸다. */
    fun requeue(id: Long, attempt: Int, errorCode: String, errorDetail: String?, now: Instant): Int

    /** 전이 6(RUNNING → FAILED). 재시도할 수 없는 실패이거나 시도 횟수 상한에 닿았다. */
    fun fail(id: Long, attempt: Int, errorCode: String, errorDetail: String?, now: Instant): Int

    /** 전이 7(끝나지 않은 작업 → FAILED). DLQ 로 온 메시지를 처리한다. */
    fun failUnfinished(id: Long, errorCode: String, errorDetail: String?, now: Instant): Int

    /**
     * 전이 8(재발행 대상). [queuedBefore] 전부터 PENDING·QUEUED 그대로이거나, [runningBefore] 전부터 RUNNING 그대로인 작업.
     * 상태는 바꾸지 않는다. 오래된 순서로 [limit] 개까지.
     */
    fun findStale(queuedBefore: Instant, runningBefore: Instant, limit: Int): List<ProcessingJob>
}
