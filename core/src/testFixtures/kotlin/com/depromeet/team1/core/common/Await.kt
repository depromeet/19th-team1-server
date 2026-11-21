package com.depromeet.team1.core.common

import java.time.Duration

/** 다른 스레드의 처리를 기다린다. [timeout] 안에 [condition] 이 참이 되지 않으면 실패한다. */
fun awaitUntil(timeout: Duration = Duration.ofSeconds(5), condition: () -> Boolean) {
    val deadline = System.nanoTime() + timeout.toNanos()
    while (!condition()) {
        check(System.nanoTime() < deadline) { "${timeout.toMillis()}ms 안에 조건을 만족하지 않았다" }
        Thread.sleep(10)
    }
}
