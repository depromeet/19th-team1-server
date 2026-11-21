package com.depromeet.team1.infra.external.queue

import com.depromeet.team1.core.job.JobMessage
import com.depromeet.team1.core.job.JobQueueException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.net.URI
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** SQS 호환 서버 ElasticMQ 에서 실제 SQS 클라이언트로 발행·받기·삭제·재전송을 확인한다. */
class SqsJobQueueTest {
    private val queueUrl = sqsClient.createQueue { it.queueName("jobs-${UUID.randomUUID()}") }.queueUrl()
    private val queue = SqsJobQueue(sqsClient, queueUrl, visibilityTimeout = VISIBILITY_TIMEOUT, waitTime = WAIT_TIME)

    @AfterEach
    fun deleteQueue() {
        sqsClient.deleteQueue { it.queueUrl(queueUrl) }
    }

    @Test
    fun `발행한 작업 ID 를 받는다`() {
        queue.publish(42)

        val messages = queue.receive(10)

        assertEquals(listOf(42L), messages.map { it.jobId })
    }

    @Test
    fun `받을 것이 없으면 빈 목록을 돌려준다`() {
        val messages = queue.receive(10)

        assertTrue(messages.isEmpty())
    }

    @Test
    fun `요청한 개수보다 많이 받지 않는다`() {
        repeat(3) { queue.publish(it.toLong()) }

        val messages = queue.receive(2)

        assertEquals(2, messages.size)
    }

    @Test
    fun `한 번에 받는 수는 1개에서 10개까지다`() {
        assertThrows<IllegalArgumentException> { queue.receive(0) }
        assertThrows<IllegalArgumentException> { queue.receive(11) }
    }

    @Test
    fun `지우지 않은 메시지는 가시성 제한 시간 뒤 다시 온다`() {
        queue.publish(7)
        val first = queue.receive(1).single()

        val again = receiveWithin(VISIBILITY_TIMEOUT.multipliedBy(5))

        assertEquals(first.jobId, again?.jobId)
        assertNotEquals(first.receipt, again?.receipt)
    }

    @Test
    fun `지운 메시지는 가시성 제한 시간이 지나도 다시 오지 않는다`() {
        queue.publish(7)
        queue.delete(queue.receive(1).single())

        val again = receiveWithin(VISIBILITY_TIMEOUT.multipliedBy(3))

        assertEquals(null, again)
    }

    @Test
    fun `큐 호출이 실패하면 작업 큐 예외로 바꾼다`() {
        val missingQueue = SqsJobQueue(sqsClient, "$queueUrl-missing", VISIBILITY_TIMEOUT, WAIT_TIME)

        assertThrows<JobQueueException> { missingQueue.publish(1) }
    }

    /** [timeout] 동안 메시지 한 건이 올 때까지 받는다. 오지 않으면 null. */
    private fun receiveWithin(timeout: Duration): JobMessage? {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            queue.receive(1).firstOrNull()?.let { return it }
        }
        return null
    }

    companion object {
        private val VISIBILITY_TIMEOUT = Duration.ofSeconds(1)
        private val WAIT_TIME = Duration.ofSeconds(1)

        private val elasticMq = GenericContainer("softwaremill/elasticmq-native:1.7.1")
            .withExposedPorts(9324)
            .waitingFor(Wait.forHttp("/?Action=ListQueues").forStatusCode(200))
            .also { it.start() }

        private val sqsClient = SqsClients.create(
            region = "ap-southeast-2",
            endpoint = URI("http://${elasticMq.host}:${elasticMq.getMappedPort(9324)}"),
        )
    }
}
