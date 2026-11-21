package com.depromeet.team1.worker.job

import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import java.time.Duration

/**
 * 앱이 뜨면 받기 루프를 가상 스레드 하나로 돌리고, 앱이 멈출 때 끝낸다.
 * 큐 호출이 실패하면 [ERROR_BACKOFF] 만큼 쉬었다가 다시 받는다.
 */
class JobReceiveLoop(private val receiver: JobReceiver) : SmartLifecycle {
    @Volatile
    private var thread: Thread? = null

    override fun start() {
        thread = Thread.ofVirtual().name("job-receive-loop").start(::run)
    }

    override fun stop() {
        val running = thread ?: return
        thread = null
        running.interrupt()
        running.join(SHUTDOWN_WAIT)
        receiver.shutdown(SHUTDOWN_WAIT)
    }

    override fun isRunning(): Boolean = thread != null

    private fun run() {
        while (!Thread.currentThread().isInterrupted) {
            try {
                receiver.awaitFreeSlot()
                receiver.pollOnce()
            } catch (_: InterruptedException) {
                return
            } catch (e: Exception) {
                if (Thread.currentThread().isInterrupted) return
                log.warn("작업 큐 받기 실패, {}초 뒤 다시 받는다: {}", ERROR_BACKOFF.toSeconds(), e.message)
                try {
                    Thread.sleep(ERROR_BACKOFF)
                } catch (_: InterruptedException) {
                    return
                }
            }
        }
    }

    companion object {
        private val ERROR_BACKOFF: Duration = Duration.ofSeconds(5)
        private val SHUTDOWN_WAIT: Duration = Duration.ofSeconds(10)

        private val log = LoggerFactory.getLogger(JobReceiveLoop::class.java)
    }
}
