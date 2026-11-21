package com.depromeet.team1.core.job

/**
 * 처리 작업을 워커에 알리는 큐. 메시지에는 작업 ID 만 싣는다. 상태·시도 횟수는 [ProcessingJobRepository] 가 기준이다.
 *
 * 같은 메시지가 두 번 올 수 있다. 실행을 잡는 조건부 UPDATE([ProcessingJobRepository.start])가 하나만 통과시킨다.
 */
interface JobQueue {
    fun publish(jobId: Long)

    /** 최대 [maxMessages] 개(1~[MAX_RECEIVE])를 받는다. 받을 것이 없으면 빈 목록. */
    fun receive(maxMessages: Int): List<JobMessage>

    /** 처리가 끝난 메시지를 지운다. 지우지 않은 메시지는 가시성 제한 시간 뒤 다시 온다. */
    fun delete(message: JobMessage)

    companion object {
        /** 한 번에 받을 수 있는 최대 수. SQS 의 상한이다. */
        const val MAX_RECEIVE = 10
    }
}

/** 받은 메시지 한 건. [receipt] 는 메시지를 지울 때 큐에 돌려주는 값이다. */
data class JobMessage(val jobId: Long, val receipt: String)

/** 큐 호출 실패. 구현이 큐 서비스의 오류를 이 예외로 바꿔 던진다. */
class JobQueueException(message: String, cause: Throwable) : RuntimeException(message, cause)
