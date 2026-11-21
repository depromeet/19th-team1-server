package com.depromeet.team1.infra.external.queue

import com.depromeet.team1.core.job.JobQueue
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.services.sqs.SqsClient
import java.net.URI
import java.time.Duration

/** SQS 작업 큐. 값은 api 의 application.yml(`job.queue.*`, `job.visibility-timeout`)에 있다. */
@Configuration(proxyBeanMethods = false)
class SqsJobQueueConfig {
    @Bean(destroyMethod = "close")
    fun sqsClient(
        @Value("\${job.queue.region}") region: String,
        @Value("\${job.queue.endpoint}") endpoint: String,
    ): SqsClient = SqsClients.create(region, endpoint.takeIf { it.isNotBlank() }?.let(::URI))

    @Bean
    fun jobQueue(
        sqsClient: SqsClient,
        @Value("\${job.queue.url}") queueUrl: String,
        @Value("\${job.visibility-timeout}") visibilityTimeout: Duration,
    ): JobQueue = SqsJobQueue(sqsClient, queueUrl, visibilityTimeout)
}
