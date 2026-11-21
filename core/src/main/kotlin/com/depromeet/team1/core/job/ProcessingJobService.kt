package com.depromeet.team1.core.job

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock

/**
 * 처리 작업의 발행·수행·재발행. 흐름은 `docs/architecture.md` 8장 "비동기 처리 흐름"이다.
 *
 * 상태는 [ProcessingJobRepository] 의 조건부 UPDATE 로만 바꾼다. 외부 호출([JobHandler.handle])은 트랜잭션 밖에서 하고,
 * 실행 잡기·완료·실패 기록은 각각 따로 커밋된다.
 */
@Service
class ProcessingJobService(
    private val jobRepository: ProcessingJobRepository,
    private val jobQueue: JobQueue,
    handlers: List<JobHandler>,
    private val completion: JobCompletionService,
    private val settings: ProcessingJobSettings,
    private val clock: Clock,
) {
    private val handlers: Map<JobType, JobHandler> = handlers.associateBy { it.jobType }.also {
        require(it.size == handlers.size) { "작업 종류마다 수행 구현은 하나다" }
    }

    /** 커밋된 작업을 발행하고 QUEUED 로 바꾼다. 저장물과 작업을 저장한 트랜잭션이 커밋된 뒤에 부른다. */
    fun publish(jobId: Long) {
        jobQueue.publish(jobId)
        jobRepository.markQueued(jobId, clock.instant())
    }

    /**
     * 받은 메시지 한 건을 처리한다.
     *
     * - 실행을 잡지 못하면(이미 실행 중·완료·실패) 메시지만 지운다.
     * - 성공하면 결과 저장과 DONE 을 한 트랜잭션으로 하고 메시지를 지운다.
     * - 재시도할 수 있는 실패이고 시도 횟수 상한 미만이면 QUEUED 로 돌리고 메시지를 지우지 않는다. 큐가 다시 보낸다.
     * - 그 밖의 실패는 FAILED 로 끝내고 메시지를 지운다.
     */
    fun process(message: JobMessage) {
        val now = clock.instant()
        val attempt = jobRepository.start(message.jobId, now.minus(settings.runningTimeout), now)
        if (attempt == null) {
            jobQueue.delete(message)
            return
        }
        val job = checkNotNull(jobRepository.findById(message.jobId)) { "실행을 잡은 작업이 없다: jobId=${message.jobId}" }

        val failure = try {
            val handler = handlers[job.jobType]
                ?: throw JobFailure(ERROR_HANDLER_MISSING, retryable = false, detail = "jobType=${job.jobType}")
            val result = handler.handle(job)
            if (!completion.complete(job.id!!, attempt, result)) {
                log.info("차례가 지난 실행이라 결과를 버린다: jobId={}, attempt={}", job.id, attempt)
            }
            null
        } catch (e: JobFailure) {
            e
        } catch (e: Exception) {
            JobFailure(ERROR_UNEXPECTED, retryable = true, detail = e.javaClass.simpleName, cause = e)
        }

        if (failure == null) {
            jobQueue.delete(message)
        } else {
            recordFailure(job, attempt, failure, message)
        }
    }

    /** 발행이 빠졌거나 메시지를 잃은 작업, 멈춘 작업을 다시 발행한다. 다시 발행한 작업 수를 돌려준다. */
    fun republishStale(): Int {
        val now = clock.instant()
        val stale = jobRepository.findStale(
            queuedBefore = now.minus(settings.republishAfter),
            runningBefore = now.minus(settings.runningTimeout),
            limit = settings.republishBatchSize,
        )
        stale.forEach { publish(it.id!!) }
        if (stale.isNotEmpty()) log.info("멈춘 작업을 다시 발행했다: count={}", stale.size)
        return stale.size
    }

    private fun recordFailure(job: ProcessingJob, attempt: Int, failure: JobFailure, message: JobMessage) {
        val now = clock.instant()
        val detail = failure.message?.take(ProcessingJob.ERROR_DETAIL_MAX)
        if (failure.retryable && attempt < settings.maxAttempts) {
            jobRepository.requeue(job.id!!, attempt, failure.errorCode, detail, now)
            log.warn("작업 실패, 다시 시도한다: jobId={}, attempt={}, code={}", job.id, attempt, failure.errorCode)
            return
        }
        jobRepository.fail(job.id!!, attempt, failure.errorCode, detail, now)
        jobQueue.delete(message)
        // 예외 메시지에는 URL 같은 사용자 데이터가 들어 있을 수 있어 원인은 종류만 남긴다.
        log.warn(
            "작업 실패로 끝낸다: jobId={}, attempt={}, code={}, cause={}",
            job.id,
            attempt,
            failure.errorCode,
            failure.cause?.javaClass?.simpleName,
        )
    }

    companion object {
        const val ERROR_HANDLER_MISSING = "JOB_HANDLER_MISSING"
        const val ERROR_UNEXPECTED = "JOB_UNEXPECTED_ERROR"

        private val log = LoggerFactory.getLogger(ProcessingJobService::class.java)
    }
}
