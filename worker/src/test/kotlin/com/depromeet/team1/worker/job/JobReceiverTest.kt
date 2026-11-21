package com.depromeet.team1.worker.job

import com.depromeet.team1.core.common.FixedClock
import com.depromeet.team1.core.common.awaitUntil
import com.depromeet.team1.core.job.FakeJobQueue
import com.depromeet.team1.core.job.FakeProcessingJobRepository
import com.depromeet.team1.core.job.JobHandler
import com.depromeet.team1.core.job.JobReason
import com.depromeet.team1.core.job.JobResult
import com.depromeet.team1.core.job.JobType
import com.depromeet.team1.core.job.ProcessingJob
import com.depromeet.team1.core.job.processingJobService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals

class JobReceiverTest {
    private val clock = FixedClock(Instant.parse("2026-11-21T03:00:00Z"))
    private val jobRepository = FakeProcessingJobRepository()
    private val jobQueue = FakeJobQueue()
    private val handler = BlockingHandler()
    private val service = processingJobService(jobRepository, jobQueue, listOf(handler), clock)
    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    @AfterEach
    fun tearDown() {
        handler.release()
        executor.shutdownNow()
    }

    @Test
    fun `비어 있는 처리 칸 수만큼만 받는다`() {
        publishJobs(5)
        val receiver = JobReceiver(jobQueue, service, concurrency = 2, executor)

        val received = receiver.pollOnce()

        assertEquals(2, received)
        assertEquals(listOf(2), jobQueue.receiveRequests)
    }

    @Test
    fun `처리 칸이 모두 차 있으면 받지 않는다`() {
        publishJobs(5)
        val receiver = JobReceiver(jobQueue, service, concurrency = 2, executor)
        receiver.pollOnce()

        val received = receiver.pollOnce()

        assertEquals(0, received)
        assertEquals(listOf(2), jobQueue.receiveRequests)
    }

    @Test
    fun `받은 메시지가 처리 칸보다 적으면 남은 칸에 다시 받는다`() {
        publishJobs(1)
        val receiver = JobReceiver(jobQueue, service, concurrency = 3, executor)
        receiver.pollOnce()

        receiver.pollOnce()

        assertEquals(listOf(3, 2), jobQueue.receiveRequests)
    }

    @Test
    fun `처리가 끝나면 그 칸에 다시 받는다`() {
        publishJobs(5)
        val receiver = JobReceiver(jobQueue, service, concurrency = 2, executor)
        receiver.pollOnce()
        handler.release()
        awaitUntil { jobQueue.deleted.size == 2 }

        val received = receiver.pollOnce()

        assertEquals(2, received)
    }

    @Test
    fun `한 번에 10개보다 많이 받지 않는다`() {
        publishJobs(20)
        val receiver = JobReceiver(jobQueue, service, concurrency = 20, executor)

        receiver.pollOnce()

        assertEquals(listOf(10), jobQueue.receiveRequests)
    }

    private fun publishJobs(count: Int) = repeat(count) {
        val job = jobRepository.create(
            ProcessingJob.create(it + 1L, JobType.ANALYZE, JobReason.INITIAL, clock.instant()),
        )!!
        service.publish(job.id!!)
    }

    /** 놓아줄 때까지 수행을 붙잡아 처리 칸을 차지하게 한다. */
    private class BlockingHandler : JobHandler {
        private val latch = CountDownLatch(1)
        override val jobType = JobType.ANALYZE

        fun release() = latch.countDown()

        override fun handle(job: ProcessingJob): JobResult {
            latch.await()
            return JobResult.NOTHING
        }
    }
}
