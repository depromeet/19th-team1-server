package com.depromeet.team1.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** 서비스가 주입받는 시계. 시각은 UTC 로 다룬다. 테스트는 고정한 시계를 넣는다. */
@Configuration(proxyBeanMethods = false)
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
