package com.depromeet.team1.infra.external.queue

import com.depromeet.team1.core.job.JobMessage
import com.depromeet.team1.core.job.JobQueue
import com.depromeet.team1.core.job.JobQueueException
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.sqs.SqsClient
import java.time.Duration

/**
 * SQS 작업 큐. 메시지 본문은 작업 ID 하나다.
 *
 * 받을 때 [visibilityTimeout] 을 직접 지정한다. 처리 작업의 멈춤 판정(ProcessingJobSettings.runningTimeout)과 같은 값을 써서
 * SQS 재전송과 DB 의 실행 잡기 조건이 어긋나지 않게 한다.
 */
class SqsJobQueue(
    private val sqsClient: SqsClient,
    private val queueUrl: String,
    private val visibilityTimeout: Duration,
    private val waitTime: Duration = LONG_POLLING,
) : JobQueue {
    override fun publish(jobId: Long) {
        call("발행") { sqsClient.sendMessage { it.queueUrl(queueUrl).messageBody(jobId.toString()) } }
    }

    override fun receive(maxMessages: Int): List<JobMessage> {
        require(maxMessages in 1..JobQueue.MAX_RECEIVE) { "한 번에 1~${JobQueue.MAX_RECEIVE}개를 받는다" }
        val response = call("받기") {
            sqsClient.receiveMessage {
                it.queueUrl(queueUrl)
                    .maxNumberOfMessages(maxMessages)
                    .waitTimeSeconds(waitTime.toSeconds().toInt())
                    .visibilityTimeout(visibilityTimeout.toSeconds().toInt())
            }
        }
        return response.messages().mapNotNull { message ->
            val jobId = message.body().toLongOrNull()
            // 작업 ID 가 아닌 메시지는 지우지 않는다. 최대 수신 횟수를 넘기면 DLQ 로 가서 사람이 본다.
            if (jobId == null) log.warn("작업 ID 가 아닌 메시지를 건너뛴다: messageId={}", message.messageId())
            jobId?.let { JobMessage(it, message.receiptHandle()) }
        }
    }

    override fun delete(message: JobMessage) {
        call("삭제") { sqsClient.deleteMessage { it.queueUrl(queueUrl).receiptHandle(message.receipt) } }
    }

    private fun <T> call(operation: String, block: () -> T): T = try {
        block()
    } catch (e: SdkException) {
        throw JobQueueException("작업 큐 $operation 실패: ${e.javaClass.simpleName}", e)
    }

    companion object {
        /** SQS 롱 폴링 최대 대기 시간 */
        val LONG_POLLING: Duration = Duration.ofSeconds(20)

        private val log = LoggerFactory.getLogger(SqsJobQueue::class.java)
    }
}
