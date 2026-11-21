package com.depromeet.team1.worker.job

import com.depromeet.team1.core.job.ProcessingJobSettings
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/** 처리 작업 설정. 값과 기본값은 api 의 application.yml(`job.*`)에 있다. 워커를 꺼도 발행에 쓰이므로 항상 만든다. */
@Configuration(proxyBeanMethods = false)
class ProcessingJobConfig {
    @Bean
    fun processingJobSettings(
        @Value("\${job.max-attempts}") maxAttempts: Int,
        @Value("\${job.visibility-timeout}") visibilityTimeout: Duration,
        @Value("\${job.republish-after}") republishAfter: Duration,
        @Value("\${job.republish-batch-size}") republishBatchSize: Int,
    ): ProcessingJobSettings = ProcessingJobSettings(
        maxAttempts = maxAttempts,
        runningTimeout = visibilityTimeout,
        republishAfter = republishAfter,
        republishBatchSize = republishBatchSize,
    )
}
