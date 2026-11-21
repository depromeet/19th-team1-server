package com.depromeet.team1.worker.schedule

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.core.type.AnnotatedTypeMetadata
import org.springframework.scheduling.annotation.EnableScheduling

/** 예약 작업. `scheduler.enabled`(`SCHEDULER_ENABLED`)가 true 일 때만 켠다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Conditional(SchedulerEnabledCondition::class)
class SchedulingConfig

class SchedulerEnabledCondition : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean =
        context.environment.getRequiredProperty("scheduler.enabled", Boolean::class.java)
}
