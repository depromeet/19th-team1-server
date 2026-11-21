package com.depromeet.team1.core.job

import java.time.Duration

/**
 * 처리 작업 설정. 값은 실행 모듈이 환경변수로 채운다(`docs/deployment.md` 6절).
 *
 * @property maxAttempts DB 시도 횟수 상한. 이 횟수째 실행이 실패하면 재시도할 수 있는 실패라도 FAILED 로 끝낸다
 * @property runningTimeout 이보다 오래 RUNNING 인 작업은 멈춘 것으로 본다. 큐의 가시성 제한 시간과 맞춘다
 * @property republishAfter 이보다 오래 PENDING·QUEUED 인 작업은 발행이 빠졌거나 메시지를 잃은 것으로 보고 다시 발행한다
 * @property republishBatchSize 재발행 한 번에 다루는 작업 수
 */
data class ProcessingJobSettings(
    val maxAttempts: Int,
    val runningTimeout: Duration,
    val republishAfter: Duration,
    val republishBatchSize: Int,
) {
    init {
        require(maxAttempts >= 1) { "시도 횟수 상한은 1 이상이다" }
        require(!runningTimeout.isNegative && !runningTimeout.isZero) { "실행 제한 시간은 0 보다 길다" }
        require(!republishAfter.isNegative && !republishAfter.isZero) { "재발행 대기 시간은 0 보다 길다" }
        require(republishBatchSize >= 1) { "재발행 묶음 크기는 1 이상이다" }
    }
}
