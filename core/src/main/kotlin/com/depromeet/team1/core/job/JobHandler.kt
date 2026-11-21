package com.depromeet.team1.core.job

/**
 * 작업 종류별 수행. [ProcessingJobService] 가 실행을 잡은 뒤 종류에 맞는 구현을 부른다.
 *
 * [handle] 은 트랜잭션 밖에서 불린다. 외부 호출(링크 수집, LLM, 임베딩)은 여기서 하고, DB 에 쓰지 않는다.
 * 저장할 것은 [JobResult] 로 돌려준다. 서비스가 작업 완료와 같은 트랜잭션에서 [JobResult.save] 를 부른다.
 *
 * 실패는 [JobFailure] 로 던진다. 다른 예외는 재시도할 수 있는 실패로 본다.
 */
interface JobHandler {
    val jobType: JobType

    fun handle(job: ProcessingJob): JobResult
}

/** 작업 결과 저장. 작업 완료(RUNNING → DONE)와 한 트랜잭션에서 불린다. 차례가 지난 실행이면 불리지 않는다. */
fun interface JobResult {
    fun save()

    companion object {
        val NOTHING = JobResult { }
    }
}

/**
 * 작업 실패. [retryable] 이 false 면 다시 해도 소용없는 실패(열리지 않는 링크, 사용량 상한 등)로 바로 FAILED 가 된다.
 * [errorCode] 는 [ProcessingJob.ERROR_CODE_MAX] 자 이하로, 로그와 작업 기록에 남는다. [errorCode] 와 detail 에는
 * 사용자 데이터(URL, 저장 내용, LLM 응답)를 넣지 않는다. detail 은 작업 기록(last_error_detail)에 남는다.
 */
class JobFailure(val errorCode: String, val retryable: Boolean, detail: String? = null, cause: Throwable? = null) :
    RuntimeException(detail ?: errorCode, cause) {
    init {
        require(errorCode.isNotBlank() && errorCode.length <= ProcessingJob.ERROR_CODE_MAX) {
            "오류 코드는 1~${ProcessingJob.ERROR_CODE_MAX}자다"
        }
    }
}
