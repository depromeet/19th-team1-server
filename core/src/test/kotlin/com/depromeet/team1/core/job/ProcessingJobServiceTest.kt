package com.depromeet.team1.core.job

import com.depromeet.team1.core.common.FixedClock
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessingJobServiceTest {
    private val clock = FixedClock(Instant.parse("2026-11-21T03:00:00Z"))
    private val jobRepository = FakeProcessingJobRepository()
    private val jobQueue = FakeJobQueue()
    private val handler = StubHandler()
    private val settings = ProcessingJobSettings(
        maxAttempts = 3,
        runningTimeout = Duration.ofMinutes(5),
        republishAfter = Duration.ofMinutes(10),
        republishBatchSize = 100,
    )
    private val service = ProcessingJobService(
        jobRepository,
        jobQueue,
        listOf(handler),
        JobCompletionService(jobRepository, clock),
        settings,
        clock,
    )

    @Test
    fun `발행하면 큐에 작업 ID 를 보내고 QUEUED 가 된다`() {
        val job = newJob()

        service.publish(job.id!!)

        assertEquals(listOf(job.id), jobQueue.published)
        assertEquals(JobStatus.QUEUED, statusOf(job))
    }

    @Test
    fun `수행에 성공하면 결과를 저장하고 DONE 이 되며 메시지를 지운다`() {
        val job = publishedJob()

        service.process(jobQueue.receive(1).single())

        assertEquals(JobStatus.DONE, statusOf(job))
        assertEquals(1, handler.saved)
        assertTrue(jobQueue.inFlight.isEmpty())
    }

    @Test
    fun `재시도할 수 있는 실패면 QUEUED 로 돌리고 메시지를 지우지 않는다`() {
        val job = publishedJob()
        handler.failure = JobFailure("LINK_TIMEOUT", retryable = true)

        service.process(jobQueue.receive(1).single())

        val saved = jobRepository.findById(job.id!!)!!
        assertEquals(JobStatus.QUEUED, saved.status)
        assertEquals("LINK_TIMEOUT", saved.lastErrorCode)
        assertEquals(1, jobQueue.inFlight.size)
    }

    @Test
    fun `재시도할 수 없는 실패면 FAILED 로 끝내고 메시지를 지운다`() {
        val job = publishedJob()
        handler.failure = JobFailure("LINK_NOT_FOUND", retryable = false)

        service.process(jobQueue.receive(1).single())

        assertEquals(JobStatus.FAILED, statusOf(job))
        assertTrue(jobQueue.inFlight.isEmpty())
    }

    @Test
    fun `시도 횟수 상한에 닿으면 재시도할 수 있는 실패라도 FAILED 로 끝낸다`() {
        val job = publishedJob()
        handler.failure = JobFailure("LINK_TIMEOUT", retryable = true)

        repeat(settings.maxAttempts) {
            service.process(jobQueue.receive(1).single())
            jobQueue.redeliver()
        }

        val saved = jobRepository.findById(job.id!!)!!
        assertEquals(JobStatus.FAILED, saved.status)
        assertEquals(settings.maxAttempts, saved.attemptCount)
    }

    @Test
    fun `예상하지 못한 예외는 재시도할 수 있는 실패로 기록한다`() {
        val job = publishedJob()
        handler.unexpected = IllegalStateException("boom")

        service.process(jobQueue.receive(1).single())

        val saved = jobRepository.findById(job.id!!)!!
        assertEquals(JobStatus.QUEUED, saved.status)
        assertEquals(ProcessingJobService.ERROR_UNEXPECTED, saved.lastErrorCode)
    }

    @Test
    fun `같은 메시지가 두 번 오면 한 번만 수행하고 나머지는 지운다`() {
        val job = publishedJob()
        jobQueue.publish(job.id!!)
        val (first, second) = jobQueue.receive(2)

        service.process(first)
        service.process(second)

        assertEquals(1, handler.calls)
        assertEquals(JobStatus.DONE, statusOf(job))
        assertTrue(jobQueue.inFlight.isEmpty())
    }

    @Test
    fun `차례가 지난 실행이 늦게 끝나면 결과를 저장하지 않는다`() {
        val job = publishedJob()
        val message = jobQueue.receive(1).single()
        handler.duringHandle = {
            // 실행이 멈춘 것으로 보일 만큼 시간이 지나 다른 워커가 같은 작업을 다시 잡는다.
            clock.advance(settings.runningTimeout.plusSeconds(1))
            jobRepository.start(job.id!!, clock.instant().minus(settings.runningTimeout), clock.instant())
        }

        service.process(message)

        assertEquals(0, handler.saved)
        val saved = jobRepository.findById(job.id!!)!!
        assertEquals(JobStatus.RUNNING, saved.status)
        assertEquals(2, saved.attemptCount)
    }

    @Test
    fun `수행 구현이 없는 작업 종류는 FAILED 로 끝낸다`() {
        val job = jobRepository.create(ProcessingJob.create(1, JobType.EMBED, JobReason.INITIAL, clock.instant()))!!
        service.publish(job.id!!)

        service.process(jobQueue.receive(1).single())

        val saved = jobRepository.findById(job.id)!!
        assertEquals(JobStatus.FAILED, saved.status)
        assertEquals(ProcessingJobService.ERROR_HANDLER_MISSING, saved.lastErrorCode)
    }

    @Test
    fun `오래 대기 중인 작업과 멈춘 작업을 다시 발행한다`() {
        val notPublished = newJob()
        val lost = publishedJob()
        val stuck = publishedJob()
        jobRepository.start(stuck.id!!, clock.instant(), clock.instant())
        clock.advance(settings.republishAfter.plusSeconds(1))
        val fresh = newJob()
        jobQueue.published.clear()

        val count = service.republishStale()

        assertEquals(3, count)
        assertEquals(setOf(notPublished.id, lost.id, stuck.id), jobQueue.published.toSet())
        assertEquals(JobStatus.QUEUED, statusOf(notPublished))
        assertEquals(JobStatus.PENDING, statusOf(fresh))
    }

    private var nextItemId = 1L

    private fun newJob(savedItemId: Long = nextItemId++): ProcessingJob =
        jobRepository.create(ProcessingJob.create(savedItemId, JobType.ANALYZE, JobReason.INITIAL, clock.instant()))!!

    private fun publishedJob(): ProcessingJob = newJob().also { service.publish(it.id!!) }

    private fun statusOf(job: ProcessingJob): JobStatus = jobRepository.findById(job.id!!)!!.status

    private class StubHandler : JobHandler {
        override val jobType = JobType.ANALYZE
        var failure: JobFailure? = null
        var unexpected: Exception? = null
        var duringHandle: () -> Unit = {}
        var calls = 0
        var saved = 0

        override fun handle(job: ProcessingJob): JobResult {
            calls++
            duringHandle()
            failure?.let { throw it }
            unexpected?.let { throw it }
            return JobResult { saved++ }
        }
    }
}
