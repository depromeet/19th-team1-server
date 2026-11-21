package com.depromeet.team1.core.common

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** 테스트용 시계. 처음 시각에 멈춰 있고 [advance] 로만 흐른다. */
class FixedClock(private var now: Instant) : Clock() {
    override fun instant(): Instant = now

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}
