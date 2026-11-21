package com.depromeet.team1.worker.job

import com.depromeet.team1.core.job.JobQueue
import com.depromeet.team1.core.job.ProcessingJobService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * 작업 큐에서 메시지를 받아 [ProcessingJobService.process] 에 넘긴다. 동시에 처리하는 수는 [concurrency] 를 넘지 않는다.
 * 처리 칸이 빈 만큼만 받으므로, 받은 메시지가 처리를 기다리며 가시성 제한 시간을 써 버리지 않는다.
 */
class JobReceiver(
    private val jobQueue: JobQueue,
    private val service: ProcessingJobService,
    concurrency: Int,
    private val executor: ExecutorService,
) {
    init {
        require(concurrency >= 1) { "동시 처리 수는 1 이상이다" }
    }

    private val slots = Semaphore(concurrency)

    /** 처리 칸이 하나라도 빌 때까지 기다린다. */
    fun awaitFreeSlot() {
        slots.acquire()
        slots.release()
    }

    /** 빈 처리 칸 수만큼(최대 [JobQueue.MAX_RECEIVE]) 받아 처리를 시작한다. 받은 수를 돌려준다. 빈 칸이 없으면 받지 않는다. */
    fun pollOnce(): Int {
        val taken = takeFreeSlots()
        if (taken == 0) return 0
        val messages = try {
            jobQueue.receive(taken)
        } catch (e: Exception) {
            slots.release(taken)
            throw e
        }
        slots.release(taken - messages.size)
        messages.forEach { message ->
            executor.execute {
                try {
                    service.process(message)
                } catch (e: Exception) {
                    // 메시지를 지우지 않았으므로 가시성 제한 시간 뒤 다시 온다. 예외 메시지에 사용자 데이터가 있을 수 있어 종류만 남긴다.
                    log.warn("작업 처리 중 오류: jobId={}, cause={}", message.jobId, e.javaClass.simpleName)
                } finally {
                    slots.release()
                }
            }
        }
        return messages.size
    }

    /** 새 처리를 받지 않고, 진행 중인 처리를 [wait] 만큼 기다린 뒤 멈춘다. 끝나지 못한 작업은 큐가 다시 보낸다. */
    fun shutdown(wait: Duration) {
        executor.shutdown()
        if (!executor.awaitTermination(wait.toMillis(), TimeUnit.MILLISECONDS)) executor.shutdownNow()
    }

    private fun takeFreeSlots(): Int {
        var taken = 0
        while (taken < JobQueue.MAX_RECEIVE && slots.tryAcquire()) taken++
        return taken
    }

    companion object {
        private val log = LoggerFactory.getLogger(JobReceiver::class.java)
    }
}
