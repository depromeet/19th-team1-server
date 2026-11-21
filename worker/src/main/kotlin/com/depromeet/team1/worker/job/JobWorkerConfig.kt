package com.depromeet.team1.worker.job

import com.depromeet.team1.core.job.JobQueue
import com.depromeet.team1.core.job.ProcessingJobService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.core.type.AnnotatedTypeMetadata
import java.util.concurrent.Executors

/** 작업 큐 받기 루프. `worker.enabled`(`WORKER_ENABLED`)가 true 일 때만 뜬다. */
@Configuration(proxyBeanMethods = false)
@Conditional(WorkerEnabledCondition::class)
class JobWorkerConfig {
    @Bean
    fun jobReceiveLoop(
        jobQueue: JobQueue,
        service: ProcessingJobService,
        @Value("\${worker.concurrency}") concurrency: Int,
    ): JobReceiveLoop =
        JobReceiveLoop(JobReceiver(jobQueue, service, concurrency, Executors.newVirtualThreadPerTaskExecutor()))
}

class WorkerEnabledCondition : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean =
        context.environment.getRequiredProperty("worker.enabled", Boolean::class.java)
}
