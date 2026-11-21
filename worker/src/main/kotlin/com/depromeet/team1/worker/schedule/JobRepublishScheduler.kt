package com.depromeet.team1.worker.schedule

import com.depromeet.team1.core.job.ProcessingJobService
import org.springframework.context.annotation.Conditional
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** 발행이 빠졌거나 메시지를 잃은 작업, 멈춘 작업을 주기적으로 다시 발행한다. */
@Component
@Conditional(SchedulerEnabledCondition::class)
class JobRepublishScheduler(private val service: ProcessingJobService) {
    @Scheduled(fixedDelayString = "\${job.republish-interval}")
    fun republishStale() {
        service.republishStale()
    }
}
