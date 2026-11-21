package com.depromeet.team1.worker

import com.depromeet.team1.core.common.FixedClock
import com.depromeet.team1.core.common.awaitUntil
import com.depromeet.team1.core.job.FakeJobQueue
import com.depromeet.team1.core.job.FakeProcessingJobRepository
import com.depromeet.team1.core.job.JobReason
import com.depromeet.team1.core.job.JobType
import com.depromeet.team1.core.job.ProcessingJob
import com.depromeet.team1.core.job.ProcessingJobService
import com.depromeet.team1.core.job.TEST_PROCESSING_JOB_SETTINGS
import com.depromeet.team1.core.job.processingJobService
import com.depromeet.team1.worker.job.JobReceiveLoop
import com.depromeet.team1.worker.job.JobWorkerConfig
import com.depromeet.team1.worker.schedule.JobRepublishScheduler
import com.depromeet.team1.worker.schedule.SchedulingConfig
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource
import java.time.Instant
import java.util.function.Supplier
import kotlin.test.assertTrue

/** 설정 플래그로 받기 루프와 예약 작업을 켜고 끈다. */
class WorkerSwitchTest {
    private val clock = FixedClock(Instant.parse("2026-11-21T03:00:00Z"))
    private val jobRepository = FakeProcessingJobRepository()
    private val jobQueue = FakeJobQueue()
    private val service = processingJobService(jobRepository, jobQueue, emptyList(), clock)

    @Test
    fun `WORKER_ENABLED 가 false 면 받기 루프가 뜨지 않는다`() {
        context("worker.enabled" to "false").use { context ->
            assertTrue(context.getBeansOfType(JobReceiveLoop::class.java).isEmpty())
            assertTrue(jobQueue.receiveRequests.isEmpty())
        }
    }

    @Test
    fun `WORKER_ENABLED 가 true 면 받기 루프가 큐에서 받는다`() {
        context("worker.enabled" to "true", "worker.concurrency" to "2").use {
            awaitUntil { jobQueue.receiveRequests.isNotEmpty() }
        }
    }

    @Test
    fun `SCHEDULER_ENABLED 가 false 면 재발행 예약 작업이 뜨지 않는다`() {
        context("scheduler.enabled" to "false").use { context ->
            assertTrue(context.getBeansOfType(JobRepublishScheduler::class.java).isEmpty())
        }
    }

    @Test
    fun `SCHEDULER_ENABLED 가 true 면 멈춘 작업을 주기적으로 다시 발행한다`() {
        val job = jobRepository.create(ProcessingJob.create(1, JobType.ANALYZE, JobReason.INITIAL, clock.instant()))!!
        clock.advance(TEST_PROCESSING_JOB_SETTINGS.republishAfter.plusSeconds(1))

        context("scheduler.enabled" to "true", "job.republish-interval" to "PT0.05S").use {
            awaitUntil { jobQueue.published.contains(job.id) }
        }
    }

    private fun context(vararg properties: Pair<String, String>): AnnotationConfigApplicationContext =
        AnnotationConfigApplicationContext().apply {
            val defaults = mapOf("worker.enabled" to "false", "scheduler.enabled" to "false")
            environment.propertySources.addFirst(MapPropertySource("test", defaults + properties.toMap()))
            registerBean(ProcessingJobService::class.java, Supplier { service })
            registerBean(FakeJobQueue::class.java, Supplier { jobQueue })
            register(JobWorkerConfig::class.java, SchedulingConfig::class.java, JobRepublishScheduler::class.java)
            refresh()
        }
}
