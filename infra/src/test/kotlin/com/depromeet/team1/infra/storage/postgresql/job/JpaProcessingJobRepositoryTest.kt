package com.depromeet.team1.infra.storage.postgresql.job

import com.depromeet.team1.core.job.JobReason
import com.depromeet.team1.core.job.JobStatus
import com.depromeet.team1.core.job.JobType
import com.depromeet.team1.core.job.ProcessingJob
import com.depromeet.team1.core.job.ProcessingJobRepository
import com.depromeet.team1.infra.storage.postgresql.StorageTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JpaProcessingJobRepositoryTest : StorageTest() {
    @Autowired
    private lateinit var jobRepository: ProcessingJobRepository

    @Test
    fun `진행 중인 ANALYZE 작업이 없으면 PENDING 으로 만든다`() {
        val job = jobRepository.create(analyze())

        assertEquals(JobStatus.PENDING, assertNotNull(job).status)
    }

    @Test
    fun `진행 중인 ANALYZE 작업이 있으면 새로 만들지 않는다`() {
        jobRepository.create(analyze())

        val second = jobRepository.create(analyze(JobReason.RETRY))

        assertNull(second)
    }

    @Test
    fun `동시에 ANALYZE 작업을 만들어도 하나만 만들어진다`() {
        val ready = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(4)

        val results = (1..4).map {
            executor.submit(
                Callable {
                    ready.await()
                    jobRepository.create(analyze(JobReason.RETRY))
                },
            )
        }
        ready.countDown()
        val created = results.mapNotNull { it.get() }
        executor.shutdown()

        assertEquals(1, created.size)
    }

    @Test
    fun `이전 ANALYZE 작업이 끝났으면 새로 만든다`() {
        val first = jobRepository.create(analyze())!!
        jobRepository.failUnfinished(first.id!!, "TIMEOUT", null, NOW)

        val second = jobRepository.create(analyze(JobReason.RETRY))

        assertNotNull(second)
    }

    @Test
    fun `진행 중인 ANALYZE 작업이 있어도 EMBED 작업은 만든다`() {
        jobRepository.create(analyze())

        val embed = jobRepository.create(ProcessingJob.create(SAVED_ITEM_ID, JobType.EMBED, JobReason.INITIAL, NOW))

        assertNotNull(embed)
    }

    @Test
    fun `PENDING 작업만 QUEUED 로 바꾼다`() {
        val id = jobRepository.create(analyze())!!.id!!

        val first = jobRepository.markQueued(id, NOW)
        val second = jobRepository.markQueued(id, NOW)

        assertEquals(1, first)
        assertEquals(0, second)
        assertEquals(JobStatus.QUEUED, jobRepository.findById(id)?.status)
    }

    @Test
    fun `QUEUED 작업을 시작하면 RUNNING 이 되고 차례 번호 1 을 돌려준다`() {
        val id = queuedJob()

        val attempt = jobRepository.start(id, NOW.minus(VISIBILITY), NOW)

        assertEquals(1, attempt)
        val job = assertNotNull(jobRepository.findById(id))
        assertEquals(JobStatus.RUNNING, job.status)
        assertEquals(1, job.attemptCount)
    }

    @Test
    fun `가시성 제한 시간이 지나지 않은 RUNNING 작업은 다시 시작하지 않는다`() {
        val id = queuedJob()
        jobRepository.start(id, NOW.minus(VISIBILITY), NOW)

        val attempt = jobRepository.start(id, NOW.minus(VISIBILITY), NOW.plusSeconds(10))

        assertNull(attempt)
    }

    @Test
    fun `가시성 제한 시간이 지난 RUNNING 작업은 다시 시작하고 차례 번호가 오른다`() {
        val id = queuedJob()
        jobRepository.start(id, NOW.minus(VISIBILITY), NOW)
        val later = NOW.plus(VISIBILITY).plusSeconds(1)

        val attempt = jobRepository.start(id, later.minus(VISIBILITY), later)

        assertEquals(2, attempt)
    }

    @Test
    fun `끝난 작업은 다시 시작하지 않는다`() {
        val id = queuedJob()
        val attempt = jobRepository.start(id, NOW.minus(VISIBILITY), NOW)!!
        jobRepository.complete(id, attempt, NOW)

        val again = jobRepository.start(id, NOW.plus(VISIBILITY), NOW.plus(VISIBILITY))

        assertNull(again)
    }

    @Test
    fun `내 차례 번호가 맞으면 DONE 으로 끝낸다`() {
        val id = queuedJob()
        val attempt = jobRepository.start(id, NOW.minus(VISIBILITY), NOW)!!

        val updated = jobRepository.complete(id, attempt, NOW)

        assertEquals(1, updated)
        val job = assertNotNull(jobRepository.findById(id))
        assertEquals(JobStatus.DONE, job.status)
        assertEquals(NOW, job.completedAt)
    }

    @Test
    fun `다른 워커가 다시 가져간 작업은 늦게 끝낸 워커가 바꾸지 못한다`() {
        val id = queuedJob()
        val myAttempt = jobRepository.start(id, NOW.minus(VISIBILITY), NOW)!!
        val later = NOW.plus(VISIBILITY).plusSeconds(1)
        jobRepository.start(id, later.minus(VISIBILITY), later)

        val completed = jobRepository.complete(id, myAttempt, later)
        val requeued = jobRepository.requeue(id, myAttempt, "TIMEOUT", null, later)
        val failed = jobRepository.fail(id, myAttempt, "NOT_FOUND", null, later)

        assertEquals(listOf(0, 0, 0), listOf(completed, requeued, failed))
        assertEquals(JobStatus.RUNNING, jobRepository.findById(id)?.status)
    }

    @Test
    fun `재시도할 수 있는 실패는 QUEUED 로 돌리고 오류를 남긴다`() {
        val id = queuedJob()
        val attempt = jobRepository.start(id, NOW.minus(VISIBILITY), NOW)!!

        val updated = jobRepository.requeue(id, attempt, "TIMEOUT", "수집 시간 초과", NOW)

        assertEquals(1, updated)
        val job = assertNotNull(jobRepository.findById(id))
        assertEquals(JobStatus.QUEUED, job.status)
        assertEquals("TIMEOUT", job.lastErrorCode)
        assertEquals("수집 시간 초과", job.lastErrorDetail)
    }

    @Test
    fun `재시도할 수 없는 실패는 FAILED 로 끝낸다`() {
        val id = queuedJob()
        val attempt = jobRepository.start(id, NOW.minus(VISIBILITY), NOW)!!

        val updated = jobRepository.fail(id, attempt, "NOT_FOUND", null, NOW)

        assertEquals(1, updated)
        val job = assertNotNull(jobRepository.findById(id))
        assertEquals(JobStatus.FAILED, job.status)
        assertEquals(NOW, job.completedAt)
    }

    @Test
    fun `DLQ 처리는 끝나지 않은 작업만 FAILED 로 바꾼다`() {
        val unfinished = queuedJob()
        val done = jobRepository.create(
            ProcessingJob.create(SAVED_ITEM_ID, JobType.EMBED, JobReason.INITIAL, NOW),
        )!!.id!!
        jobRepository.complete(done, jobRepository.start(done, NOW, NOW)!!, NOW)

        val first = jobRepository.failUnfinished(unfinished, "DLQ", null, NOW)
        val second = jobRepository.failUnfinished(done, "DLQ", null, NOW)

        assertEquals(1, first)
        assertEquals(0, second)
        assertEquals(JobStatus.DONE, jobRepository.findById(done)?.status)
    }

    @Test
    fun `오래 멈춘 작업만 재발행 대상으로 찾는다`() {
        val stalePending = jobRepository.create(analyze(savedItemId = 1))!!.id!!
        val staleRunning = queuedJob(savedItemId = 2)
        jobRepository.start(staleRunning, NOW.minus(VISIBILITY), NOW)
        jobRepository.create(analyze(savedItemId = 3, now = NOW.plusSeconds(600)))
        val finished = queuedJob(savedItemId = 4)
        jobRepository.failUnfinished(finished, "DLQ", null, NOW)
        val checkAt = NOW.plusSeconds(600)

        val stale = jobRepository.findStale(
            queuedBefore = checkAt.minusSeconds(300),
            runningBefore = checkAt.minus(VISIBILITY),
            limit = 10,
        )

        assertEquals(setOf(stalePending, staleRunning), stale.map { it.id }.toSet())
    }

    private fun analyze(reason: JobReason = JobReason.INITIAL, savedItemId: Long = SAVED_ITEM_ID, now: Instant = NOW) =
        ProcessingJob.create(savedItemId, JobType.ANALYZE, reason, now)

    private fun queuedJob(savedItemId: Long = SAVED_ITEM_ID): Long {
        val id = jobRepository.create(analyze(savedItemId = savedItemId))!!.id!!
        jobRepository.markQueued(id, NOW)
        return id
    }

    companion object {
        private const val SAVED_ITEM_ID = 100L
        private val VISIBILITY: Duration = Duration.ofMinutes(5)
    }
}
