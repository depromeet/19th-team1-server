package com.depromeet.team1.core.job

/**
 * [JobQueue] 의 가짜 구현. 받은 메시지는 지울 때까지 [inFlight] 에 남는다. 가시성 제한 시간은 흉내 내지 않는다.
 * 다시 보내는 것은 테스트가 [redeliver] 로 직접 한다. 워커 테스트에서 여러 스레드가 함께 쓰므로 메서드를 동기화한다.
 */
class FakeJobQueue : JobQueue {
    private val waiting = ArrayDeque<JobMessage>()
    private val received = linkedMapOf<String, JobMessage>()
    private var nextReceipt = 1

    /** 지금까지 발행한 작업 ID. 같은 작업을 다시 발행하면 다시 들어간다. */
    val published = mutableListOf<Long>()

    val inFlight: List<JobMessage>
        @Synchronized get() = received.values.toList()

    private val deletedMessages = mutableListOf<JobMessage>()

    val deleted: List<JobMessage>
        @Synchronized get() = deletedMessages.toList()

    private val requests = mutableListOf<Int>()

    /** [receive] 에 요청한 개수. 호출 순서대로 쌓인다. */
    val receiveRequests: List<Int>
        @Synchronized get() = requests.toList()

    @Synchronized
    override fun publish(jobId: Long) {
        published += jobId
        waiting.addLast(JobMessage(jobId, "receipt-${nextReceipt++}"))
    }

    @Synchronized
    override fun receive(maxMessages: Int): List<JobMessage> {
        require(maxMessages >= 1) { "1개 이상 받는다" }
        requests += maxMessages
        val batch = generateSequence { waiting.removeFirstOrNull() }.take(maxMessages).toList()
        batch.forEach { received[it.receipt] = it }
        return batch
    }

    @Synchronized
    override fun delete(message: JobMessage) {
        received.remove(message.receipt)
        deletedMessages += message
    }

    /** 지우지 않은 메시지를 다시 보낸다. 가시성 제한 시간이 지난 것과 같다. */
    @Synchronized
    fun redeliver() {
        received.values.forEach { waiting.addLast(it.copy(receipt = "receipt-${nextReceipt++}")) }
        received.clear()
    }
}
