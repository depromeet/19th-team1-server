package com.depromeet.team1.core.job

import java.time.Clock
import java.time.Duration

/** 테스트용 처리 작업 설정. */
val TEST_PROCESSING_JOB_SETTINGS = ProcessingJobSettings(
    maxAttempts = 3,
    runningTimeout = Duration.ofMinutes(5),
    republishAfter = Duration.ofMinutes(10),
    republishBatchSize = 100,
)

/** 가짜 저장소·큐로 조립한 처리 작업 서비스. */
fun processingJobService(
    jobRepository: ProcessingJobRepository,
    jobQueue: JobQueue,
    handlers: List<JobHandler>,
    clock: Clock,
    settings: ProcessingJobSettings = TEST_PROCESSING_JOB_SETTINGS,
): ProcessingJobService =
    ProcessingJobService(jobRepository, jobQueue, handlers, JobCompletionService(jobRepository, clock), settings, clock)
