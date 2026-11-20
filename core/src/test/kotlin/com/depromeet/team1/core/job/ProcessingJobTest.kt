package com.depromeet.team1.core.job

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProcessingJobTest {
    @Test
    fun `새 작업은 PENDING 이고 시도 횟수는 0 이다`() {
        val job = ProcessingJob.create(1, JobType.ANALYZE, JobReason.INITIAL, NOW)

        assertEquals(JobStatus.PENDING, job.status)
        assertEquals(0, job.attemptCount)
    }

    @Test
    fun `오류 내용이 500자를 넘으면 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            ProcessingJob.create(1, JobType.ANALYZE, JobReason.INITIAL, NOW).copy(lastErrorDetail = "가".repeat(501))
        }
    }

    companion object {
        private val NOW = Instant.parse("2026-11-21T03:00:00Z")
    }
}
